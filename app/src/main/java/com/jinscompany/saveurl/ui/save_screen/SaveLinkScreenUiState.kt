package com.jinscompany.saveurl.ui.save_screen

sealed class SaveLinkScreenUiState<out T> {
    data object Loading : SaveLinkScreenUiState<Nothing>()
    data class SuccessNewLinkUrl<out T>(val data: T) : SaveLinkScreenUiState<T>()
    data class SuccessUpdateLinkUrl<out T>(val data: T) : SaveLinkScreenUiState<T>()
    data object Saved : SaveLinkScreenUiState<Nothing>()
    data class Error(val exception: Throwable) : SaveLinkScreenUiState<Nothing>()
    data object Empty : SaveLinkScreenUiState<Nothing>()
}