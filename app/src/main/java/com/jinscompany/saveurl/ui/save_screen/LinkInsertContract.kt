package com.jinscompany.saveurl.ui.save_screen

import com.jinscompany.saveurl.domain.model.UrlData

sealed class LinkInsertUiState {
    data object Idle : LinkInsertUiState()
    data object Loading : LinkInsertUiState()
    data class Success(val urlData: UrlData) : LinkInsertUiState()
    data class Error(val message: String) : LinkInsertUiState()
}

sealed class LinkInsertUrlIntent {
    //data class SubmitLinkInsertUrl(val url: String) : LinkInsertUrlIntent()
    data class SubmitWebViewCrawlerResult(val data: UrlData? = null): LinkInsertUrlIntent()
    data object CrawlerLoading: LinkInsertUrlIntent()
    data class AddTag(val tag: String): LinkInsertUrlIntent()
    data class RemoveTag(val tag: String): LinkInsertUrlIntent()
    data class ChangeCategory(val name: String): LinkInsertUrlIntent()
    data class IsBookmark(val isBookmark: Boolean): LinkInsertUrlIntent()
    data object SubmitSaveData: LinkInsertUrlIntent()
}

sealed class LinkInsertUiEffect {
    data object NavigateToResult : LinkInsertUiEffect()
}