package com.jinscompany.saveurl.ui.save_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.usecase.FindUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.InsertCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.LearnDomainCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.ParseUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SaveUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SaveResult
import com.jinscompany.saveurl.domain.usecase.SuggestCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.UpdateUrlUseCase
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.utils.extractUrlFromText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val saveUrlUseCase: SaveUrlUseCase,
    private val updateUrlUseCase: UpdateUrlUseCase,
    private val findUrlDataUseCase: FindUrlDataUseCase,
    private val parseUrlUseCase: ParseUrlUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val suggestCategoryUseCase: SuggestCategoryUseCase,
    private val learnDomainCategoryUseCase: LearnDomainCategoryUseCase,
    private val insertCategoryUseCase: InsertCategoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkSaveUiState())
    val uiState: StateFlow<LinkSaveUiState> = _uiState

    private val _uiEffect = MutableSharedFlow<LinkSaveUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    private var parseJob: Job? = null
    private var suggestedCategory: String? = null

    fun onIntent(intent: LinkSaveIntent) {
        when (intent) {
            is LinkSaveIntent.ForceSaveLink -> forceSaveLink()
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
                        urlData = UrlData(url = _uiState.value.userInputUrl, description = _uiState.value.userInputUrl)
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
            _uiState.update { current -> current.copy(categoryName = categoryName) }
        }
    }

    private fun saveLink() {
        viewModelScope.launch {
            _uiState.value.getSaveData()?.let { urlData ->
                if (_uiState.value.isEditScreen) {
                    updateUrlUseCase(urlData)
                    afterSave(urlData)
                } else {
                    when (val result = saveUrlUseCase(urlData)) {
                        is SaveResult.Duplicate -> {
                            _uiEffect.emit(LinkSaveUiEffect.ShowDuplicateDialog(result.existing))
                            return@launch
                        }
                        is SaveResult.Success -> afterSave(urlData)
                        SaveResult.Error -> return@launch
                    }
                }
            }
        }
    }

    private fun forceSaveLink() {
        viewModelScope.launch {
            _uiState.value.getSaveData()?.let { urlData ->
                when (saveUrlUseCase(urlData, force = true)) {
                    is SaveResult.Success -> afterSave(urlData)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun afterSave(urlData: UrlData) {
        val finalCategory = urlData.category
        if (!finalCategory.isNullOrEmpty() && finalCategory != FilterDefaults.CATEGORY_ALL) {
            val existingCategories = getCategoriesUseCase()
            if (existingCategories.none { it.name == finalCategory }) {
                insertCategoryUseCase(
                    CategoryModel(
                        name = finalCategory,
                        addDate = System.currentTimeMillis()
                    )
                )
            }
            learnDomainCategoryUseCase(urlData.url ?: "", finalCategory)
        }
        _uiEffect.emit(LinkSaveUiEffect.GotoNextScreen())
    }

    private fun removeTag(tag: String) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(tagList = (current.tagList ?: emptyList()).filter { it != tag })
            }
        }
    }

    private fun insertTag(tags: List<String>) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(tagList = (current.tagList ?: emptyList()) + tags)
            }
        }
    }

    private fun bookMarkToggle(isBookMark: Boolean) {
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isBookMark = isBookMark) }
        }
    }

    private fun crawlerLoading(loadingUrl: String) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading, userInputUrl = loadingUrl)
            }
        }
    }

    private fun webViewCrawlerStateResult(data: UrlData?) {
        viewModelScope.launch {
            val isStateLoading = _uiState.value.linkUrlPreviewUiState == LinkUrlPreviewUiState.Loading
            if (!isStateLoading) return@launch

            if (data != null) {
                _uiState.update { current ->
                    current.copy(
                        linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(data),
                        isEditScreen = false
                    )
                }
            } else {
                // WebView도 실패 → 수동 입력 다이얼로그
                _uiEffect.emit(LinkSaveUiEffect.ShowCrawlFailedDialog(_uiState.value.userInputUrl))
                _uiState.update { current ->
                    current.copy(
                        linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(
                            urlData = UrlData(url = current.userInputUrl, description = current.userInputUrl)
                        ),
                        isEditScreen = false
                    )
                }
            }
        }
    }

    fun startCrawling(url: String) {
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            try {
                val realUrl = extractUrlFromText(url)
                if (!realUrl.isNullOrEmpty()) {
                    findUrlDataUseCase(realUrl)?.let {
                        _uiState.update { current ->
                            current.copy(
                                isEditScreen = true,
                                userInputUrl = realUrl,
                                categoryName = it.category ?: FilterDefaults.CATEGORY_ALL,
                                isBookMark = it.isBookMark,
                                tagList = it.tagList ?: emptyList(),
                                linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = it)
                            )
                        }
                    } ?: run {
                        _uiState.update { current ->
                            current.copy(isEditScreen = false, userInputUrl = url, linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading)
                        }
                        val data = parseUrlUseCase(realUrl)
                        if (data.title.isNullOrEmpty()) {
                            _uiEffect.emit(LinkSaveUiEffect.StartCrawling(url))
                        } else {
                            val checkData = findUrlDataUseCase(data.url ?: "")
                            val suggested = suggestCategoryUseCase(
                                url = data.url ?: realUrl,
                                title = data.title,
                                description = data.description,
                            )
                            suggestedCategory = suggested
                            _uiState.update { current ->
                                current.copy(
                                    isEditScreen = data.title == (checkData?.title ?: ""),
                                    userInputUrl = realUrl,
                                    categoryName = suggested ?: current.categoryName,
                                    linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = data)
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
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
            val categories = getCategoriesUseCase()
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
                    categoryName = data.category ?: FilterDefaults.CATEGORY_ALL,
                    isBookMark = data.isBookMark,
                    tagList = data.tagList ?: emptyList(),
                    linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = data)
                )
            }
        }
    }
}
