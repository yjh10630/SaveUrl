package com.jinscompany.saveurl.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.paging.PagingData
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import kotlinx.coroutines.flow.Flow

sealed class MainListUiState {
    data object Idle : MainListUiState()
    data object Loading: MainListUiState()
    data class Success(val urlFlowState: Flow<PagingData<UrlData>>): MainListUiState()
    data class Error(val message: String): MainListUiState()
}

sealed class MainListIntent {
    data object FetchCategoryData: MainListIntent()
    data object GoToSearchScreen: MainListIntent()
    data object GoToAppSetting: MainListIntent()
    data object GoToCategorySettingScreen: MainListIntent()
    data class GoToLinkInsertScreen(val url: String): MainListIntent()
    data class GoToLinkEditScreen(val urlData: UrlData): MainListIntent()
    data class GoToOutLinkWebSite(val url: String?): MainListIntent()
    data class GotoOutShareUrl(val url: String?): MainListIntent()
    data class DeleteLinkItem(val urlData: UrlData): MainListIntent()
    data class ClipboardUrlCheck(val url: String): MainListIntent()
    data class NewFilterData(val category: List<String>, val sort: String, val site: List<String>): MainListIntent()
    data class ShowLinkInfoDialog(val data: UrlData): MainListIntent()
}

sealed class MainListUiEffect {
    data class NavigateToResult(val route: String, val urlData: UrlData? = null, val url: String? = null): MainListUiEffect()
    data class OutLinkWebSite(val url: String): MainListUiEffect()
    data class ShowToast(val message: String): MainListUiEffect()
    data class UrlShare(val url: String): MainListUiEffect()
    data class ShowSnackBarSaveUrl(val url: String): MainListUiEffect()
    data object ListRefresh: MainListUiEffect()
    data class ShowLinkInfoDialog(val model: SimpleMenuModel): MainListUiEffect()
}

sealed class FilterState {
    data class MultiSelect<T>(val options: List<T>, val selected: SnapshotStateList<T>) : FilterState()
    data class SingleSelect<T>(val options: List<T>, var selected: MutableState<T>) : FilterState()
}