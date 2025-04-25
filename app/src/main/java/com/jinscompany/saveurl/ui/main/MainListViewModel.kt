package com.jinscompany.saveurl.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.APP_SETTING
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.EDIT_CATEGORY
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SEARCH
import com.jinscompany.saveurl.ui.save_screen.LinkInsertUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainListViewModel @Inject constructor(
    private val urlRepository: UrlRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    var mainListUiState by mutableStateOf<MainListUiState>(MainListUiState.Idle)
        private set

    var mainCategoryUiState by mutableStateOf<MainCategoryUiState>(MainCategoryUiState.Idle)
        private set

    private val _mainListEffect = MutableSharedFlow<MainListUiEffect>()
    val mainListEffect = _mainListEffect.asSharedFlow()

    private var isSelectedCategoryName by mutableStateOf("전체")

    init {
        getLinkList()
    }

    fun onIntent(intent: MainListIntent) {
        viewModelScope.launch {
            when (intent) {
                is MainListIntent.CategoryClick -> {
                    getLinkList(intent.categoryName)
                }
                is MainListIntent.GoToOutLinkWebSite -> {
                    _mainListEffect.emit(
                        if (intent.url.isNullOrEmpty()) {
                            MainListUiEffect.ShowToast("Url 정보가 누락 되어 이동할 수 없습니다.")
                        } else {
                            MainListUiEffect.OutLinkWebSite(intent.url)
                        }
                    )
                }
                is MainListIntent.GotoOutShareUrl -> {
                    _mainListEffect.emit(
                        if (intent.url.isNullOrEmpty()) {
                            MainListUiEffect.ShowToast("Url 정보가 누락 되어 이동할 수 없습니다.")
                        } else {
                            MainListUiEffect.UrlShare(intent.url)
                        }
                    )
                }
                is MainListIntent.GoToLinkEditScreen -> {
                    _mainListEffect.emit(
                        if (intent.url.isNullOrEmpty()) {
                            MainListUiEffect.ShowToast("Url 정보가 누락 되어 이동할 수 없습니다.")
                        } else {
                            MainListUiEffect.NavigateToResult(route = SAVE_LINK, url = intent.url)
                        }
                    )
                }
                is MainListIntent.DeleteLinkItem -> deleteLinkItem(intent.urlData)
                MainListIntent.GoToCategorySettingScreen -> {
                    _mainListEffect.emit(MainListUiEffect.NavigateToResult(route = EDIT_CATEGORY))
                }
                MainListIntent.GoToLinkInsertScreen -> {
                    _mainListEffect.emit(MainListUiEffect.NavigateToResult(route = SAVE_LINK))
                }
                MainListIntent.GoToSearchScreen -> {
                    _mainListEffect.emit(MainListUiEffect.NavigateToResult(route = SEARCH))
                }

                MainListIntent.FetchCategoryData -> getCategoryList()
                is MainListIntent.ClipboardUrlCheck -> clipboardUrlCheckToSnackBar(intent.url)
                MainListIntent.GoToAppSetting -> {
                    _mainListEffect.emit(MainListUiEffect.NavigateToResult(route = APP_SETTING))
                }
            }
        }
    }

    private fun clipboardUrlCheckToSnackBar(url: String) {
        viewModelScope.launch {
            val isSaved = urlRepository.isSavedUrl(url)
            if (!isSaved) _mainListEffect.emit(MainListUiEffect.ShowSnackBarSaveUrl(url))
        }
    }

    private fun deleteLinkItem(data: UrlData) {
        viewModelScope.launch {
            urlRepository.removeUrl(data)
        }
    }

    private fun getCategoryList() {
        viewModelScope.launch {
            mainCategoryUiState = MainCategoryUiState.Loading
            val categories = listOf(CategoryModel(name = "북마크"), CategoryModel(name = "전체")) + categoryRepository.get()
            categories.firstOrNull { it.name == isSelectedCategoryName }?.isSelected = true
            mainCategoryUiState = MainCategoryUiState.Success(categories = categories)
        }
    }

    private fun getLinkList(categoryName: String? = "") {
        viewModelScope.launch {
            isSelectedCategoryName = categoryName ?: "전체"
            mainListUiState = MainListUiState.Loading
            val dataFlow = Pager(
                config = PagingConfig(
                    pageSize = 10,
                    prefetchDistance = 5,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { urlRepository.getUrlList(categoryName) }
            ).flow.cachedIn(viewModelScope)
            mainListUiState = MainListUiState.Success(urlFlowState = dataFlow)
        }
    }

}