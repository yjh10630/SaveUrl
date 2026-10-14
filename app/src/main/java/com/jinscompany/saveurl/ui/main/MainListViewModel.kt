package com.jinscompany.saveurl.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.jinscompany.saveurl.data.revisit.RevisitClock
import com.jinscompany.saveurl.data.revisit.RevisitRepository
import com.jinscompany.saveurl.domain.model.LinkReminder
import com.jinscompany.saveurl.domain.revisit.ReminderPreset
import com.jinscompany.saveurl.domain.revisit.ReminderTimes
import com.jinscompany.saveurl.domain.revisit.RevisitRules
import com.jinscompany.saveurl.domain.revisit.WeeklyPromptState
import com.jinscompany.saveurl.ui.revisit.ReminderPickRequest
import com.jinscompany.saveurl.ui.revisit.RevisitOpenInbox
import com.jinscompany.saveurl.ui.revisit.RevisitOpenRequest
import com.jinscompany.saveurl.domain.model.UpcomingReminder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.usecase.DeleteWithTrashUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.GetSiteNameListUseCase
import com.jinscompany.saveurl.domain.usecase.GetTagListUseCase
import com.jinscompany.saveurl.domain.usecase.GetTrashStateUseCase
import com.jinscompany.saveurl.domain.usecase.GetUrlListUseCase
import com.jinscompany.saveurl.domain.usecase.IsSavedUrlUseCase
import com.jinscompany.saveurl.domain.usecase.MarkAsReadUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.RemoveUrlUseCase
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
import com.jinscompany.saveurl.ui.tutorial.TutorialBadgePrefs
import com.jinscompany.saveurl.ui.tutorial.isTutorialLink
import dagger.hilt.android.lifecycle.HiltViewModel
import com.jinscompany.saveurl.domain.importer.MetaFillScheduler
import com.jinscompany.saveurl.domain.model.MetaFillProgress
import com.jinscompany.saveurl.domain.repository.ImportRepository
import com.jinscompany.saveurl.ui.importer.MainListRequests
import com.jinscompany.saveurl.utils.RemoteFlags
import com.jinscompany.saveurl.domain.autocat.AutoCategoryNotice
import com.jinscompany.saveurl.domain.autocat.AutoCategoryNotices
import com.jinscompany.saveurl.domain.autocat.RuleSuggestion
import com.jinscompany.saveurl.domain.repository.SuggestionAcceptance
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainListViewModel @Inject constructor(
    private val getUrlListUseCase: GetUrlListUseCase,
    private val removeUrlUseCase: RemoveUrlUseCase,
    private val isSavedUrlUseCase: IsSavedUrlUseCase,
    private val getTrashStateUseCase: GetTrashStateUseCase,
    private val deleteWithTrashUseCase: DeleteWithTrashUseCase,
    private val trashRepository: TrashRepository,
    private val preferencesManager: PreferencesManager,
    private val clipboardReader: ClipboardReader,
    private val markAsReadUseCase: MarkAsReadUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getSiteNameListUseCase: GetSiteNameListUseCase,
    private val getTagListUseCase: GetTagListUseCase,
    private val lockSession: CategoryLockSession,
    observeLockedCategoriesUseCase: ObserveLockedCategoriesUseCase,
    importRepository: ImportRepository,
    private val metaFillScheduler: MetaFillScheduler,
    private val remoteFlags: RemoteFlags,
    private val mainListRequests: MainListRequests,
    private val autoCategoryNotices: AutoCategoryNotices,
    private val autoCategoryRepository: AutoCategoryRepository,
    private val revisitRepository: RevisitRepository,
    private val revisitOpenInbox: RevisitOpenInbox,
    private val revisitClock: RevisitClock,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // ---- 다시 보게 만들기 (42번) ----
    /** 안 읽음 탭 숫자 (회색 작은 글씨) */
    val unreadCount: StateFlow<Int> = revisitRepository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 목록 ⏰ 표시: linkId → remindAt */
    val reminderTimes: StateFlow<Map<Int, Long>> = revisitRepository.observeReminderTimes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 2분할 홈 패널 "곧 올 알림" (시간 순) */
    val upcomingReminders: StateFlow<List<UpcomingReminder>> = revisitRepository.observeUpcoming()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "곧 올 알림" 행: 알림에서 연 것과 같은 경로 (잠금 링크는 인증 뒤) */
    fun openReminderLink(linkId: Int) = revisitOpenInbox.put(RevisitOpenRequest.Link(linkId))

    /** 안 읽음 탭에 들어온 뒤 연 링크 (목록에서 바로 빠지지 않게). 탭에 다시 들어오면 비운다 */
    private val unreadKeepIds = java.util.concurrent.CopyOnWriteArraySet<Int>()

    /** 오늘의 다시 볼 링크 카드 (null = 숨김) */
    private val _revisitCard = MutableStateFlow<UrlData?>(null)
    val revisitCard: StateFlow<UrlData?> = _revisitCard.asStateFlow()

    /** 주간 정리 제안 카드 (이 실행에서 보이면 다시 볼 링크 카드 대신) */
    private val _weeklyPrompt = MutableStateFlow(false)
    val weeklyPrompt: StateFlow<Boolean> = _weeklyPrompt.asStateFlow()
    private var weeklyPromptChecked = false

    /** 저장 직후 자동 분류 스낵바 (화면이 [takeAutoCategoryNotice] 로 받아 간다) */
    val autoCategoryNotice: StateFlow<AutoCategoryNotice?> = autoCategoryNotices.pending

    fun takeAutoCategoryNotice(): AutoCategoryNotice? = autoCategoryNotices.take()

    /**
     * 업데이트 후 첫 실행 자동 분류 카드 (36번 §4.2): 이 기능 전부터 쓰던 사용자 + 제안이 1개 이상 + 아직 닫거나 누르지 않음.
     * 값 = 제안 수. 이 실행에서 한 번 보였으면 튜토리얼 카드는 이 실행 동안 보이지 않는다.
     */
    private val _autoCategoryLaunchCard = MutableStateFlow<Int?>(null)
    val autoCategoryLaunchCard: StateFlow<Int?> = _autoCategoryLaunchCard.asStateFlow()
    private val _autoCategoryCardShownThisRun = MutableStateFlow(false)
    val autoCategoryCardShownThisRun: StateFlow<Boolean> = _autoCategoryCardShownThisRun.asStateFlow()

    /** 링크 가져오기 원격 스위치 (빈 화면 버튼) */
    val importEnabled: StateFlow<Boolean> = remoteFlags.importEnabled

    /** 가져온 링크 미리보기 채우기 진행 (상태 카드). 잠금 카테고리 링크는 세지 않는다 */
    val metaFillProgress: StateFlow<MetaFillProgress> = importRepository.observeFillProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetaFillProgress(0, 0))

    /** 모바일 데이터에서도 채우기 (상태 카드의 "Wi-Fi에서만" 표시) */
    val metaFillCellular: StateFlow<Boolean> = preferencesManager.metaFillCellular
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 잠금 카테고리 세션 (칩 줄: 닫힘 = 🔒 칩, 열림 = 잠금 카테고리 칩 + 다시 잠그기) */
    val isUnlocked: StateFlow<Boolean> = lockSession.isUnlocked

    /** 잠금 카테고리 이름. 세션이 닫혀 있으면 화면은 개수(🔒 칩 표시 여부)만 쓴다 */
    val lockedCategoryNames: StateFlow<List<String>> = observeLockedCategoriesUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** 카테고리 칩 행에 보여줄 사용자 카테고리 이름 ("전체" 제외, 잠금 카테고리 제외: 잠금 카테고리 칩은 [lockedCategoryNames]) */
    private val _categoryNames = MutableStateFlow<List<String>>(emptyList())
    val categoryNames: StateFlow<List<String>> = _categoryNames.asStateFlow()

    /** 메인 목록 맨 위 첫 실행 안내 카드 (새로 설치한 사용자, ✕ 로 닫기 전까지) */
    val tutorialCardVisible: StateFlow<Boolean> = preferencesManager.tutorialCardVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 새 기능 안내 카드·NEW 판단 재료. null = 읽는 중 (화면에서 보이는 주제로 거른다: TutorialNews.cardMode) */
    val tutorialBadgePrefs: StateFlow<TutorialBadgePrefs?> = preferencesManager.tutorialBadgePrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // 가져오기 완료 [목록에서 보기]: 최근 저장 탭 + 그 카테고리(여러 개면 전체)
        mainListRequests.takeCategory()?.let { category ->
            _selectedTab.value = MainTab.RECENT
            _filterSelectedItems.value = _filterSelectedItems.value.copy(
                categories = listOf(category.ifEmpty { FilterDefaults.CATEGORY_ALL }), siteList = listOf(), tagList = listOf(),
            )
        }
        getLinkList()
        deleteExpiredTrash()
        loadCategories()
        observeLockState()
        loadAutoCategoryLaunchCard()
        observeTabRequests()
        loadRevisitCards()
    }

    /** 알림 "안 읽은 링크 보기" → 안 읽음 탭 + 전체 (42번 §2.7) */
    private fun observeTabRequests() {
        viewModelScope.launch {
            revisitOpenInbox.tabRequests.collect { tab ->
                revisitOpenInbox.consumeTabRequest()
                unreadKeepIds.clear()
                _selectedTab.value = tab
                _filterSelectedItems.value = _filterSelectedItems.value.copy(
                    categories = listOf(FilterDefaults.CATEGORY_ALL), siteList = listOf(), tagList = listOf(),
                )
                getLinkList()
                loadRevisitCards()
            }
        }
    }

    /**
     * 메인 상단 카드: 주간 정리 제안(조건이 되면 이 실행 동안) > 다시 볼 링크 (42번 §4.1·§5.1).
     * 다시 볼 링크는 "최근 저장 · 전체 · 필터 없음"에서만 고른다 (고르는 순간 표시 시각을 기록하므로 다른 화면에선 고르지 않음).
     */
    private fun loadRevisitCards() {
        viewModelScope.launch {
            runCatching {
                if (!weeklyPromptChecked) {
                    weeklyPromptChecked = true
                    _weeklyPrompt.value = shouldShowWeeklyPrompt()
                }
                if (_weeklyPrompt.value) {
                    _revisitCard.value = null
                    return@runCatching
                }
                val params = _filterSelectedItems.value
                val context = RevisitRules.isCardContext(
                    isRecentTab = _selectedTab.value == MainTab.RECENT,
                    isAllCategory = params.categories.contains(FilterDefaults.CATEGORY_ALL),
                    hasDetailFilter = params.siteList.isNotEmpty() || params.tagList.isNotEmpty(),
                )
                val today = ReminderTimes.epochDay(revisitClock.now(), revisitClock.zone())
                val enabled = remoteFlags.revisitCardEnabled.value && preferencesManager.revisitCardEnabled.first() &&
                    preferencesManager.revisitCardClosedDay.first() != today
                _revisitCard.value = if (context && enabled) revisitRepository.todaysRevisitLink() else null
            }
        }
    }

    private suspend fun shouldShowWeeklyPrompt(): Boolean {
        val installedAt = preferencesManager.appInstalledAt()
        val now = revisitClock.now()
        val show = RevisitRules.shouldShowWeeklyPrompt(
            remoteEnabled = remoteFlags.weeklyDigestEnabled.value,
            promptState = preferencesManager.weeklyPromptState.first(),
            directLinks = revisitRepository.countDirectLinks(),
            minLinks = remoteFlags.weeklyPromptMinLinks,
            installedAt = installedAt,
            now = now,
        ) && !preferencesManager.weeklyDigestEnabled.first()
        if (!show) return false
        return preferencesManager.countWeeklyPromptShow(
            epochDay = ReminderTimes.epochDay(now, revisitClock.zone()),
            max = RevisitRules.WEEKLY_PROMPT_MAX_SHOWS,
            autoHiddenState = WeeklyPromptState.AUTO_HIDDEN,
        )
    }

    /** 다시 볼 링크 [열기] (카드 전체 눌러도 같음): 기존 열기 경로 → 그날 카드 숨김 */
    fun openRevisitCard(link: UrlData) {
        _revisitCard.value = null
        onIntent(MainListIntent.GoToOutLinkWebSite(link.url, link.id))
    }

    /** ✕: 오늘은 닫기 */
    fun closeRevisitCardToday() {
        _revisitCard.value = null
        viewModelScope.launch {
            preferencesManager.setRevisitCardClosedDay(ReminderTimes.epochDay(revisitClock.now(), revisitClock.zone()))
        }
    }

    /** 스낵바 [그만 보기] → 확인 → 스위치 끔 */
    fun stopRevisitCard() {
        _revisitCard.value = null
        viewModelScope.launch { preferencesManager.setRevisitCardEnabled(false) }
    }

    /** 주간 정리 제안 [받기]: 권한 결과에 따라 켜고(허용) 다시 묻지 않음 */
    fun acceptWeeklyPrompt(granted: Boolean) {
        _weeklyPrompt.value = false
        viewModelScope.launch {
            preferencesManager.setWeeklyPromptState(WeeklyPromptState.ACCEPTED)
            if (granted) {
                preferencesManager.setWeeklyDigestEnabled(true)
                revisitRepository.syncWeekly()
            }
        }
    }

    /** 주간 정리 제안 [괜찮아요] */
    fun declineWeeklyPrompt() {
        _weeklyPrompt.value = false
        viewModelScope.launch { preferencesManager.setWeeklyPromptState(WeeklyPromptState.DECLINED) }
    }

    /** 시간을 골랐음 (권한 확인 뒤). @return 예전 예약 (실행 취소용) */
    suspend fun setReminder(linkId: Int, preset: ReminderPreset, at: Long): LinkReminder? = viewModelScope.async {
        val previous = revisitRepository.getReminder(linkId)
        revisitRepository.setReminder(linkId, at, preset)
        previous
    }.await()

    suspend fun cancelReminder(linkId: Int): LinkReminder? = viewModelScope.async {
        val previous = revisitRepository.getReminder(linkId)
        revisitRepository.cancelReminder(linkId)
        previous
    }.await()

    fun restoreReminder(linkId: Int, previous: LinkReminder?) {
        viewModelScope.launch { revisitRepository.restoreReminder(linkId, previous) }
    }

    private fun loadAutoCategoryLaunchCard() {
        viewModelScope.launch {
            runCatching {
                if (!preferencesManager.autoCategoryEnabled.first()) return@runCatching
                if (preferencesManager.autoCategoryLaunchCardDone.first()) return@runCatching
                val baseline = preferencesManager.tutorialBadgePrefs.first().baselineVersionCode ?: return@runCatching
                // 새로 설치한 사용자(기준 = 설치 버전)는 기록이 없으므로 카드 없음
                if (baseline >= AUTO_CATEGORY_ADDED_VERSION_CODE) return@runCatching
                val count = autoCategoryRepository.suggestions(remoteFlags.autoCategoryThresholds).size
                if (count > 0) {
                    _autoCategoryLaunchCard.value = count
                    _autoCategoryCardShownThisRun.value = true
                } else {
                    // 업데이트 후 첫 실행에만 판단한다: 제안이 없으면 끝난 것으로 기록해 실행할 때마다 다시 계산하지 않고,
                    // 몇 주 뒤 제안이 생겨도 "업데이트 후" 카드가 뜨지 않게 (40번 독립 리뷰 R6)
                    preferencesManager.setAutoCategoryLaunchCardDone()
                }
            }
        }
    }

    /** 첫 실행 카드 닫기·누르기: 다시 보이지 않는다 */
    fun finishAutoCategoryLaunchCard() {
        _autoCategoryLaunchCard.value = null
        viewModelScope.launch { preferencesManager.setAutoCategoryLaunchCardDone() }
    }

    /**
     * 제안 스낵바 "규칙 만들기" (1탭 수락). @return null = 그사이 같은 사이트 규칙이 생겨 아무것도 바꾸지 않음
     * ViewModel 범위에서 저장한다: 누른 직후 화면을 떠나 스낵바 코루틴이 취소돼도 규칙은 만들어진다.
     */
    suspend fun acceptSuggestion(suggestion: RuleSuggestion): SuggestionAcceptance? = viewModelScope.async {
        autoCategoryRepository.acceptSuggestion(suggestion.key, suggestion.categoryId)
    }.await()

    /** 수락 확인 스낵바 "되돌리기": 수락 전 상태로 (행 없음 또는 거절 기록) */
    fun undoAcceptedSuggestion(acceptance: SuggestionAcceptance) {
        viewModelScope.launch { autoCategoryRepository.undoAcceptSuggestion(acceptance) }
    }

    private fun observeLockState() {
        // 잠그거나 풀면 보이는 카테고리 칩이 바뀐다 (목록 자체는 Room 이 Category 테이블 변경으로 다시 읽음)
        viewModelScope.launch {
            lockedCategoryNames.drop(1).collect { loadCategories() }
        }
        // 세션이 바뀌면 목록을 다시 만든다 (includeLocked 는 PagingSource 를 만들 때 정해지므로).
        // 닫힐 때 잠금 카테고리를 보고 있었으면 "전체"로 돌아가고, 잠금 링크에만 있던 사이트·태그 필터는 뺀다
        // (상세 필터 칩 줄에 이름이 남지 않게. 38번 리뷰 4번)
        viewModelScope.launch {
            lockSession.isUnlocked.drop(1).collect { unlocked ->
                if (!unlocked) _filterSelectedItems.value = prunedForLockedSession(_filterSelectedItems.value)
                getLinkList()
            }
        }
    }

    private suspend fun prunedForLockedSession(current: FilterParams): FilterParams {
        val lockedNames = lockedCategoryNames.value
        val categories = current.categories.filterNot { it in lockedNames }.ifEmpty { listOf(FilterDefaults.CATEGORY_ALL) }
        val sites = if (current.siteList.isEmpty()) current.siteList else {
            val visible = runCatching { getSiteNameListUseCase() }.getOrDefault(emptyList()).toSet()
            current.siteList.filter { it in visible }
        }
        val tags = if (current.tagList.isEmpty()) current.tagList else {
            val visible = runCatching { getTagListUseCase() }.getOrDefault(emptyList()).toSet()
            current.tagList.filter { it in visible }
        }
        return current.copy(categories = categories, siteList = sites, tagList = tags)
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val loaded = runCatching { getCategoriesUseCase(includeLocked = false) }.getOrNull() ?: return@launch
            _categoryNames.value = loaded
                .map { it.name }
                .filter { it != FilterDefaults.CATEGORY_ALL && it != FilterDefaults.CATEGORY_BOOKMARK }
                .distinct()
            // 보고 있던 카테고리가 사라졌으면(가져오기 되돌리기·카테고리 삭제) 전체로 돌아간다
            val known = _categoryNames.value.toSet() + lockedCategoryNames.value + FilterDefaults.CATEGORY_ALL + FilterDefaults.CATEGORY_BOOKMARK
            val current = _filterSelectedItems.value
            val kept = current.categories.filter { it in known }
            if (kept.size != current.categories.size) {
                _filterSelectedItems.value = current.copy(categories = kept.ifEmpty { listOf(FilterDefaults.CATEGORY_ALL) })
                getLinkList()
            }
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
                    // 안 읽음 탭에서 연 링크는 탭에 다시 들어올 때까지 남긴다 (읽음이 되어 목록이 다시 읽히기 전에 넣는다)
                    if (_selectedTab.value == MainTab.UNREAD && intent.linkId != null) unreadKeepIds.add(intent.linkId)
                    if (!intent.url.isNullOrEmpty()) markAsReadUseCase(intent.url)
                    _mainListEffect.emit(
                        if (intent.url.isNullOrEmpty()) {
                            ShowToast(R.string.error_url_missing)
                        } else if (isTutorialLink(intent.url)) {
                            // 기존 사용자 DB 에 남은 첫 실행 시드 항목: 웹 페이지 대신 앱 안 튜토리얼
                            OpenTutorial
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
                // 저장 직후 자동 분류 스낵바가 먼저다 (같은 진입에서 클립보드 스낵바는 띄우지 않음, 36번 §4.3)
                MainListIntent.ReadClipboard -> if (!autoCategoryNotices.isRecentlyActive()) {
                    clipboardReader.readUrl()?.let { clipboardUrlCheckToSnackBar(it) }
                }
                MainListIntent.RefreshOnResume -> {
                    loadCategories()
                    loadRevisitCards()
                }
                is MainListIntent.SelectTab -> {
                    if (_selectedTab.value != intent.tab || intent.tab == MainTab.UNREAD) {
                        // 안 읽음 탭에 (다시) 들어오면 읽은 링크를 정리한다
                        unreadKeepIds.clear()
                        _selectedTab.value = intent.tab
                        getLinkList()
                        loadRevisitCards()
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
                    loadRevisitCards()
                }
                is MainListIntent.ShowLinkInfoDialog -> showLinkInfoDialog(intent.data)
                MainListIntent.OpenTutorial -> _mainListEffect.emit(OpenTutorial)
                MainListIntent.DismissTutorialCard -> preferencesManager.dismissTutorialCard()
                is MainListIntent.DismissTutorialNewCard -> preferencesManager.dismissTutorialNewCard(intent.versionCode)
                MainListIntent.RelockCategories -> lockSession.relock()
                is MainListIntent.PrioritizeMeta -> metaFillScheduler.prioritize(intent.linkId, intent.explicit)
                is MainListIntent.CancelPrioritizeMeta -> metaFillScheduler.cancelPriority(intent.linkId)
                MainListIntent.OpenImport -> _mainListEffect.emit(MainListUiEffect.OpenImport)
                MainListIntent.OpenImportHistory -> _mainListEffect.emit(MainListUiEffect.OpenImportHistory)
            }
        }
    }

    private fun showLinkInfoDialog(data: UrlData) {
        viewModelScope.launch {
            val isTrashEnable = getTrashStateUseCase()
            val reminderAt = revisitRepository.getReminder(data.id)?.remindAt
            val list = mutableListOf<SimpleMenuModel.MenuModel>(
                SimpleMenuModel.MenuModel(txtRes = R.string.main_list_menu_share, icon = Icons.Outlined.Share, event = {
                    viewModelScope.launch { onIntent(MainListIntent.GotoOutShareUrl(data.url)) }
                }),
                SimpleMenuModel.MenuModel(txtRes = R.string.main_list_menu_edit, icon = Icons.Outlined.Edit, event = {
                    viewModelScope.launch { onIntent(MainListIntent.GoToLinkEditScreen(url = data.url ?: "")) }
                }),
                // 읽음/안 읽음 표시 (42번 §1.4): 스낵바 없이 점만 바뀐다
                SimpleMenuModel.MenuModel(
                    txtRes = if (data.isRead) R.string.menu_mark_unread else R.string.menu_mark_read,
                    icon = if (data.isRead) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle,
                    event = { viewModelScope.launch { revisitRepository.setRead(data.id, !data.isRead) } },
                ),
                // 나중에 알림 받기 / 알림 바꾸기 · 시각 (42번 §2.1)
                reminderAt.let { at ->
                    SimpleMenuModel.MenuModel(
                        txt = if (at != null) appContext.getString(
                            R.string.menu_remind_change, ReminderTimes.menuLabel(at, revisitClock.now(), revisitClock.zone())
                        ) else "",
                        txtRes = if (at != null) null else R.string.menu_remind_later,
                        icon = Icons.Outlined.Alarm,
                        event = {
                            viewModelScope.launch {
                                _mainListEffect.emit(OpenReminderSheet(ReminderPickRequest(
                                    linkId = data.id,
                                    linkTitle = data.title.orEmpty().ifBlank { data.url.orEmpty() },
                                    currentAt = at,
                                )))
                            }
                        },
                    )
                },
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

            if (isTutorialLink(data.url)) list.removeAll { it.txtRes == R.string.main_list_menu_edit }
            if (isTutorialLink(data.url)) list.removeAll { it.icon == Icons.Outlined.Alarm }

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

            // 첫 실행: 예전처럼 튜토리얼 링크 항목을 넣지 않고, 메인 목록 맨 위 안내 카드를 켠다.
            // 기존 사용자의 시드 항목은 DB 에 그대로 두고, 누르면 앱 안 튜토리얼을 연다 (isTutorialLink).
            val isInitRunApp = preferencesManager.isInitFirstRun.first()
            if (!isInitRunApp) preferencesManager.completeFirstRunWithTutorialCard()

            val isUnreadTab = _selectedTab.value == MainTab.UNREAD
            val dataFlow = Pager(
                config = PagingConfig(pageSize = 10, prefetchDistance = 5, enablePlaceholders = false),
                // 안 읽음 탭: 다시 읽힐 때마다(읽음 처리 등) 지금까지 연 링크를 함께 넘긴다
                pagingSourceFactory = {
                    if (isUnreadTab) getUrlListUseCase.unread(params, unreadKeepIds.toList()) else getUrlListUseCase(queryParams)
                }
            ).flow.cachedIn(viewModelScope)
                .map { pagingData ->
                    val tagList = params.tagList
                    val byTag = if (tagList.isEmpty()) pagingData
                        else pagingData.filter { it.tagList?.any { it in tagList } == true }
                    if (filterBookmarkInMemory) byTag.filter { it.isBookMark } else byTag
                }
            _mainListUiState.value = MainListUiState.Success(urlFlowState = dataFlow)
        }
    }
}

/** 자동 분류가 처음 나가는 버전 (첫 실행 카드: 이 버전 전부터 쓰던 사용자만) */
const val AUTO_CATEGORY_ADDED_VERSION_CODE = 40
