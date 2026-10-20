package com.moyue.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moyue.reader.MoyueApp
import com.moyue.reader.AppContainer

class AppViewModelFactory(private val create: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val container = (LocalContext.current.applicationContext as MoyueApp).container
    return viewModel(
        key = key,
        factory = AppViewModelFactory { create(container) }
    )
}
