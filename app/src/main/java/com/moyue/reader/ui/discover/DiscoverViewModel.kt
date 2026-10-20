package com.moyue.reader.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyue.reader.data.BookRepository
import com.moyue.reader.source.BookSource
import com.moyue.reader.source.CustomSource
import com.moyue.reader.source.SourceRegistry
import com.moyue.reader.source.SourceBook
import com.moyue.reader.source.SourceException
import com.moyue.reader.source.TocResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

data class DiscoverUiState(
    val sourceId: String = "wikisource",
    val sourceName: String = "维基文库",
    val sources: List<BookSource> = emptyList(),
    val customSources: List<CustomSource> = emptyList(),
    val catalog: List<SourceBook> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    val query: String = "",
    val searchActive: Boolean = false,
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val results: List<SourceBook> = emptyList(),
    val selected: SourceBook? = null,
    val preview: TocResult? = null,
    val previewLoading: Boolean = false,
    val previewError: String? = null,
    val adding: Boolean = false,
    val addedBookId: String? = null,
    val message: String? = null
)

class DiscoverViewModel(
    private val repository: BookRepository,
    private val registry: SourceRegistry
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    private var previewJob: Job? = null
    private val unavailableBooks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        refreshSources()
        selectSource("wikisource")
        viewModelScope.launch {
            registry.customSources.collect { refreshSources() }
        }
    }

    fun refreshSources() {
        val sources = registry.allSources()
        val customSources = registry.customSources.value
        val currentId = _state.value.sourceId
        val currentExists = sources.any { it.id == currentId }
        _state.update {
            it.copy(
                sources = sources,
                customSources = customSources,
                sourceId = if (currentExists) it.sourceId else sources.firstOrNull()?.id.orEmpty(),
                sourceName = if (currentExists) it.sourceName else sources.firstOrNull()?.name.orEmpty(),
                catalog = if (currentExists) it.catalog else emptyList(),
                catalogLoading = if (currentExists) it.catalogLoading else false,
                catalogError = if (currentExists) it.catalogError else null
            )
        }
        if (!currentExists) {
            sources.firstOrNull()?.let { selectSource(it.id) }
        }
    }

    fun selectSource(sourceId: String) {
        if (sourceId == _state.value.sourceId && _state.value.catalog.isNotEmpty()) return
        val source = _state.value.sources.firstOrNull { it.id == sourceId }
            ?: registry.get(sourceId)
            ?: return
        searchJob?.cancel()
        previewJob?.cancel()
        _state.update {
            it.copy(
                sourceId = source.id,
                sourceName = source.name,
                catalog = emptyList(),
                catalogLoading = true,
                catalogError = null,
                query = "",
                searchActive = false,
                searchLoading = false,
                searchError = null,
                results = emptyList(),
                selected = null,
                preview = null,
                previewError = null
            )
        }
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            runCatching { source.catalog() }
                .onSuccess { catalog ->
                    _state.update {
                        it.copy(
                            catalog = catalog,
                            catalogLoading = false,
                            catalogError = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            catalogLoading = false,
                            catalogError = error.readable()
                        )
                    }
                }
        }
    }

    fun refreshCatalog() {
        val sourceId = _state.value.sourceId
        _state.update { it.copy(catalog = emptyList()) }
        selectSource(sourceId)
    }

    fun addCustomSource(json: String): Result<CustomSource> = runCatching {
        registry.addCustomSource(json)
    }.onSuccess { source ->
        refreshSources()
        selectSource(source.id.orEmpty())
        _state.update { it.copy(message = "书源「${source.name.orEmpty()}」已添加") }
    }

    fun removeCustomSource(id: String) {
        runCatching { registry.removeCustomSource(id) }
            .onSuccess {
                refreshSources()
                _state.update { it.copy(message = "书源已删除") }
            }
            .onFailure { error ->
                _state.update { it.copy(message = error.readable()) }
            }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun activateSearch() {
        if (!_state.value.searchActive) {
            _state.update {
                it.copy(
                    searchActive = true,
                    searchError = null,
                    results = emptyList()
                )
            }
        }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        val currentId = _state.value.sourceId
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchActive = true,
                searchLoading = true,
                searchError = null,
                results = emptyList()
            )
        }
        searchJob = viewModelScope.launch {
            val sources = _state.value.sources
                .filter { it.searchable }
                .sortedByDescending { it.id == currentId }
            withTimeoutOrNull(20_000) { searchAllSources(sources, query) }
            val found = _state.value.results
            _state.update {
                it.copy(
                    results = found,
                    searchLoading = false,
                    searchError = if (found.isEmpty()) {
                        "没有找到「$query」，可换个关键词，或稍后再试"
                    } else {
                        null
                    }
                )
            }
        }
    }

    private suspend fun searchAllSources(
        sources: List<BookSource>,
        query: String,
        updateState: Boolean = true,
        titleFilter: String? = null
    ): List<SourceBook> {
        val order = sources.mapIndexed { index, source -> source.id to index }.toMap()
        return coroutineScope {
            val semaphore = Semaphore(sources.size.coerceIn(1, 32))
            sources.map { source ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val raw = try {
                            withTimeoutOrNull(8_000) { source.search(query) }.orEmpty()
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            emptyList()
                        }
                        val list = if (titleFilter.isNullOrBlank()) {
                            raw
                        } else {
                            raw.filter {
                                it.title.contains(titleFilter, true) ||
                                    titleFilter.contains(it.title, true)
                            }
                        }
                        if (updateState && list.isNotEmpty()) {
                            _state.update { state ->
                                state.copy(
                                    results = (state.results + list)
                                        .filter { it.title.isNotBlank() }
                                        .filterNot { unavailableBooks.contains(it.sourceId + "|" + it.key) }
                                        .distinctBy { it.sourceId + it.key }
                                        .sortedBy { order[it.sourceId] ?: Int.MAX_VALUE }
                                )
                            }
                        }
                        list
                    }
                }
            }.awaitAll()
                .flatten()
                .filter { it.title.isNotBlank() }
                .filterNot { unavailableBooks.contains(it.sourceId + "|" + it.key) }
                .distinctBy { it.sourceId + it.key }
                .sortedBy { order[it.sourceId] ?: Int.MAX_VALUE }
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchActive = false,
                searchLoading = false,
                searchError = null,
                results = emptyList(),
                query = ""
            )
        }
    }

    fun openBook(book: SourceBook) {
        previewJob?.cancel()
        _state.update {
            it.copy(
                selected = book,
                preview = null,
                previewError = null,
                previewLoading = true
            )
        }
        previewJob = viewModelScope.launch {
            try {
                val toc = withTimeoutOrNull(12_000) { repository.previewToc(book) }
                    ?: throw SourceException("读取目录超时，请稍后重试")
                _state.update { it.copy(preview = toc, previewLoading = false) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                unavailableBooks += book.sourceId + "|" + book.key
                fallbackSearch(book)
            }
        }
    }

    private fun fallbackSearch(book: SourceBook) {
        val sources = _state.value.sources.filter { it.searchable && it.id != book.sourceId }
        _state.update {
            it.copy(
                selected = null,
                preview = null,
                previewLoading = false,
                previewError = null,
                searchActive = true,
                query = book.title,
                searchLoading = true,
                searchError = null,
                results = emptyList(),
                message = "《${book.title}》在当前书源不可用，正在自动换源…"
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            withTimeoutOrNull(15_000) {
                searchAllSources(sources, book.title, titleFilter = book.title)
            }
            val hasResults = _state.value.results.isNotEmpty()
            _state.update {
                it.copy(
                    searchLoading = false,
                    searchError = if (hasResults) null else "没有找到「${book.title}」的其他可用书源",
                    message = if (hasResults) "已为你找到其他书源" else null
                )
            }
        }
    }

    fun closeBook() {
        previewJob?.cancel()
        _state.update {
            it.copy(
                selected = null,
                preview = null,
                previewLoading = false,
                previewError = null,
                adding = false
            )
        }
    }

    fun addSelected() {
        val book = _state.value.selected ?: return
        if (_state.value.adding) return
        viewModelScope.launch {
            _state.update { it.copy(adding = true) }
            runCatching { repository.addFromSource(book) }
                .onSuccess { entity ->
                    _state.update {
                        it.copy(
                            adding = false,
                            addedBookId = entity.id,
                            message = "《${entity.title}》已加入书架"
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            adding = false,
                            message = error.readable()
                        )
                    }
                }
        }
    }

    fun consumeAddedBook() {
        _state.update { it.copy(addedBookId = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun Throwable.readable(): String = when (this) {
        is SourceException -> message ?: "加载失败"
        else -> message ?: "网络开小差了，请稍后重试"
    }
}
