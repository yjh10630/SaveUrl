package com.jinscompany.saveurl.ui.main

import androidx.paging.PagingData
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.flow.Flow

sealed class MainListUiState {
    data object Idle : MainListUiState()
    data object Loading: MainListUiState()
    data class Success(val urlFlowState: Flow<PagingData<UrlData>>): MainListUiState()
    data class Error(val message: String): MainListUiState()
}

sealed class MainCategoryUiState {
    data object Idle : MainCategoryUiState()
    data object Loading: MainCategoryUiState()
    data class Success(val categories: List<CategoryModel>): MainCategoryUiState()
    data class Error(val message: String): MainCategoryUiState()
}

sealed class MainListIntent {
    data object FetchCategoryData: MainListIntent()
    data object GoToSearchScreen: MainListIntent()
    data object GoToAppSetting: MainListIntent()
    data object GoToCategorySettingScreen: MainListIntent()
    data object GoToLinkInsertScreen: MainListIntent()
    data class GoToLinkEditScreen(val url: String?): MainListIntent()
    data class GoToOutLinkWebSite(val url: String?): MainListIntent()
    data class CategoryClick(val categoryName: String): MainListIntent()
    data class GotoOutShareUrl(val url: String?): MainListIntent()
    data class DeleteLinkItem(val urlData: UrlData): MainListIntent()
    data class ClipboardUrlCheck(val url: String): MainListIntent()
}

sealed class MainListUiEffect {
    data class NavigateToResult(val route: String, val urlData: UrlData? = null, val url: String? = null): MainListUiEffect()
    data class OutLinkWebSite(val url: String): MainListUiEffect()
    data class ShowToast(val message: String): MainListUiEffect()
    data class UrlShare(val url: String): MainListUiEffect()
    data class ShowSnackBarSaveUrl(val url: String): MainListUiEffect()
    data object ListRefresh: MainListUiEffect()
}