package com.jinscompany.saveurl.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.APP_SETTING
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.EDIT_CATEGORY
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SEARCH
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

    var filterMap = mutableStateMapOf<FilterKey, FilterState>()
        private set

    // 초기화 데이터 포멧
    val onClearMap = mutableStateMapOf<FilterKey, FilterState>(
        FilterKey.CATEGORY to FilterState.MultiSelect(
            options = listOf(),
            selected = SnapshotStateList<String>().apply { add("전체") }
        ),
        FilterKey.SORT to FilterState.SingleSelect(
            options = listOf(),
            selected = mutableStateOf("최신순")
        )
    )

    init {
        getLinkList()
    }

    fun onIntent(intent: MainListIntent) {
        viewModelScope.launch {
            when (intent) {
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
                is MainListIntent.NewFilterData -> {
                    viewModelScope.launch {
                        updateFilters(intent.data)
                        val params = filterMap.extractFilterParams()
                        getLinkList(params)
                    }
                }
            }
        }
    }

    fun filterSelectedData() = filterMap.values.flatMap { state ->
        when (state) {
            is FilterState.MultiSelect<*> -> state.selected.mapNotNull { it as? String }
            is FilterState.SingleSelect<*> -> listOf(state.selected.value as? String).filterNotNull()
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
            val categories = listOf(CategoryModel(name = "북마크"), CategoryModel(name = "전체")) + categoryRepository.get()
            filterMap[FilterKey.CATEGORY] = FilterState.MultiSelect(
                options = categories.map { it.name },
                selected = mutableStateListOf("전체")
            )

            filterMap[FilterKey.SORT] = FilterState.SingleSelect(
                options = listOf("최신순", "과거순"),
                selected = mutableStateOf("최신순")
            )
        }
    }

    private fun getLinkList(params: FilterParams? = null) {
        viewModelScope.launch {
            mainListUiState = MainListUiState.Loading
            val dataFlow = Pager(
                config = PagingConfig(
                    pageSize = 10,
                    prefetchDistance = 5,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { urlRepository.getUrlList(params) }
            ).flow.cachedIn(viewModelScope)
            mainListUiState = MainListUiState.Success(urlFlowState = dataFlow)
        }
    }

    private fun SnapshotStateMap<FilterKey, FilterState>.extractFilterParams(): FilterParams {
        val categories = (this[FilterKey.CATEGORY] as? FilterState.MultiSelect<String>)
            ?.selected ?: emptyList()

        val sort = (this[FilterKey.SORT] as? FilterState.SingleSelect<String>)
            ?.selected?.value ?: "최신순"

        return FilterParams(categories = categories, sort = sort)
    }

    private fun updateFilters(newFilters: Map<FilterKey, FilterState>) {
        filterMap.clear()
        filterMap = mutableStateMapOf<FilterKey, FilterState>().apply { putAll(newFilters) }
    }

}