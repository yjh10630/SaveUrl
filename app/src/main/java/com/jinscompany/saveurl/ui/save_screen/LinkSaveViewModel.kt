package com.jinscompany.saveurl.ui.save_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.extractUrlFromText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LinkSaveViewModel @Inject constructor(
    private val urlRepository: UrlRepository,
    private val categoryRepository: CategoryRepository
): ViewModel() {

    private val _uiState = MutableStateFlow(LinkSaveUiState())
    val uiState: StateFlow<LinkSaveUiState> = _uiState

    private val _uiEffect = MutableSharedFlow<LinkSaveUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    private var parseJob: Job? = null

    fun onIntent(intent: LinkSaveIntent) {
        when(intent) {
            is LinkSaveIntent.BookMarkToggle -> bookMarkToggle(intent.isBookMark)
            is LinkSaveIntent.OpenCategorySelector -> openCategorySelector(intent.currentCategoryName)
            LinkSaveIntent.SaveLink -> saveLink()
            LinkSaveIntent.ScreenBackPress -> onBackPress()
            is LinkSaveIntent.UserInputTag -> insertTag(intent.tag)
            is LinkSaveIntent.WebViewCrawlerDataResult -> webViewCrawlerStateResult(intent.data)
            is LinkSaveIntent.CrawlerLoading -> crawlerLoading(intent.loadingUrl)
            is LinkSaveIntent.UserRemoveTag -> removeTag(intent.tag)
            is LinkSaveIntent.CategorySelectedItem -> selectedCategoryItem(intent.selectedCategory)
            LinkSaveIntent.CategoryEdit -> goToCategoryEditScreen()
            LinkSaveIntent.OpenPreviewContentEdit -> goToPreviewContentEditScreen()
            is LinkSaveIntent.PreviewContentEditData -> editPreviewContentData(intent.urlData)
            is LinkSaveIntent.StartCrawling -> startCrawling(intent.url)
            LinkSaveIntent.UserForcedEndCrawling -> userForcedEndCrawling()
        }
    }

    private fun userForcedEndCrawling() {
        viewModelScope.launch {
            parseJob?.cancel()
            _uiState.update { current ->
                current.copy(
                    linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(
                        urlData = UrlData(
                            url = _uiState.value.userInputUrl,
                            description = _uiState.value.userInputUrl
                        )
                    ),
                    isEditScreen = false,
                )
            }
        }
    }

    private fun editPreviewContentData(data: UrlData) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(data))
            }
        }
    }

    private fun goToPreviewContentEditScreen() {
        viewModelScope.launch {
            val urlData = (_uiState.value.linkUrlPreviewUiState as? LinkUrlPreviewUiState.LinkUrlData)?.urlData ?: UrlData()
            _uiEffect.emit(LinkSaveUiEffect.OpenPreviewContentEdit(urlData))
        }
    }

    private fun goToCategoryEditScreen() {
        viewModelScope.launch {
            _uiEffect.emit(LinkSaveUiEffect.GotoNextScreen(isCategoryEdit = true))
        }
    }

    private fun selectedCategoryItem(categoryName: String) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(categoryName = categoryName)
            }
        }
    }

    private fun saveLink() {
        viewModelScope.launch {
            _uiState.value.getSaveData()?.let {
                if (_uiState.value.isEditScreen) {
                    urlRepository.updateUrl(it)
                } else {
                    urlRepository.saveUrl(it)
                }
                _uiEffect.emit(LinkSaveUiEffect.GotoNextScreen())
            }
        }
    }

    private fun removeTag(tag: String) {
        viewModelScope.launch {
            _uiState.update { current ->
                val newTags = (current.tagList ?: emptyList()).filter { it != tag }
                current.copy(tagList = newTags)
            }
        }
    }

    private fun insertTag(tags: List<String>) {
        viewModelScope.launch {
            _uiState.update { current ->
                val newTags = (current.tagList ?: emptyList()) + tags
                current.copy(tagList = newTags)
            }
        }
    }

    private fun bookMarkToggle(isBookMark: Boolean) {
        viewModelScope.launch { 
            _uiState.update { current ->
                current.copy(isBookMark = isBookMark)
            }
        }
    }

    private fun crawlerLoading(loadingUrl: String) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading,
                    userInputUrl = loadingUrl
                )
            }
        }
    }

    private fun webViewCrawlerStateResult(data: UrlData?) {
        viewModelScope.launch {
            // 현재 상태가 로딩 중 일 때에만 값을 출력 하도록
            val isStateLoading = _uiState.value.linkUrlPreviewUiState == LinkUrlPreviewUiState.Loading
            if (isStateLoading == false) return@launch
            _uiState.update { current ->
                current.copy(
                    linkUrlPreviewUiState = data?.let {
                        LinkUrlPreviewUiState.LinkUrlData(it)
                    } ?: run {
                        LinkUrlPreviewUiState.LinkUrlData(
                            urlData = UrlData(
                                url = _uiState.value.userInputUrl,
                                description = _uiState.value.userInputUrl
                            )
                        )
                    },
                    isEditScreen = false
                )
            }
        }
    }

    fun startCrawling(url: String) {
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            val realUrl = extractUrlFromText(url)
            if (!realUrl.isNullOrEmpty()) {
                urlRepository.findUrlData(realUrl)?.let {
                    _uiState.update { current ->
                        current.copy(
                            isEditScreen = true,
                            userInputUrl = realUrl,
                            categoryName = it.category ?: "전체",
                            isBookMark = it.isBookMark,
                            tagList = it.tagList ?: emptyList(),
                            linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = it)
                        )
                    }
                } ?: run {
                    _uiState.update { current ->
                        current.copy(
                            isEditScreen = false,
                            userInputUrl = url,
                            linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading
                        )
                    }
                    val data = urlRepository.parserUrl(realUrl)
                    if (data.title.isNullOrEmpty()) {
                        _uiEffect.emit(LinkSaveUiEffect.StartCrawling(url))
                    } else {
                        val checkData = urlRepository.findUrlData(data.url ?: "")
                        _uiState.update { current ->
                            current.copy(
                                isEditScreen = data.title == (checkData?.title ?: ""),
                                userInputUrl = realUrl,
                                linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = data)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun onBackPress() {
        viewModelScope.launch {
            _uiEffect.emit(LinkSaveUiEffect.GotoNextScreen(isPopBack = true))
        }
    }

    private fun openCategorySelector(currentCategoryName: String) {
        viewModelScope.launch {
            val categories = categoryRepository.get()
            categories.firstOrNull { it.name == currentCategoryName }?.isSelected = true
            _uiEffect.emit(LinkSaveUiEffect.OpenCategorySelector(categories))
        }
    }

    fun userSelectLinkEditMode(data: UrlData) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isEditScreen = true,
                    userInputUrl = data.url ?: "",
                    categoryName = data.category ?: "전체",
                    isBookMark = data.isBookMark,
                    tagList = data.tagList ?: emptyList(),
                    linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = data)
                )
            }
        }
    }
}