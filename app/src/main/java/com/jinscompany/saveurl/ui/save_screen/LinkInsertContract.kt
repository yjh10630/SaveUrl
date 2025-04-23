package com.jinscompany.saveurl.ui.save_screen

import com.jinscompany.saveurl.domain.model.UrlData

sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    data class Success(val urlData: UrlData) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class UrlIntent {
    data class SubmitUrl(val url: String) : UrlIntent()
    data class AddTag(val tag: String): UrlIntent()
    data class RemoveTag(val tag: String): UrlIntent()
    data class ChangeCategory(val name: String): UrlIntent()
    data class IsBookmark(val isBookmark: Boolean): UrlIntent()
    data object SubmitSaveData: UrlIntent()
}

sealed class UiEffect {
    data object NavigateToResult : UiEffect()
}