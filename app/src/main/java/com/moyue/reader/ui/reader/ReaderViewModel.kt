package com.moyue.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyue.reader.data.BookEntity
import com.moyue.reader.data.BookRepository
import com.moyue.reader.data.BookmarkEntity
import com.moyue.reader.data.ChapterEntity
import com.moyue.reader.data.ReaderPrefs
import com.moyue.reader.data.SettingsRepository
import com.moyue.reader.source.SourceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val book: BookEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val content: String = "",
    val contentKey: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0,
    val pageCount: Int = 1,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val prefs: ReaderPrefs = ReaderPrefs(),
    val message: String? = null
)

class ReaderViewModel(
    private val bookId: String,
    private val repository: BookRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var chapterJob: Job? = null
    private var saveJob: Job? = null

    /**
     * Progress writes must survive the ViewModel being cleared when the reader
     * screen is popped, so final saves do not use [viewModelScope].
     */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            settings.readerPrefs.collect { prefs ->
                _state.update { it.copy(prefs = prefs) }
            }
        }
        viewModelScope.launch {
            repository.observeBookmarks(bookId).collect { bookmarks ->
                _state.update { it.copy(bookmarks = bookmarks) }
            }
        }
        loadInitial()
    }

    private fun loadInitial() {
        chapterJob?.cancel()
        chapterJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val book = repository.getBook(bookId)
            if (book == null) {
                _state.update {
                    it.copy(loading = false, error = "书籍不存在或已被删除")
                }
                return@launch
            }
            val chapters = repository.getChapters(book.id)
            if (chapters.isEmpty()) {
                _state.update {
                    it.copy(
                        book = book,
                        chapters = emptyList(),
                        loading = false,
                        error = "没有找到这本书的章节"
                    )
                }
                return@launch
            }
            repository.touch(book.id)
            val index = book.currentChapter.coerceIn(0, chapters.lastIndex)
            _state.update {
                it.copy(
                    book = book,
                    chapters = chapters,
                    chapterIndex = index,
                    chapterTitle = chapters[index].title
                )
            }
            loadChapterInternal(book, index, book.currentPage)
        }
    }

    private suspend fun loadChapterInternal(
        book: BookEntity,
        index: Int,
        page: Int
    ) {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    chapterIndex = index,
                    chapterTitle = it.chapters.getOrNull(index)?.title.orEmpty(),
                    content = "",
                    currentPage = page,
                    pageCount = 1
                )
            }
            try {
                val text = repository.loadChapter(book, index)
                _state.update {
                    it.copy(
                        content = text,
                        contentKey = it.contentKey + 1,
                        loading = false,
                        error = null
                    )
                }
                repository.touch(book.id)
                viewModelScope.launch {
                    repository.prefetchChapter(book, index + 1)
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _state.update {
                it.copy(
                    loading = false,
                    error = error.readable()
                )
            }
        }
    }

    fun openChapter(index: Int, page: Int = 0) {
        val current = _state.value
        val book = current.book ?: return
        if (index !in current.chapters.indices) return
        chapterJob?.cancel()
        chapterJob = viewModelScope.launch {
            loadChapterInternal(book, index, page)
        }
    }

    fun nextChapter() {
        val current = _state.value
        if (current.chapterIndex + 1 < current.chapters.size) {
            openChapter(current.chapterIndex + 1, 0)
        } else {
            _state.update { it.copy(message = "已经是最后一章了") }
        }
    }

    fun previousChapter() {
        val current = _state.value
        if (current.chapterIndex > 0) {
            openChapter(current.chapterIndex - 1, LAST_PAGE)
        } else {
            _state.update { it.copy(message = "已经是第一章了") }
        }
    }

    fun onPageChanged(page: Int, pageCount: Int) {
        _state.update {
            it.copy(
                currentPage = page,
                pageCount = pageCount.coerceAtLeast(1)
            )
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            persistProgress(_state.value)
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        val snapshot = _state.value
        saveScope.launch { persistProgress(snapshot) }
    }

    private suspend fun persistProgress(current: ReaderUiState) {
        val pageCount = current.pageCount
        val book = current.book ?: return
        if (current.currentPage < 0) return
        val within = if (pageCount <= 0) 0f else {
            ((current.currentPage + 1f) / pageCount).coerceIn(0f, 1f)
        }
        val progress = if (book.totalChapters <= 0) {
            0f
        } else {
            ((current.chapterIndex + within) / book.totalChapters).coerceIn(0f, 1f)
        }
        repository.updateProgress(
            bookId = book.id,
            chapterIndex = current.chapterIndex,
            page = current.currentPage,
            progress = progress
        )
    }

    fun toggleBookmark(snippet: String) {
        val current = _state.value
        val book = current.book ?: return
        if (current.content.isBlank()) return
        viewModelScope.launch {
            val added = repository.addBookmark(
                bookId = book.id,
                chapterIndex = current.chapterIndex,
                page = current.currentPage,
                chapterTitle = current.chapterTitle,
                snippet = snippet.ifBlank { "第 ${current.currentPage + 1} 页" }
            )
            _state.update {
                it.copy(message = if (added) "已添加书签" else "已移除书签")
            }
        }
    }

    fun removeBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            repository.removeBookmark(bookmark)
            _state.update { it.copy(message = "书签已删除") }
        }
    }

    fun openBookmark(bookmark: BookmarkEntity) {
        openChapter(bookmark.chapterIndex, bookmark.page)
    }

    fun updatePrefs(transform: (ReaderPrefs) -> ReaderPrefs) {
        settings.updateReaderPrefs(transform)
    }

    fun retry() {
        val current = _state.value
        val book = current.book ?: return
        chapterJob?.cancel()
        chapterJob = viewModelScope.launch {
            loadChapterInternal(book, current.chapterIndex, current.currentPage)
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        saveJob?.cancel()
    }

    private fun Throwable.readable(): String = when (this) {
        is SourceException -> message ?: "加载失败"
        else -> message ?: "加载失败，请稍后重试"
    }

    private companion object {
        const val LAST_PAGE = -1
    }
}
