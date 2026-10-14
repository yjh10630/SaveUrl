package com.jinscompany.saveurl.ui.save_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.usecase.FindUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.InsertCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.LearnDomainCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
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
    private val lockSession: CategoryLockSession,
    private val observeLockedCategoriesUseCase: ObserveLockedCategoriesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkSaveUiState())
    val uiState: StateFlow<LinkSaveUiState> = _uiState

    private val _uiEffect = MutableSharedFlow<LinkSaveUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    private var parseJob: Job? = null
    private var suggestedCategory: String? = null
    /** 수정 모드로 열 때 읽은 저장된 행 (저장할 때 그사이 채워진 미리보기를 지키는 기준) */
    private var editOriginal: UrlData? = null
    private var isInitialUrlHandled = false

    init {
        viewModelScope.launch {
            observeLockedCategoriesUseCase().collect { names ->
                _uiState.update { it.copy(lockedCategoryNames = names) }
            }
        }
        viewModelScope.launch {
            lockSession.isUnlocked.drop(1).collect { unlocked -> if (!unlocked) onRelocked() }
        }
    }

    /**
     * 다시 잠기면 잠금 카테고리 내용이 화면에 남지 않게 한다 (38번 리뷰 4번).
     * - 잠금 링크를 수정 중: 화면을 닫는다 (제목·태그·카테고리 이름이 보이므로)
     * - 새 링크에 잠금 카테고리를 골라 둔 상태: 카테고리 선택만 "선택 안 함"으로 되돌린다
     */
    private suspend fun onRelocked() {
        val state = _uiState.value
        val lockedNames = observeLockedCategoriesUseCase().first()
        if (state.isEditScreen) {
            val savedCategory = findUrlDataUseCase(state.userInputUrl)?.category
            if (state.categoryName in lockedNames || savedCategory in lockedNames) {
                _uiState.update { it.copy(isClosedByRelock = true) }
            }
        } else if (state.categoryName in lockedNames) {
            _uiState.update { it.copy(categoryName = FilterDefaults.CATEGORY_ALL) }
        }
    }

    /** 닫힌 잠금 카테고리에 있는 링크: 수정 모드로 열거나 제목을 보여 주면 안 된다 (37번 기획서 6.3) */
    private suspend fun UrlData.isHiddenByLock(): Boolean {
        val name = category ?: return false
        // 공유로 바로 열린 경우 위 수집보다 먼저 불릴 수 있으므로 DB 에서 직접 확인한다
        return !lockSession.isUnlocked.value && name in observeLockedCategoriesUseCase().first()
    }

    // 네비게이션 인자로 받은 URL 은 최초 진입 시 한 번만 처리
    fun startInitialCrawling(url: String?) {
        if (isInitialUrlHandled) return
        isInitialUrlHandled = true
        if (!url.isNullOrEmpty()) startCrawling(url)
    }

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
                    // 그사이 미리보기 채우기가 채운 값은 살리고, 사용자가 바꾼 칸만 쓴다 (39번 독립 리뷰 R1)
                    val original = editOriginal
                    val fresh = findUrlDataUseCase(original?.url ?: urlData.url ?: _uiState.value.userInputUrl)
                    val edited = EditedLinkMerger.merge(original, urlData, fresh)
                    updateUrlUseCase(edited)
                    afterSave(edited)
                } else {
                    when (val result = saveUrlUseCase(urlData)) {
                        is SaveResult.Duplicate -> {
                            _uiEffect.emit(LinkSaveUiEffect.ShowDuplicateDialog(result.existing, hideTitle = result.existing.isHiddenByLock()))
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
                    // 닫힌 잠금 카테고리의 링크는 수정 모드로 열지 않고 새 링크처럼 다룬다 (저장하면 중복 안내, 제목 없이)
                    findUrlDataUseCase(realUrl)?.takeUnless { it.isHiddenByLock() }?.let {
                        editOriginal = it.copy()
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
                            // 공유 텍스트 전체가 아닌 추출된 URL 로 WebView 폴백 (리다이렉트 해석된 URL 우선)
                            _uiEffect.emit(LinkSaveUiEffect.StartCrawling(data.url?.takeIf { it.startsWith("http") } ?: realUrl))
                        } else {
                            val checkData = findUrlDataUseCase(data.url ?: "")?.takeUnless { it.isHiddenByLock() }
                            val suggested = suggestCategoryUseCase(
                                url = data.url ?: realUrl,
                                title = data.title,
                                description = data.description,
                            )
                            suggestedCategory = suggested
                            editOriginal = checkData?.copy()
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
            // 잠금 카테고리도 넘긴다. 시트가 세션이 닫혀 있으면 "잠금 카테고리에 저장…" 한 행으로 접는다
            val categories = getCategoriesUseCase(includeLocked = true)
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
