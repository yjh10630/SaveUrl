package com.jinscompany.saveurl.ui.main

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.domain.usecase.DeleteWithTrashUseCase
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import com.jinscompany.saveurl.ui.main.MainListIntent
import com.jinscompany.saveurl.ui.main.MainListUiEffect.*
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.APP_SETTING
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.EDIT_CATEGORY
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SEARCH
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.tutorialUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainListViewModel @Inject constructor(
    private val urlRepository: UrlRepository,
    private val categoryRepository: CategoryRepository,
    private val deleteWithTrashUseCase: DeleteWithTrashUseCase,
    private val trashRepository: TrashRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    var mainListUiState by mutableStateOf<MainListUiState>(MainListUiState.Idle)
        private set

    private val _mainListEffect = MutableSharedFlow<MainListUiEffect>()
    val mainListEffect = _mainListEffect.asSharedFlow()

    var filterSelectedItems by mutableStateOf<FilterParams>(FilterParams(categories = listOf("전체"), sort = "최신순", siteList = listOf(), tagList = listOf()))
        private set

    init {
        getLinkList()
        deleteExpiredTrash()
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
                    _mainListEffect.emit(
                        if (intent.url.isNullOrEmpty()) {
                            ShowToast("Url 정보가 누락 되어 이동할 수 없습니다.")
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
                            ShowToast("Url 정보가 누락 되어 이동할 수 없습니다.")
                        } else {
                            UrlShare(intent.url)
                        }
                    )
                }
                is MainListIntent.GoToLinkEditScreen -> {
                    _mainListEffect.emit(
                        NavigateToResult(route = SAVE_LINK, url = intent.url)
                    )
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

                MainListIntent.FetchCategoryData -> {}
                is MainListIntent.ClipboardUrlCheck -> clipboardUrlCheckToSnackBar(intent.url)
                MainListIntent.GoToAppSetting -> {
                    _mainListEffect.emit(NavigateToResult(route = APP_SETTING))
                }
                is MainListIntent.NewFilterData -> {
                    viewModelScope.launch {
                        filterSelectedItems = FilterParams(categories = intent.category, sort = intent.sort, siteList = intent.site, tagList = intent.tag)
                        getLinkList(filterSelectedItems)
                    }
                }

                is MainListIntent.ShowLinkInfoDialog -> showLinkInfoDialog(intent.data)
            }
        }
    }

    private fun showLinkInfoDialog(data: UrlData) {
        viewModelScope.launch {
            val isTrashEnable = trashRepository.getTrashState()
            val list = mutableListOf<SimpleMenuModel.MenuModel>(
                SimpleMenuModel.MenuModel(txt = "공유하기", txtColor = Color.LightGray, event = {
                    viewModelScope.launch {
                        onIntent(MainListIntent.GotoOutShareUrl(data.url))
                    }
                }),
                SimpleMenuModel.MenuModel(txt = "수정하기", txtColor = Color.LightGray, isBold = true, event = {
                    viewModelScope.launch {
                        onIntent(MainListIntent.GoToLinkEditScreen(url = data.url ?: ""))
                    }
                }),
                SimpleMenuModel.MenuModel(
                    txt = if (isTrashEnable) "휴지통으로 이동" else "삭제하기",
                    txtColor = Color.Red,
                    isBold = true,
                    event = {
                        viewModelScope.launch {
                            if (isTrashEnable) {
                                onIntent(MainListIntent.DeleteLinkItem(data))
                            } else {
                                urlRepository.removeUrl(data)
                            }
                        }
                    }
                )
            )

            if (data.url == tutorialUrl) list.removeAll { it.txt == "수정하기" }    // 튜토리얼은 수정하기 방지

            val model = SimpleMenuModel(menuList = list)
            _mainListEffect.emit(ShowLinkInfoDialog(model))
        }
    }

    private fun clipboardUrlCheckToSnackBar(url: String) {
        viewModelScope.launch {
            val isSaved = urlRepository.isSavedUrl(url)
            if (!isSaved) _mainListEffect.emit(ShowSnackBarSaveUrl(url))
        }
    }

    private fun deleteLinkItem(data: UrlData) {
        viewModelScope.launch {
            deleteWithTrashUseCase.execute(data)
        }
    }

    private fun getLinkList(params: FilterParams? = null) {
        viewModelScope.launch {
            mainListUiState = MainListUiState.Loading

            val isInitRunApp = preferencesManager.isInitFirstRun.first()
            if (!isInitRunApp) {
                // 앱 최초 시작 시 튜토리얼 링크 추가
                val isSaved = urlRepository.saveUrl(
                    UrlData(
                        title = "이렇게 사용하세요!",
                        imgUrl = "https://github.com/yjh10630/MyWeb/blob/main/assets/images/help.png?raw=true",
                        url = tutorialUrl,
                        description = "사용방법을 숙지 하셨다면 튜토리얼을 제거하셔도 됩니다. :) ",
                        siteName = "튜토리얼",
                        isBookMark = true,
                        tagList = listOf("사용방법", "튜토리얼")
                    )
                )
                if (isSaved) preferencesManager.setInitFirstRun(true)   // 우선 튜토리얼 저장이 완료 되면 플래그, 다른 용도가 있을 경우 변경 필요
            }

            val dataFlow = Pager(
                config = PagingConfig(
                    pageSize = 10,
                    prefetchDistance = 5,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { urlRepository.getUrlList(params) }
            ).flow.cachedIn(viewModelScope)
                .map { pagingData ->
                    params?.tagList?.let { tagList ->
                        if (tagList.isEmpty()) pagingData
                        else {
                            pagingData.filter {
                                it.tagList?.any { it in tagList } == true
                            }
                        }
                    } ?: run { pagingData }
                }
            mainListUiState = MainListUiState.Success(urlFlowState = dataFlow)
        }
    }
}