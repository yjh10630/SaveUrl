package com.jinscompany.saveurl.ui.save_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun onIntent(intent: LinkSaveIntent) {
        when(intent) {
            is LinkSaveIntent.BookMarkToggle -> bookMarkToggle(intent.isBookMark)
            is LinkSaveIntent.OpenCategorySelector -> openCategorySelector(intent.currentCategoryName)
            LinkSaveIntent.SaveLink -> saveLink()
            LinkSaveIntent.ScreenBackPress -> onBackPress()
            is LinkSaveIntent.UserInputTag -> insertTag(intent.tag)
            is LinkSaveIntent.WebViewCrawlerDataResult -> webViewCrawlerStateResult(intent.data)
            LinkSaveIntent.CrawlerLoading -> crawlerLoading()
            is LinkSaveIntent.UserRemoveTag -> removeTag(intent.tag)
            is LinkSaveIntent.CategorySelectedItem -> selectedCategoryItem(intent.selectedCategory)
            LinkSaveIntent.CategoryEdit -> goToCategoryEditScreen()
            LinkSaveIntent.OpenPreviewContentEdit -> goToPreviewContentEditScreen()
            is LinkSaveIntent.PreviewContentEditData -> editPreviewContentData(intent.urlData)
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

    private fun crawlerLoading() {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading)
            }
        }
    }

    private fun webViewCrawlerStateResult(data: UrlData?) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    linkUrlPreviewUiState = data?.let {
                        LinkUrlPreviewUiState.LinkUrlData(it)
                    } ?: run {
                        LinkUrlPreviewUiState.LinkUrlData(
                            urlData = UrlData(
                                url = _uiState.value.userInputUrl,
                                title = "링크 정보를 불러오지 못했어요.",
                                description = _uiState.value.userInputUrl
                            )
                        )
                    }
                )
            }
        }
    }

    fun externalInputUrl(url: String) {
        viewModelScope.launch {
            urlRepository.findUrlData(url)?.let {
                _uiState.update { current ->
                    current.copy(
                        isEditScreen = true,
                        userInputUrl = url,
                        categoryName = it.category ?: "전체",
                        isBookMark = it.isBookMark,
                        tagList = it.tagList ?: emptyList(),
                        linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = it)
                    )
                }
            } ?: run {
                _uiState.update { current ->
                    current.copy(
                        linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading,
                        userInputUrl = url
                    )
                }
                _uiEffect.emit(LinkSaveUiEffect.StartCrawling(url))
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
}