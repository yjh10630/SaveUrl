package com.jinscompany.saveurl.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.usecase.DeleteWithTrashUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.GetTrashStateUseCase
import com.jinscompany.saveurl.domain.usecase.GetUrlListUseCase
import com.jinscompany.saveurl.domain.usecase.IsSavedUrlUseCase
import com.jinscompany.saveurl.domain.usecase.MarkAsReadUseCase
import com.jinscompany.saveurl.domain.usecase.RemoveUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SaveUrlUseCase
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import com.jinscompany.saveurl.ui.main.MainListUiEffect.*
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.APP_SETTING
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.EDIT_CATEGORY
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SEARCH
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.utils.ClipboardReader
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.tutorialUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainListViewModel @Inject constructor(
    private val getUrlListUseCase: GetUrlListUseCase,
    private val saveUrlUseCase: SaveUrlUseCase,
    private val removeUrlUseCase: RemoveUrlUseCase,
    private val isSavedUrlUseCase: IsSavedUrlUseCase,
    private val getTrashStateUseCase: GetTrashStateUseCase,
    private val deleteWithTrashUseCase: DeleteWithTrashUseCase,
    private val trashRepository: TrashRepository,
    private val preferencesManager: PreferencesManager,
    private val clipboardReader: ClipboardReader,
    private val markAsReadUseCase: MarkAsReadUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
) : ViewModel() {

    private val _mainListUiState = MutableStateFlow<MainListUiState>(MainListUiState.Idle)
    val mainListUiState: StateFlow<MainListUiState> = _mainListUiState.asStateFlow()

    private val _mainListEffect = MutableSharedFlow<MainListUiEffect>()
    val mainListEffect = _mainListEffect.asSharedFlow()

    private val _filterSelectedItems = MutableStateFlow(
        FilterParams(categories = listOf(FilterDefaults.CATEGORY_ALL), sort = FilterDefaults.SORT_LATEST, siteList = listOf(), tagList = listOf())
    )
    val filterSelectedItems: StateFlow<FilterParams> = _filterSelectedItems.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.RECENT)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    /** 카테고리 칩 행에 보여줄 사용자 카테고리 이름 ("전체" 제외) */
    private val _categoryNames = MutableStateFlow<List<String>>(emptyList())
    val categoryNames: StateFlow<List<String>> = _categoryNames.asStateFlow()

    init {
        getLinkList()
        deleteExpiredTrash()
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _categoryNames.value = runCatching { getCategoriesUseCase() }.getOrDefault(emptyList())
                .map { it.name }
                .filter { it != FilterDefaults.CATEGORY_ALL && it != FilterDefaults.CATEGORY_BOOKMARK }
                .distinct()
        }
    }

    private fun deleteExpiredTrash() {
        viewModelScope.launch {
            trashRepository.deleteItemsPastEndDate()
        }
    }

    fun onIntent(intent: MainListIntent) {
        viewModelScope.launch {
            when (intent) {
                is MainListIntent.GoToOutLinkWebSite -> {
                    if (!intent.url.isNullOrEmpty()) markAsReadUseCase(intent.url)
                    _mainListEffect.emit(
                        if (intent.url.isNullOrEmpty()) {
                            ShowToast(R.string.error_url_missing)
                        } else if (intent.url == tutorialUrl) {
                            StaticWebOpen(intent.url)
                        } else {
                            OutLinkWebSite(intent.url)
                        }
                    )
                }
                is MainListIntent.GotoOutShareUrl -> {
                    _mainListEffect.emit(
                        if (intent.url.isNullOrEmpty()) {
                            ShowToast(R.string.error_url_missing)
                        } else {
                            UrlShare(intent.url)
                        }
                    )
                }
                is MainListIntent.GoToLinkEditScreen -> {
                    _mainListEffect.emit(NavigateToResult(route = SAVE_LINK, url = intent.url))
                }
                is MainListIntent.DeleteLinkItem -> deleteLinkItem(intent.urlData)
                MainListIntent.GoToCategorySettingScreen -> {
                    _mainListEffect.emit(NavigateToResult(route = EDIT_CATEGORY))
                }
                is MainListIntent.GoToLinkInsertScreen -> {
                    _mainListEffect.emit(NavigateToResult(route = SAVE_LINK, url = intent.url))
                }
                MainListIntent.GoToSearchScreen -> {
                    _mainListEffect.emit(NavigateToResult(route = SEARCH))
                }
                MainListIntent.FetchCategoryData -> loadCategories()
                MainListIntent.ReadClipboard -> clipboardReader.readUrl()?.let { clipboardUrlCheckToSnackBar(it) }
                MainListIntent.RefreshOnResume -> loadCategories()
                is MainListIntent.SelectTab -> {
                    if (_selectedTab.value != intent.tab) {
                        _selectedTab.value = intent.tab
                        getLinkList()
                    }
                }
                MainListIntent.GoToAppSetting -> {
                    _mainListEffect.emit(NavigateToResult(route = APP_SETTING))
                }
                is MainListIntent.NewFilterData -> {
                    _filterSelectedItems.update {
                        FilterParams(categories = intent.category, sort = intent.sort, siteList = intent.site, tagList = intent.tag)
                    }
                    getLinkList()
                }
                is MainListIntent.ShowLinkInfoDialog -> showLinkInfoDialog(intent.data)
            }
        }
    }

    private fun showLinkInfoDialog(data: UrlData) {
        viewModelScope.launch {
            val isTrashEnable = getTrashStateUseCase()
            val list = mutableListOf<SimpleMenuModel.MenuModel>(
                SimpleMenuModel.MenuModel(txtRes = R.string.main_list_menu_share, icon = Icons.Outlined.Share, event = {
                    viewModelScope.launch { onIntent(MainListIntent.GotoOutShareUrl(data.url)) }
                }),
                SimpleMenuModel.MenuModel(txtRes = R.string.main_list_menu_edit, icon = Icons.Outlined.Edit, event = {
                    viewModelScope.launch { onIntent(MainListIntent.GoToLinkEditScreen(url = data.url ?: "")) }
                }),
                SimpleMenuModel.MenuModel(
                    txtRes = if (isTrashEnable) R.string.trash_move_label else R.string.main_list_menu_delete,
                    icon = Icons.Outlined.Delete,
                    isDanger = true,
                    event = {
                        viewModelScope.launch {
                            if (isTrashEnable) {
                                onIntent(MainListIntent.DeleteLinkItem(data))
                            } else {
                                removeUrlUseCase(data)
                            }
                        }
                    }
                )
            )

            if (data.url == tutorialUrl) list.removeAll { it.txtRes == R.string.main_list_menu_edit }

            val header = SimpleMenuModel.Header(
                imgUrl = data.imgUrl,
                title = data.title.orEmpty().ifBlank { data.url.orEmpty() },
                subtitle = listOfNotNull(
                    data.siteName?.takeIf { it.isNotBlank() },
                    data.category?.takeIf { it.isNotBlank() && it != FilterDefaults.CATEGORY_ALL },
                ).joinToString(" · "),
            )
            val model = SimpleMenuModel(menuList = list, header = header)
            _mainListEffect.emit(ShowLinkInfoDialog(model))
        }
    }

    private fun clipboardUrlCheckToSnackBar(url: String) {
        viewModelScope.launch {
            val isSaved = isSavedUrlUseCase(url)
            if (!isSaved) _mainListEffect.emit(ShowSnackBarSaveUrl(url))
        }
    }

    private fun deleteLinkItem(data: UrlData) {
        viewModelScope.launch {
            deleteWithTrashUseCase.execute(data)
        }
    }

    private fun getLinkList() {
        val params = _filterSelectedItems.value
        val isFavoritesTab = _selectedTab.value == MainTab.FAVORITES
        // 즐겨찾기 탭 + "전체" 카테고리면 DB 쿼리(북마크)로 바로 거르고,
        // 특정 카테고리를 고른 경우에는 카테고리 쿼리 결과에서 즐겨찾기만 남긴다.
        val queryParams = if (isFavoritesTab && params.categories.contains(FilterDefaults.CATEGORY_ALL)) {
            params.copy(categories = listOf(FilterDefaults.CATEGORY_BOOKMARK))
        } else params
        val filterBookmarkInMemory = isFavoritesTab && !queryParams.categories.contains(FilterDefaults.CATEGORY_BOOKMARK)
        viewModelScope.launch {
            _mainListUiState.value = MainListUiState.Loading

            val isInitRunApp = preferencesManager.isInitFirstRun.first()
            if (!isInitRunApp) {
                val result = saveUrlUseCase(
                    UrlData(
                        title = "이렇게 사용하세요!",
                        imgUrl = "https://github.com/yjh10630/MyWeb/blob/main/assets/images/help.png?raw=true",
                        url = tutorialUrl,
                        description = "사용방법을 숙지 하셨다면 튜토리얼을 제거하셔도 됩니다. :) ",
                        siteName = "튜토리얼",
                        isBookMark = true,
                        tagList = listOf("사용방법", "튜토리얼")
                    ),
                    force = true
                )
                if (result is com.jinscompany.saveurl.domain.usecase.SaveResult.Success) preferencesManager.setInitFirstRun(true)
            }

            val dataFlow = Pager(
                config = PagingConfig(pageSize = 10, prefetchDistance = 5, enablePlaceholders = false),
                pagingSourceFactory = { getUrlListUseCase(queryParams) }
            ).flow.cachedIn(viewModelScope)
                .map { pagingData ->
                    val tagList = queryParams.tagList
                    val byTag = if (tagList.isEmpty()) pagingData
                        else pagingData.filter { it.tagList?.any { it in tagList } == true }
                    if (filterBookmarkInMemory) byTag.filter { it.isBookMark } else byTag
                }
            _mainListUiState.value = MainListUiState.Success(urlFlowState = dataFlow)
        }
    }
}
