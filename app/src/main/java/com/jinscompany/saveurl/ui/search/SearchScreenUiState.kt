package com.jinscompany.saveurl.ui.search

sealed class SearchScreenUiState<out T> {
    data object Loading: SearchScreenUiState<Nothing>()
    data class Success<out T>(val data: T): SearchScreenUiState<T>()
    data class Error(val errorType: SearchErrorType): SearchScreenUiState<Nothing>()
    data object Init: SearchScreenUiState<Nothing>()
    data object Empty: SearchScreenUiState<Nothing>()
}

enum class SearchErrorType {
    KEYWORD_EMPTY,
}