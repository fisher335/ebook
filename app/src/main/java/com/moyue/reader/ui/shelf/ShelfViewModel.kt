package com.moyue.reader.ui.shelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyue.reader.data.BookEntity
import com.moyue.reader.data.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShelfViewModel(private val repository: BookRepository) : ViewModel() {
    val books: StateFlow<List<BookEntity>> = repository.books.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun importBook(uri: Uri) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            runCatching { repository.importLocal(uri) }
                .onSuccess {
                    _message.value = "《${it.title}》已导入书架"
                }
                .onFailure {
                    _message.value = it.message ?: "导入失败，请检查文件格式"
                }
            _importing.value = false
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
            _message.value = "已移出书架"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
