package com.moyue.reader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyue.reader.data.AppThemeMode
import com.moyue.reader.data.BookEntity
import com.moyue.reader.data.BookRepository
import com.moyue.reader.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileStats(
    val total: Int = 0,
    val reading: Int = 0,
    val finished: Int = 0,
    val bookmarks: Int = 0
)

class ProfileViewModel(
    private val repository: BookRepository,
    val settings: SettingsRepository
) : ViewModel() {
    val stats: StateFlow<ProfileStats> = combine(
        repository.books,
        repository.bookmarkCount
    ) { books, bookmarks ->
        ProfileStats(
            total = books.size,
            reading = books.count { it.progress > 0.005f && it.progress < 0.995f },
            finished = books.count { it.progress >= 0.995f },
            bookmarks = bookmarks
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileStats()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) = settings.setThemeMode(mode)

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCachedChapters()
            _message.value = "在线章节缓存已清理"
        }
    }

    fun clearShelf() {
        viewModelScope.launch {
            repository.clearShelf()
            _message.value = "书架已清空"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
