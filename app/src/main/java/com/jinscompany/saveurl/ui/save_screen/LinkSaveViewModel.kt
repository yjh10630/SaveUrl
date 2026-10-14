package com.jinscompany.saveurl.ui.save_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.autocat.AutoCategorySaveFlow
import com.jinscompany.saveurl.domain.autocat.AutoRuleStats
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.domain.repository.BulkMoveResult
import com.jinscompany.saveurl.domain.usecase.FindUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.ParseUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SaveUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SaveResult
import com.jinscompany.saveurl.domain.usecase.UpdateUrlUseCase
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.autocat.RuleSheetRequest
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
    private val lockSession: CategoryLockSession,
    private val observeLockedCategoriesUseCase: ObserveLockedCategoriesUseCase,
    private val autoCategorySaveFlow: AutoCategorySaveFlow,
    private val autoCategoryRepository: AutoCategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkSaveUiState())
    val uiState: StateFlow<LinkSaveUiState> = _uiState

    private val _uiEffect = MutableSharedFlow<LinkSaveUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    private var parseJob: Job? = null
    /** 수정 모드로 열 때 읽은 저장된 행 (저장할 때 그사이 채워진 미리보기를 지키는 기준) */
    private var editOriginal: UrlData? = null
    private var isInitialUrlHandled = false
    /** 칩으로 규칙을 바꾸거나 껐음 → 이번 저장은 규칙 기록에 넣지 않는다 (36번 §2.5-2) */
    private var ruleActionTaken = false
    /** "앞으로 항상"에서 기존 링크를 함께 옮긴 결과 (되돌리기) */
    private var createdMove: BulkMoveResult? = null

    init {
        viewModelScope.launch {
            observeLockedCategoriesUseCase().collect { names ->
                _uiState.update { it.copy(lockedCategoryNames = names) }
            }
        }
        viewModelScope.launch {
            lockSession.isUnlocked.drop(1).collect { unlocked -> if (!unlocked) onRelocked() }
        }
        viewModelScope.launch {
            val enabled = runCatching { autoCategorySaveFlow.isEnabled() }.getOrDefault(false)
            _uiState.update { it.copy(autoCatEnabled = enabled) }
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
            _uiState.update {
                it.copy(categoryName = FilterDefaults.CATEGORY_ALL, categorySource = CategorySource.NONE, autoCatDone = null)
            }
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
            LinkSaveIntent.MakeRuleClick -> openMakeRuleSheet()
            is LinkSaveIntent.RuleCreated -> onRuleCreated(intent)
            LinkSaveIntent.UpdateRuleChip -> updateRuleFromChip()
            LinkSaveIntent.DisableRuleChip -> disableRuleFromChip()
            LinkSaveIntent.OnceChip -> _uiState.update { s ->
                s.copy(autoCatDone = AutoCatDone.Once(s.categoryName.takeIf { LinkSaveUiState.isRealCategory(it) }), autoCatCanUndo = false)
            }
            LinkSaveIntent.UndoAutoCat -> undoAutoCat()
        }
    }

    // ---- 자동 분류 판정 (36번 §2.1) ----

    /**
     * [url] 로 규칙을 판정한다. 사용자가 직접 고른 값(USER)과 수정 모드(STORED)는 덮지 않는다.
     * 규칙이 없으면 "선택 안 함"으로 둔다 (학습·추측 없음).
     */
    private suspend fun evaluateRule(url: String?) {
        val keys = AutoCategoryKeys.keysOf(url)
        val keyRuleState = keys?.let { runCatching { autoCategoryRepository.findByKey(it.defaultKey) }.getOrNull() }
            ?.ruleState?.takeIf { it != AutoRuleState.DISMISSED }
        val enabled = runCatching { autoCategorySaveFlow.isEnabled() }.getOrDefault(false)
        val matched = if (enabled) runCatching { autoCategorySaveFlow.match(url) }.getOrNull() else null
        val matchedRule = matched?.let { AppliedRule(it.rule.id, it.rule.key, it.rule.categoryId, it.categoryName) }
        val keysChanged = _uiState.value.urlKeys != keys
        if (keysChanged) {
            // 다른 사이트로 바뀌면 옛 규칙에 한 칩 동작·만들기 결과는 이 URL 과 상관없다 (40번 독립 리뷰 R7)
            ruleActionTaken = false
            createdMove = null
        }
        _uiState.update { s ->
            val base = s.copy(urlKeys = keys, keyRuleState = keyRuleState, autoCatEnabled = enabled)
            when (s.categorySource) {
                CategorySource.STORED -> base
                // 사용자가 고른 값은 그대로 두고, 비교 기준 규칙만 새 URL 것으로 (옛 규칙 칩·기록이 남지 않게)
                CategorySource.USER -> if (keysChanged) {
                    base.copy(appliedRule = matchedRule, autoCatDone = null, autoCatCanUndo = false)
                } else base
                CategorySource.NONE, CategorySource.RULE -> if (matchedRule != null) {
                    base.copy(
                        categoryName = matchedRule.categoryName,
                        categorySource = CategorySource.RULE,
                        appliedRule = matchedRule,
                        autoCatDone = null,
                    )
                } else if (s.categorySource == CategorySource.RULE) {
                    base.copy(categoryName = FilterDefaults.CATEGORY_ALL, categorySource = CategorySource.NONE, appliedRule = null)
                } else base
            }
        }
    }

    private fun openMakeRuleSheet() {
        viewModelScope.launch {
            val state = _uiState.value
            val keys = state.urlKeys ?: return@launch
            val category = getCategoriesUseCase(includeLocked = false).firstOrNull { it.name == state.categoryName } ?: return@launch
            val existing = autoCategoryRepository.findByKey(keys.defaultKey)
            val request = if (existing != null && existing.ruleState == AutoRuleState.OFF) {
                // G: 꺼진 규칙 → 수정 모드로 열고 저장하면 켠다
                RuleSheetRequest.Edit(existing.id, presetCategoryId = category.id, activateOnSave = true)
            } else {
                RuleSheetRequest.Create(keys, category.id, category.name)
            }
            _uiEffect.emit(LinkSaveUiEffect.OpenRuleSheet(request))
        }
    }

    private fun onRuleCreated(intent: LinkSaveIntent.RuleCreated) {
        val moved = intent.move?.count ?: 0
        createdMove = intent.move?.takeIf { moved > 0 }
        _uiState.update {
            it.copy(
                autoCatDone = AutoCatDone.RuleCreated(intent.siteName, intent.categoryName, moved),
                autoCatCanUndo = moved > 0,
                keyRuleState = AutoRuleState.ACTIVE,
            )
        }
    }

    private fun updateRuleFromChip() {
        viewModelScope.launch {
            val state = _uiState.value
            val applied = state.appliedRule ?: return@launch
            val chosen = getCategoriesUseCase(includeLocked = false).firstOrNull { it.name == state.categoryName } ?: return@launch
            autoCategoryRepository.setCategory(applied.ruleId, chosen.id)
            ruleActionTaken = true
            _uiState.update {
                it.copy(
                    appliedRule = applied.copy(categoryId = chosen.id, categoryName = chosen.name),
                    autoCatDone = AutoCatDone.RuleUpdated(applied.siteName, chosen.name, applied.categoryId),
                    autoCatCanUndo = true,
                )
            }
        }
    }

    private fun disableRuleFromChip() {
        viewModelScope.launch {
            val applied = _uiState.value.appliedRule ?: return@launch
            autoCategoryRepository.setState(applied.ruleId, AutoRuleState.OFF)
            ruleActionTaken = true
            _uiState.update { it.copy(autoCatDone = AutoCatDone.RuleDisabled(applied.siteName), autoCatCanUndo = true) }
        }
    }

    private fun undoAutoCat() {
        viewModelScope.launch {
            val state = _uiState.value
            when (val done = state.autoCatDone) {
                is AutoCatDone.RuleUpdated -> {
                    val applied = state.appliedRule ?: return@launch
                    autoCategoryRepository.setCategory(applied.ruleId, done.previousCategoryId)
                    val previousName = getCategoriesUseCase(includeLocked = false).firstOrNull { it.id == done.previousCategoryId }?.name
                        ?: return@launch
                    _uiState.update {
                        it.copy(appliedRule = applied.copy(categoryId = done.previousCategoryId, categoryName = previousName), autoCatDone = null)
                    }
                }
                is AutoCatDone.RuleDisabled -> {
                    val applied = state.appliedRule ?: return@launch
                    autoCategoryRepository.setState(applied.ruleId, AutoRuleState.ACTIVE)
                    _uiState.update { it.copy(autoCatDone = null) }
                }
                is AutoCatDone.RuleCreated -> {
                    createdMove?.let { autoCategoryRepository.undoMove(it) }
                    createdMove = null
                    _uiState.update {
                        it.copy(autoCatDone = done.copy(moved = 0), autoCatCanUndo = false)
                    }
                }
                else -> Unit
            }
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
            _uiState.update { current ->
                // 시트를 열었다 닫기만 하면(값이 같으면) 출처를 바꾸지 않는다: 자동 라벨이 사라지면 안 됨 (36번 §2.4)
                if (current.categoryName == categoryName) current
                else current.copy(
                    categoryName = categoryName,
                    categorySource = if (current.isEditScreen) CategorySource.STORED else CategorySource.USER,
                    autoCatDone = null,
                    autoCatCanUndo = false,
                )
            }
        }
    }

    private fun saveLink() {
        viewModelScope.launch {
            val urlData = _uiState.value.getSaveData() ?: return@launch
            ensureCategoryExists(urlData)
            if (_uiState.value.isEditScreen) {
                // 그사이 미리보기 채우기가 채운 값은 살리고, 사용자가 바꾼 칸만 쓴다 (39번 독립 리뷰 R1)
                val original = editOriginal
                val fresh = findUrlDataUseCase(original?.url ?: urlData.url ?: _uiState.value.userInputUrl)
                val edited = EditedLinkMerger.merge(original, urlData, fresh)
                updateUrlUseCase(edited)
                afterSave(edited, isNew = false)
            } else {
                when (val result = saveUrlUseCase(urlData)) {
                    is SaveResult.Duplicate -> {
                        _uiEffect.emit(LinkSaveUiEffect.ShowDuplicateDialog(result.existing, hideTitle = result.existing.isHiddenByLock()))
                        return@launch
                    }
                    is SaveResult.Success -> afterSave(urlData, isNew = true)
                    SaveResult.Error -> return@launch
                }
            }
        }
    }

    private fun forceSaveLink() {
        viewModelScope.launch {
            val urlData = _uiState.value.getSaveData() ?: return@launch
            ensureCategoryExists(urlData)
            when (saveUrlUseCase(urlData, force = true)) {
                is SaveResult.Success -> afterSave(urlData, isNew = true)
                else -> Unit
            }
        }
    }

    /**
     * 저장 순간 고른 카테고리가 없으면(다른 화면에서 지운 경합) "선택 안 함"으로 저장한다.
     * 예전처럼 없는 카테고리를 새로 만들지 않는다 (36번 §2.5-1).
     */
    private suspend fun ensureCategoryExists(urlData: UrlData) {
        val name = urlData.category
        if (!LinkSaveUiState.isRealCategory(name)) return
        val exists = runCatching { getCategoriesUseCase(includeLocked = true).any { it.name == name } }.getOrDefault(true)
        if (!exists) {
            urlData.category = FilterDefaults.CATEGORY_ALL
            _uiState.update { it.copy(categoryName = FilterDefaults.CATEGORY_ALL) }
            _uiEffect.emit(LinkSaveUiEffect.ShowToast(R.string.autocat_category_missing_toast))
        }
    }

    private suspend fun afterSave(urlData: UrlData, isNew: Boolean) {
        if (isNew) {
            val state = _uiState.value
            runCatching {
                val savedId = if (LinkSaveUiState.isRealCategory(urlData.category)) {
                    getCategoriesUseCase(includeLocked = true).firstOrNull { it.name == urlData.category }?.id ?: AutoRuleStats.NONE_CATEGORY_ID
                } else AutoRuleStats.NONE_CATEGORY_ID
                // 기록·제안 계산은 기다리지 않는다: 화면은 바로 닫힌다 (40번 독립 리뷰 R6)
                autoCategorySaveFlow.afterSaveInBackground(
                    savedUrl = urlData.url ?: state.userInputUrl,
                    savedCategoryName = urlData.category,
                    savedCategoryId = savedId,
                    // 잠금 카테고리에 저장했으면 기록하지 않는다: 점검 시트 최근 기록에 잠금 카테고리가 남지 않게 (40번 독립 리뷰 R12)
                    appliedRuleId = state.appliedRule?.ruleId?.takeUnless { ruleActionTaken || urlData.category in state.lockedCategoryNames },
                )
            }.onFailure { if (it is CancellationException) throw it else Firebase.crashlytics.recordException(it) }
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
                // 폴백 경로도 같은 판정 (WebView 가 따라간 최종 URL)
                data.url?.takeIf { it.startsWith("http") }?.let { evaluateRule(it) }
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
                                categorySource = CategorySource.STORED,
                                appliedRule = null,
                                autoCatDone = null,
                                isBookMark = it.isBookMark,
                                tagList = it.tagList ?: emptyList(),
                                linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = it)
                            )
                        }
                    } ?: run {
                        _uiState.update { current ->
                            current.copy(
                                isEditScreen = false, userInputUrl = url, linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading,
                                // 다른 URL 로 바꾸면 사용자가 고른 값이 아닐 때만 다시 판정한다 (36번 §2.1-3).
                                // 저장된 링크를 보다가 바꿨으면 그 링크의 카테고리를 들고 가지 않는다: "선택 안 함"부터 (40번 독립 리뷰 R10)
                                categorySource = if (current.categorySource == CategorySource.STORED) CategorySource.NONE else current.categorySource,
                                categoryName = if (current.categorySource == CategorySource.STORED) FilterDefaults.CATEGORY_ALL else current.categoryName,
                                autoCatDone = null,
                            )
                        }
                        // 수집을 기다리지 않고 처음 URL 로 바로 판정 (공유 직후 라벨이 보이도록)
                        evaluateRule(realUrl)
                        val data = parseUrlUseCase(realUrl)
                        if (data.title.isNullOrEmpty()) {
                            // 공유 텍스트 전체가 아닌 추출된 URL 로 WebView 폴백 (리다이렉트 해석된 URL 우선)
                            _uiEffect.emit(LinkSaveUiEffect.StartCrawling(data.url?.takeIf { it.startsWith("http") } ?: realUrl))
                        } else {
                            val checkData = findUrlDataUseCase(data.url ?: "")?.takeUnless { it.isHiddenByLock() }
                            editOriginal = checkData?.copy()
                            val isEdit = data.title == (checkData?.title ?: "")
                            _uiState.update { current ->
                                current.copy(
                                    isEditScreen = isEdit,
                                    userInputUrl = realUrl,
                                    // 해석된 URL 이 이미 저장된 링크면 추천값이 아니라 저장된 카테고리 (예전 252~263행 버그)
                                    categoryName = if (isEdit) checkData?.category ?: FilterDefaults.CATEGORY_ALL else current.categoryName,
                                    categorySource = if (isEdit) CategorySource.STORED else current.categorySource,
                                    appliedRule = if (isEdit) null else current.appliedRule,
                                    linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = data)
                                )
                            }
                            // 최종 URL(단축 링크 해제)로 한 번 더 판정
                            if (!isEdit) evaluateRule(data.url ?: realUrl)
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
            val state = _uiState.value
            val ruleDescription = state.appliedRule
                ?.takeIf { state.categorySource == CategorySource.RULE && state.autoCatEnabled }
                ?.let { it.siteName to it.categoryName }
            _uiEffect.emit(LinkSaveUiEffect.OpenCategorySelector(categories, ruleDescription))
        }
    }

    fun userSelectLinkEditMode(data: UrlData) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isEditScreen = true,
                    userInputUrl = data.url ?: "",
                    categoryName = data.category ?: FilterDefaults.CATEGORY_ALL,
                    categorySource = CategorySource.STORED,
                    isBookMark = data.isBookMark,
                    tagList = data.tagList ?: emptyList(),
                    linkUrlPreviewUiState = LinkUrlPreviewUiState.LinkUrlData(urlData = data)
                )
            }
        }
    }
}
