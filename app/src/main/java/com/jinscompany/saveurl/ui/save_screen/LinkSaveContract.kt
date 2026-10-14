package com.jinscompany.saveurl.ui.save_screen

import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults

data class LinkSaveUiState (
    val isBookMark: Boolean = false,
    val userInputUrl: String = "",
    val tagList: List<String> = emptyList(),
    val categoryName: String = FilterDefaults.CATEGORY_ALL,
    val linkUrlPreviewUiState: LinkUrlPreviewUiState = LinkUrlPreviewUiState.Idle,
    val isEditScreen: Boolean = false,  // 수정 모드 일경우 true
    /** 잠금 카테고리 이름 (카테고리 행에 자물쇠 표시) */
    val lockedCategoryNames: List<String> = emptyList(),
    /** 잠금 링크를 수정하던 중 다시 잠김 → 화면을 닫는다 (38번 리뷰 4번) */
    val isClosedByRelock: Boolean = false,
) {
    fun getSaveData(): UrlData? {
        return (linkUrlPreviewUiState as? LinkUrlPreviewUiState.LinkUrlData) ?.let {
            it.urlData.isBookMark = isBookMark
            it.urlData.tagList = tagList
            it.urlData.category = categoryName
            it.urlData
        }
    }
}

sealed class LinkUrlPreviewUiState {
    data object Idle: LinkUrlPreviewUiState()
    data object Loading: LinkUrlPreviewUiState()
    data class LinkUrlData(val urlData: UrlData): LinkUrlPreviewUiState()
}

sealed class LinkSaveIntent {
    data object ScreenBackPress: LinkSaveIntent()
    data object CategoryEdit: LinkSaveIntent()
    data object SaveLink: LinkSaveIntent()
    data class StartCrawling(val url: String): LinkSaveIntent()
    data class WebViewCrawlerDataResult(val data: UrlData? = null): LinkSaveIntent()
    data class UserInputTag(val tag: List<String>): LinkSaveIntent()
    data class UserRemoveTag(val tag: String): LinkSaveIntent()
    data class BookMarkToggle(val isBookMark: Boolean): LinkSaveIntent()
    data class OpenCategorySelector(val currentCategoryName: String): LinkSaveIntent()
    data object OpenPreviewContentEdit: LinkSaveIntent()
    data class PreviewContentEditData(val urlData: UrlData): LinkSaveIntent()
    data class CategorySelectedItem(val selectedCategory: String): LinkSaveIntent()
    data class CrawlerLoading(val loadingUrl: String): LinkSaveIntent()
    data object UserForcedEndCrawling: LinkSaveIntent()
    data object ForceSaveLink: LinkSaveIntent()
}

sealed class LinkSaveUiEffect {
    data class GotoNextScreen(val isPopBack: Boolean = false, val isCategoryEdit: Boolean = false): LinkSaveUiEffect()
    data class OpenCategorySelector(val categories: List<CategoryModel>): LinkSaveUiEffect()
    data class OpenPreviewContentEdit(val urlData: UrlData): LinkSaveUiEffect()
    data class StartCrawling(val url: String): LinkSaveUiEffect()
    data class ShowCrawlFailedDialog(val url: String): LinkSaveUiEffect()
    /** [hideTitle] = 기존 링크가 닫힌 잠금 카테고리에 있어 제목을 보여 주면 안 됨 */
    data class ShowDuplicateDialog(val existing: UrlData, val hideTitle: Boolean = false): LinkSaveUiEffect()
}