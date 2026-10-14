package com.jinscompany.saveurl.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.rememberSaveable
import com.jinscompany.saveurl.ui.composable.SimpleMenuDropdown
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.CommonSimpleMenuBottomSheet
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.filter.FilterScreenBottomSheet
import com.jinscompany.saveurl.ui.main.MainListIntent.FetchCategoryData
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToAppSetting
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToCategorySettingScreen
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToLinkInsertScreen
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToOutLinkWebSite
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToSearchScreen
import com.jinscompany.saveurl.ui.main.MainListIntent.NewFilterData
import com.jinscompany.saveurl.ui.main.MainListIntent.ShowLinkInfoDialog
import com.jinscompany.saveurl.ui.main.components.ActiveFilterRow
import com.jinscompany.saveurl.ui.main.components.CategoryChipRow
import com.jinscompany.saveurl.ui.main.components.DateHeader
import com.jinscompany.saveurl.ui.main.components.LockedViewingBanner
import com.jinscompany.saveurl.ui.main.components.LinkListItem
import com.jinscompany.saveurl.ui.main.components.MainEmptyState
import com.jinscompany.saveurl.ui.main.components.MainEmptyType
import com.jinscompany.saveurl.ui.main.components.MainTabRow
import com.jinscompany.saveurl.ui.main.components.MainTopBar
import com.jinscompany.saveurl.ui.main.components.SortRow
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.APP_SETTING
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.EDIT_CATEGORY
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SEARCH
import com.jinscompany.saveurl.ui.navigation.navigateToAppSetting
import com.jinscompany.saveurl.ui.navigation.navigateToEditCategory
import com.jinscompany.saveurl.ui.navigation.navigateToSaveLink
import com.jinscompany.saveurl.ui.navigation.navigateToSearch
import com.jinscompany.saveurl.ui.navigation.navigateToTutorial
import com.jinscompany.saveurl.ui.lock.rememberOpenLockedCategories
import com.jinscompany.saveurl.ui.tutorial.TutorialCardMode
import com.jinscompany.saveurl.ui.tutorial.TutorialEntryCard
import com.jinscompany.saveurl.domain.model.MetaFillProgress
import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.ui.importer.ImportEntry
import com.jinscompany.saveurl.ui.navigation.navigateToImport
import com.jinscompany.saveurl.ui.navigation.navigateToImportHistory
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Row
import com.jinscompany.saveurl.ui.theme.AppDimens
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import com.jinscompany.saveurl.ui.tutorial.TutorialNews
import com.jinscompany.saveurl.ui.tutorial.EntryCardContent
import com.jinscompany.saveurl.domain.autocat.AutoCategoryNotice
import com.jinscompany.saveurl.ui.autocat.RuleSheetEvent
import com.jinscompany.saveurl.ui.autocat.RuleSheetHost
import com.jinscompany.saveurl.ui.autocat.RuleSheetRequest
import com.jinscompany.saveurl.ui.autocat.RuleSheetViewModel
import com.jinscompany.saveurl.ui.navigation.navigateToAutoCategory
import androidx.compose.material.icons.outlined.Bolt
import com.jinscompany.saveurl.ui.tutorial.currentVisibleTutorialTopics
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.openUrlInBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * 2분할 왼쪽 목록으로 그릴 때의 상태 ([com.jinscompany.saveurl.ui.adaptive.TwoPaneLayout]).
 * @param rightPaneShowsHome 오른쪽이 기본(홈 패널)일 때 true. 저장/편집 등이 열려 있으면 FAB 를 숨긴다.
 * @param selectedUrl 오른쪽에서 편집 중인 링크 URL (그 행을 선택 표시)
 * @param scrollToTop 저장 직후 목록 맨 위로 (메인 백스택 항목 인자)
 */
@Immutable
data class MainListPane(
    val rightPaneShowsHome: Boolean,
    val selectedUrl: String?,
    val scrollToTop: Boolean,
)

/**
 * 메인 목록.
 * - 폰: 메인 화면 전체 ([pane] = null). 동작은 기존과 같다.
 * - 2분할: 왼쪽 목록 패널 ([pane] != null). 링크 메뉴는 행의 ⋮ 드롭다운, 저장/편집은 오른쪽 패널에 열고,
 *   클립보드는 열 때 읽지 않는다 (홈 패널 빠른 저장 입력칸에 포커스할 때만 확인).
 */
@Composable
fun MainListScreen(
    navController: NavHostController,
    listState: LazyListState = rememberLazyListState(),
    pane: MainListPane? = null,
    viewModel: MainListViewModel = hiltViewModel(),
    sharedViewModel: SharedViewModel = hiltViewModel(LocalActivity.current as MainActivity),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val isListPane = pane != null
    val snackBarHostState = remember { SnackbarHostState() }

    val mainListUiState by viewModel.mainListUiState.collectAsState()
    val filterSelectedItems by viewModel.filterSelectedItems.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val categoryNames by viewModel.categoryNames.collectAsState()
    val lockedCategoryNames by viewModel.lockedCategoryNames.collectAsState()
    val isLockedUnlocked by viewModel.isUnlocked.collectAsState()
    val openLockedCategories = rememberOpenLockedCategories("main_open")
    val viewMode by sharedViewModel.listViewMode.collectAsState()
    val tutorialCardVisible by viewModel.tutorialCardVisible.collectAsState()
    val importEnabled by viewModel.importEnabled.collectAsState()
    val metaFillProgress by viewModel.metaFillProgress.collectAsState()
    val metaFillCellular by viewModel.metaFillCellular.collectAsState()
    val tutorialBadgePrefs by viewModel.tutorialBadgePrefs.collectAsState()
    val autoCategoryLaunchCard by viewModel.autoCategoryLaunchCard.collectAsState()
    val autoCategoryCardShownThisRun by viewModel.autoCategoryCardShownThisRun.collectAsState()
    val visibleTutorialTopics = currentVisibleTutorialTopics()
    // 메인 상단 카드 자리는 하나: 자동 분류 첫 실행 카드 > 튜토리얼 새 기능 카드 > 튜토리얼 첫 실행 카드 (36번 §4.2).
    // 자동 분류 카드가 이번 실행에 보였으면 닫아도 튜토리얼 카드는 다음 실행부터
    val tutorialCard = if (autoCategoryCardShownThisRun) TutorialCardMode.Hidden else TutorialNews.cardMode(
        introCardVisible = tutorialCardVisible,
        newTopics = TutorialNews.newTopics(visibleTutorialTopics, tutorialBadgePrefs),
        dismissedVersionCode = tutorialBadgePrefs?.newCardDismissedVersionCode ?: 0,
    )
    // 규칙을 고칠까요? 시트 (점검 스낵바 "고치기")
    val ruleSheetViewModel: RuleSheetViewModel = hiltViewModel()
    val autoCategoryNotice by viewModel.autoCategoryNotice.collectAsState()

    val mainListPagingData = when (val uiState = mainListUiState) {
        is MainListUiState.Success -> uiState.urlFlowState.collectAsLazyPagingItems()
        else -> null
    }

    val uiEffect = viewModel.mainListEffect
    var filterDialog by remember { mutableStateOf<String?>(null) }
    var linkInfoDialog by remember { mutableStateOf<SimpleMenuModel?>(null) }
    // 2분할: 링크 메뉴 드롭다운을 붙일 행 (⋮ 또는 길게 누른 행)
    var menuTargetId by remember { mutableStateOf<Int?>(null) }
    // 다시 잠기면 열려 있던 링크 정보 시트·메뉴를 닫는다 (잠금 링크의 제목·카테고리가 남지 않게. 38번 리뷰 4번)
    LaunchedEffect(isLockedUnlocked) {
        if (!isLockedUnlocked) {
            linkInfoDialog = null
            menuTargetId = null
        }
    }

    // 저장 직후 자동 분류 스낵바 (점검 > 제안, 한 번에 하나). 받아 가면 비워서 두 번 뜨지 않는다
    LaunchedEffect(autoCategoryNotice) {
        if (autoCategoryNotice == null) return@LaunchedEffect
        val notice = viewModel.takeAutoCategoryNotice() ?: return@LaunchedEffect
        coroutineScope.launch {
            snackBarHostState.currentSnackbarData?.dismiss()
            when (notice) {
                is AutoCategoryNotice.Suggest -> {
                    val s = notice.suggestion
                    val result = snackBarHostState.showSnackbar(
                        message = if (s.unanimous) resources.getString(R.string.autocat_suggest_msg, notice.siteName, s.count, s.categoryName)
                        else resources.getString(R.string.autocat_suggest_msg_most, notice.siteName, s.count, s.topCount, s.categoryName),
                        actionLabel = resources.getString(R.string.autocat_suggest_accept),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val accepted = viewModel.acceptSuggestion(s)
                        if (accepted == null) {
                            snackBarHostState.showSnackbar(resources.getString(R.string.autocat_suggest_rule_exists, notice.siteName))
                            return@launch
                        }
                        val undo = snackBarHostState.showSnackbar(
                            message = resources.getString(R.string.autocat_suggest_accepted, notice.siteName, s.categoryName),
                            actionLabel = resources.getString(R.string.autocat_undo),
                            duration = SnackbarDuration.Long,
                        )
                        if (undo == SnackbarResult.ActionPerformed) viewModel.undoAcceptedSuggestion(accepted)
                    }
                }
                is AutoCategoryNotice.Fix -> {
                    val result = snackBarHostState.showSnackbar(
                        message = resources.getString(R.string.autocat_fix_snackbar, notice.siteName, notice.overrides),
                        actionLabel = resources.getString(R.string.autocat_fix_action),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) ruleSheetViewModel.open(RuleSheetRequest.Fix(notice.ruleId))
                }
            }
        }
    }
    RuleSheetHost(ruleSheetViewModel) { event ->
        when (event) {
            is RuleSheetEvent.FixChanged -> coroutineScope.launch {
                val undo = snackBarHostState.showSnackbar(
                    message = resources.getString(R.string.autocat_fix_changed, event.categoryName),
                    actionLabel = resources.getString(R.string.autocat_undo),
                    duration = SnackbarDuration.Long,
                )
                if (undo == SnackbarResult.ActionPerformed) ruleSheetViewModel.undoCategoryChange(event.ruleId, event.previousCategoryId)
            }
            is RuleSheetEvent.Moved -> coroutineScope.launch {
                val undo = snackBarHostState.showSnackbar(
                    message = resources.getString(R.string.autocat_apply_done, event.result.count),
                    actionLabel = resources.getString(R.string.autocat_undo),
                    duration = SnackbarDuration.Long,
                )
                if (undo == SnackbarResult.ActionPerformed) ruleSheetViewModel.undoMove(event.result)
            }
            RuleSheetEvent.OpenSettings -> navController.navigateToAutoCategory(fromMain = true)
            else -> Unit
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 2분할에서는 열 때 클립보드를 읽지 않는다 (홈 패널 입력칸 포커스 때만)
                if (!isListPane) viewModel.onIntent(MainListIntent.ReadClipboard)
                // 카테고리 편집 화면에서 돌아왔을 때 칩 목록을 갱신 (ViewModel 은 백스택에 유지되어 init 이 다시 호출되지 않음)
                viewModel.onIntent(MainListIntent.RefreshOnResume)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onIntent(FetchCategoryData)
        uiEffect.collectLatest { effect ->
            when (effect) {
                is MainListUiEffect.NavigateToResult -> {
                    when (effect.route) {
                        EDIT_CATEGORY -> navController.navigateToEditCategory()
                        SAVE_LINK -> {
                            if (effect.url?.isNotEmpty() == true) {
                                navController.navigateToSaveLink(url = effect.url, replaceRightPane = isListPane)
                            } else {
                                navController.navigateToSaveLink(replaceRightPane = isListPane)
                            }
                        }
                        SEARCH -> navController.navigateToSearch()
                        APP_SETTING -> navController.navigateToAppSetting()
                    }
                }
                is MainListUiEffect.OutLinkWebSite -> context.openUrlInBrowser(effect.url)
                is MainListUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.messageRes, Toast.LENGTH_SHORT).show()
                }
                is MainListUiEffect.UrlShare -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, effect.url)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_app_chooser_title))
                    context.startActivity(shareIntent)
                }
                MainListUiEffect.ListRefresh -> mainListPagingData?.refresh()
                is MainListUiEffect.ShowSnackBarSaveUrl -> {
                    coroutineScope.launch {
                        val result = snackBarHostState
                            .showSnackbar(
                                message = context.getString(R.string.clipboard_snackbar_message, effect.url),
                                duration = SnackbarDuration.Short,
                                actionLabel = context.getString(R.string.clipboard_snackbar_action)
                            )
                        when (result) {
                            SnackbarResult.ActionPerformed -> {
                                viewModel.onIntent(GoToLinkInsertScreen(effect.url))
                            }
                            SnackbarResult.Dismissed -> {}
                        }
                    }
                }

                is MainListUiEffect.ShowLinkInfoDialog -> { linkInfoDialog = effect.model }
                MainListUiEffect.OpenTutorial -> navController.navigateToTutorial(replaceRightPane = isListPane)
                MainListUiEffect.OpenImport -> navController.navigateToImport(ImportEntry.EMPTY_STATE, replaceRightPane = isListPane)
                MainListUiEffect.OpenImportHistory -> navController.navigateToImportHistory(replaceRightPane = isListPane)
            }
        }
    }

    val scrollToTop = if (pane != null) pane.scrollToTop
        else navController.currentBackStackEntry?.arguments?.getBoolean("scrollToTop") ?: false
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            listState.animateScrollToItem(0)  // 스크롤을 최상단으로 이동
            // 초기화
            if (pane != null) navController.currentBackStackEntry?.arguments?.putBoolean("scrollToTop", false)
            else navController.previousBackStackEntry?.arguments?.putBoolean("scrollToTop", false)
        }
    }
    if (pane != null) {
        // 2분할: 오른쪽 저장 패널에서 저장하면 새 항목이 맨 위에 생긴다. 목록이 맨 위 근처였다면 새 항목이 보이도록 붙여 둔다
        // (키 기반 스크롤 유지 때문에 새 항목이 화면 위로 밀려 숨는 것 방지)
        val firstItemId = mainListPagingData?.itemSnapshotList?.items?.firstOrNull()?.id
        LaunchedEffect(firstItemId) {
            if (firstItemId != null && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
        }
    }

    // 탭/필터가 바뀌면 새 목록의 처음부터 보여준다.
    // 첫 구성(회전·접기/펼치기로 다시 그려질 때)에서는 복원된 스크롤 위치를 유지한다.
    var lastListKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedTab, filterSelectedItems) {
        val key = "$selectedTab|$filterSelectedItems"
        if (lastListKey != null && lastListKey != key &&
            (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        ) {
            listState.scrollToItem(0)
        }
        lastListKey = key
    }

    // 링크가 하나도 없는 빈 상태에서는 화면 안의 "링크 저장하기" 버튼과 겹치지 않도록 FAB 를 숨긴다
    val hasNoLinksAtAll = mainListPagingData != null &&
        mainListPagingData.loadState.refresh is LoadState.NotLoading &&
        mainListPagingData.itemCount == 0 &&
        selectedTab == MainTab.RECENT &&
        filterSelectedItems.categories.contains(FilterDefaults.CATEGORY_ALL) &&
        filterSelectedItems.siteList.isEmpty() && filterSelectedItems.tagList.isEmpty()

    val colors = AppTheme.colors
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        // 광고 슬롯: 모든 화면 공통 AdBannerBar (기존 AdMobBannerAd + surface 배경 + 상단 구분선)
        bottomBar = { AdBannerBar() },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            val onFabClick = singleClick { viewModel.onIntent(GoToLinkInsertScreen("")) }
            if (hasNoLinksAtAll || (pane != null && !pane.rightPaneShowsHome)) {
                // FAB 없음 (2분할: 오른쪽에 저장/편집 등이 열려 있는 동안 숨김)
            } else if (viewMode == ListViewMode.COMPACT) {
                FloatingActionButton(
                    onClick = onFabClick,
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.main_fab_save_link))
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = onFabClick,
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                    shape = CircleShape,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.main_fab_save_link), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                )
            }
        }
    ) { paddingValues ->
        filterDialog?.let {
            FilterScreenBottomSheet(
                dismiss = { filterDialog = null },
                initSelectedData = filterSelectedItems,
                onConfirm = { categories, sort, site, tag ->
                    viewModel.onIntent(NewFilterData(category = categories, sort = sort, site = site, tag = tag))
                    filterDialog = null
                },
                goToCategorySetting = {
                    viewModel.onIntent(GoToCategorySettingScreen)
                    filterDialog = null
                }
            )
        }
        if (!isListPane) {
            linkInfoDialog?.let {
                CommonSimpleMenuBottomSheet(
                    model = it,
                    dismiss = { linkInfoDialog = null }
                )
            }
        }
        val showLinkMenu: (UrlData) -> Unit = { urlData ->
            menuTargetId = urlData.id
            viewModel.onIntent(ShowLinkInfoDialog(urlData))
        }
        // 2분할: 행의 ⋮ + 드롭다운 메뉴 (폰은 길게 눌러 바텀시트)
        val itemMenu: (@Composable (UrlData) -> Unit)? = if (isListPane) {
            { urlData ->
                IconButton(onClick = { showLinkMenu(urlData) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.main_link_more), tint = colors.textSecondary)
                }
                SimpleMenuDropdown(
                    model = linkInfoDialog,
                    expanded = menuTargetId == urlData.id,
                    dismiss = {
                        linkInfoDialog = null
                        menuTargetId = null
                    }
                )
            }
        } else null

        MainListScreen(
            mainListPagingData = mainListPagingData,
            paddingValues = paddingValues,
            viewMode = viewMode,
            onViewModeChange = { sharedViewModel.setListViewMode(it) },
            selectedTab = selectedTab,
            onTabSelect = { viewModel.onIntent(MainListIntent.SelectTab(it)) },
            filterParams = filterSelectedItems,
            categoryNames = categoryNames,
            lockedCategoryNames = lockedCategoryNames,
            isLockedUnlocked = isLockedUnlocked,
            onLockedEntryClick = { openLockedCategories(R.string.prompt_open_locked) {} },
            onRelockClick = { viewModel.onIntent(MainListIntent.RelockCategories) },
            onSearchClick = { viewModel.onIntent(GoToSearchScreen) },
            onAppSettingClick = { viewModel.onIntent(GoToAppSetting) },
            onLinkItemClick = { url -> viewModel.onIntent(GoToOutLinkWebSite(url)) },
            onLinkItemLongClick = { urlData: UrlData -> showLinkMenu(urlData) },
            onFilterOpen = { filterDialog = "" },
            onCategoryClick = { category ->
                viewModel.onIntent(NewFilterData(
                    category = listOf(category),
                    sort = filterSelectedItems.sort,
                    site = filterSelectedItems.siteList,
                    tag = filterSelectedItems.tagList
                ))
            },
            onSortChange = { sort ->
                viewModel.onIntent(NewFilterData(
                    category = filterSelectedItems.categories,
                    sort = sort,
                    site = filterSelectedItems.siteList,
                    tag = filterSelectedItems.tagList
                ))
            },
            onSaveClick = { viewModel.onIntent(GoToLinkInsertScreen("")) },
            onTutorialClick = { viewModel.onIntent(MainListIntent.OpenTutorial) },
            autoCategoryLaunchCard = autoCategoryLaunchCard,
            onAutoCategoryCardOpen = {
                viewModel.finishAutoCategoryLaunchCard()
                navController.navigateToAutoCategory(fromMain = true)
            },
            onAutoCategoryCardDismiss = { viewModel.finishAutoCategoryLaunchCard() },
            tutorialCard = tutorialCard,
            onTutorialCardDismiss = {
                when (tutorialCard) {
                    is TutorialCardMode.News -> viewModel.onIntent(MainListIntent.DismissTutorialNewCard(tutorialCard.latestVersionCode))
                    else -> viewModel.onIntent(MainListIntent.DismissTutorialCard)
                }
            },
            listState = listState,
            selectedUrl = pane?.selectedUrl,
            itemMenu = itemMenu,
            onImportClick = if (importEnabled) ({ viewModel.onIntent(MainListIntent.OpenImport) }) else null,
            metaFillProgress = metaFillProgress,
            metaFillCellular = metaFillCellular,
            onMetaFillCardClick = { viewModel.onIntent(MainListIntent.OpenImportHistory) },
            onPrioritizeMeta = { id, explicit -> viewModel.onIntent(MainListIntent.PrioritizeMeta(id, explicit)) },
            onCancelPrioritizeMeta = { id -> viewModel.onIntent(MainListIntent.CancelPrioritizeMeta(id)) },
        )
    }
}

@Composable
fun MainListScreen(
    mainListPagingData: LazyPagingItems<UrlData>? = flowOf(PagingData.empty<UrlData>()).collectAsLazyPagingItems(),
    paddingValues: PaddingValues = PaddingValues(),
    viewMode: ListViewMode = ListViewMode.DEFAULT,
    onViewModeChange: (ListViewMode) -> Unit = {},
    selectedTab: MainTab = MainTab.RECENT,
    onTabSelect: (MainTab) -> Unit = {},
    filterParams: FilterParams = FilterParams(
        categories = listOf(FilterDefaults.CATEGORY_ALL), sort = FilterDefaults.SORT_LATEST, siteList = listOf(), tagList = listOf()
    ),
    categoryNames: List<String> = emptyList(),
    /** 잠금 카테고리 이름과 세션 상태 (37번 기획서 5.1) */
    lockedCategoryNames: List<String> = emptyList(),
    isLockedUnlocked: Boolean = false,
    onLockedEntryClick: () -> Unit = {},
    onRelockClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAppSettingClick: () -> Unit = {},
    onLinkItemClick: (String?) -> Unit = {},
    onLinkItemLongClick: (UrlData) -> Unit = {},
    onFilterOpen: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onSortChange: (String) -> Unit = {},
    onSaveClick: () -> Unit = {},
    onTutorialClick: () -> Unit = {},
    /** 업데이트 후 첫 실행 자동 분류 카드 (제안 수, null = 숨김). 튜토리얼 카드보다 먼저 */
    autoCategoryLaunchCard: Int? = null,
    onAutoCategoryCardOpen: () -> Unit = {},
    onAutoCategoryCardDismiss: () -> Unit = {},
    /** 맨 위 튜토리얼 카드: 첫 실행 안내(새로 설치, ✕ 전까지) 또는 새 기능 안내 */
    tutorialCard: TutorialCardMode = TutorialCardMode.Hidden,
    onTutorialCardDismiss: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    selectedUrl: String? = null,
    itemMenu: (@Composable (UrlData) -> Unit)? = null,
    /** 빈 화면 "카톡·북마크에서 가져오기" (null = 숨김) */
    onImportClick: (() -> Unit)? = null,
    /** 가져온 링크 미리보기 채우기 진행 (상태 카드) */
    metaFillProgress: MetaFillProgress = MetaFillProgress(0, 0),
    metaFillCellular: Boolean = false,
    onMetaFillCardClick: () -> Unit = {},
    /** 미리보기를 기다리는 카드가 1초 넘게 보였거나 실패한 링크를 열었을 때 */
    onPrioritizeMeta: (id: Int, explicit: Boolean) -> Unit = { _, _ -> },
    onCancelPrioritizeMeta: (Int) -> Unit = {},
) {
    val colors = AppTheme.colors
    val tutorialCardVisible = tutorialCard != TutorialCardMode.Hidden
    val tutorialNewCount = (tutorialCard as? TutorialCardMode.News)?.count
    val selectedCategories = filterParams.categories
    // "전체" + 사용자 카테고리. 필터 시트에서 고른 값 중 목록에 없는 항목(예: 북마크)은 앞쪽에 함께 보여준다
    val chips = buildList {
        add(FilterDefaults.CATEGORY_ALL)
        selectedCategories.filter { it != FilterDefaults.CATEGORY_ALL && it !in categoryNames && it !in lockedCategoryNames }.forEach { add(it) }
        addAll(categoryNames)
    }.distinct()
    val detailFilters = filterParams.siteList + filterParams.tagList

    val snapshot = mainListPagingData?.itemSnapshotList
    val showDateHeader = selectedTab == MainTab.RECENT && viewMode == ListViewMode.DEFAULT
    val dateLabels = if (showDateHeader) {
        snapshot?.mapIndexed { index, item ->
            val label = item?.getDate() ?: ""
            val prevLabel = if (index > 0) snapshot[index - 1]?.getDate() ?: "" else ""
            label to (label != prevLabel)
        }
    } else null

    // 전체 개수는 모든 페이지를 불러온 뒤에만 정확하므로 그때만 표시한다
    val isFullyLoaded = mainListPagingData != null &&
        mainListPagingData.loadState.refresh is LoadState.NotLoading &&
        mainListPagingData.loadState.append.endOfPaginationReached
    val itemCount = mainListPagingData?.itemCount ?: 0
    val countLabel = when {
        !isFullyLoaded || itemCount == 0 -> null
        selectedTab == MainTab.FAVORITES -> stringResource(R.string.main_favorites_count, itemCount)
        viewMode != ListViewMode.DEFAULT -> "총 ${itemCount}개"
        else -> null
    }

    val isEmpty = mainListPagingData != null &&
        mainListPagingData.loadState.refresh is LoadState.NotLoading &&
        itemCount == 0
    val hasFilter = !selectedCategories.contains(FilterDefaults.CATEGORY_ALL) || detailFilters.isNotEmpty()
    val emptyType = when {
        hasFilter -> MainEmptyType.NO_FILTER_RESULT
        selectedTab == MainTab.FAVORITES -> MainEmptyType.NO_FAVORITES
        else -> MainEmptyType.NO_LINKS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(colors.background)
    ) {
        MainTopBar(
            viewMode = viewMode,
            onViewModeChange = onViewModeChange,
            onSearchClick = onSearchClick,
            onSettingClick = onAppSettingClick,
        )
        MainTabRow(selectedTab = selectedTab, onTabSelect = onTabSelect)
        val hasLocked = lockedCategoryNames.isNotEmpty()
        CategoryChipRow(
            chips = chips,
            selected = selectedCategories,
            hasDetailFilter = detailFilters.isNotEmpty(),
            onCategoryClick = onCategoryClick,
            onFilterClick = onFilterOpen,
            lockedChips = if (isLockedUnlocked) lockedCategoryNames else emptyList(),
            showLockedEntry = hasLocked && !isLockedUnlocked,
            showRelock = hasLocked && isLockedUnlocked,
            onLockedEntryClick = onLockedEntryClick,
            onRelockClick = onRelockClick,
        )
        if (isLockedUnlocked && selectedCategories.any { it in lockedCategoryNames }) {
            LockedViewingBanner()
        }
        ActiveFilterRow(filters = detailFilters)
        if (metaFillProgress.isActive && !isEmpty) {
            MetaFillStatusCard(progress = metaFillProgress, cellularAllowed = metaFillCellular, onClick = onMetaFillCardClick)
        }
        if (!isEmpty) {
            SortRow(countLabel = countLabel, sort = filterParams.sort, onSortChange = onSortChange)
        }

        if (isEmpty) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (autoCategoryLaunchCard != null) {
                    AutoCategoryLaunchCard(count = autoCategoryLaunchCard, onOpen = onAutoCategoryCardOpen, onDismiss = onAutoCategoryCardDismiss)
                }
                if (tutorialCardVisible) {
                    TutorialEntryCard(onOpen = onTutorialClick, onDismiss = onTutorialCardDismiss, newCount = tutorialNewCount)
                }
                MainEmptyState(
                    type = emptyType,
                    onSaveClick = onSaveClick,
                    onTutorialClick = onTutorialClick,
                    onImportClick = onImportClick,
                    modifier = Modifier.padding(top = if (tutorialCardVisible) 8.dp else 48.dp)
                )
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // FAB 에 마지막 아이템이 가리지 않도록 하단 여백
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (autoCategoryLaunchCard != null) {
                item(key = "autocat_launch_card", contentType = "tutorial_entry_card") {
                    AutoCategoryLaunchCard(
                        count = autoCategoryLaunchCard,
                        onOpen = onAutoCategoryCardOpen,
                        onDismiss = onAutoCategoryCardDismiss,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (tutorialCardVisible) {
                item(key = "tutorial_entry_card", contentType = "tutorial_entry_card") {
                    TutorialEntryCard(
                        onOpen = onTutorialClick,
                        onDismiss = onTutorialCardDismiss,
                        newCount = tutorialNewCount,
                        modifier = Modifier.animateItem()
                    )
                }
            }
            if (snapshot != null) {
                items(
                    count = snapshot.size,
                    key = { index -> snapshot[index]?.id ?: index },
                    contentType = { viewMode }
                ) { index ->
                    // itemSnapshotList 접근은 페이지 로드를 트리거하지 않으므로 LazyPagingItems[index] 로 접근해야 다음 페이지가 로드됨
                    val item = mainListPagingData?.get(index) ?: return@items
                    Column(modifier = Modifier.animateItem()) {
                        val header = dateLabels?.getOrNull(index)
                        if (header != null && header.second) {
                            DateHeader(label = header.first, isFirst = index == 0)
                        } else if (index == 0 && viewMode == ListViewMode.DEFAULT) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (MetaState.isPending(item.metaState)) {
                            // 화면에 1초 넘게 보이면 그 링크를 먼저 채운다 (모바일 데이터 허용, 동시 1개).
                            // 화면에서 사라지면 기다리던 요청을 뺀다 (목록을 훑어도 요청이 쌓이지 않게)
                            LaunchedEffect(item.id) {
                                var requested = false
                                try {
                                    delay(PENDING_VISIBLE_MS)
                                    requested = true
                                    onPrioritizeMeta(item.id, false)
                                    awaitCancellation()
                                } finally {
                                    if (requested) onCancelPrioritizeMeta(item.id)
                                }
                            }
                        }
                        LinkListItem(
                            data = item,
                            viewMode = viewMode,
                            onClick = {
                                // 미리보기를 못 가져온 링크는 열 때 다시 시도
                                if (item.metaState == MetaState.FAILED) onPrioritizeMeta(item.id, true)
                                onLinkItemClick.invoke(item.url)
                            },
                            onLongClick = { onLinkItemLongClick.invoke(item) },
                            showDivider = index < snapshot.size - 1,
                            selected = selectedUrl != null && item.url == selectedUrl,
                            menu = itemMenu?.let { menu -> { menu(item) } },
                        )
                    }
                }
            }
        }
    }
}

private const val PENDING_VISIBLE_MS = 1_000L

/**
 * "미리보기 채우는 중 done / total" 상태 카드 (34번 §S12). 누르면 가져오기 기록. 다 채우면 사라진다.
 * 연결이 데이터 요금 네트워크뿐이고 모바일 데이터를 허용하지 않았으면 "Wi-Fi에 연결되면 미리보기를 채워요".
 */
@Composable
private fun MetaFillStatusCard(progress: MetaFillProgress, cellularAllowed: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val waiting = !cellularAllowed && runCatching {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        cm.activeNetwork == null || cm.isActiveNetworkMetered
    }.getOrDefault(false)
    Row(
        modifier = Modifier
            .padding(horizontal = AppDimens.Gutter, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (waiting) {
            Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        } else {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (waiting) stringResource(R.string.import_main_status_waiting)
                else stringResource(R.string.import_main_status, fmtNumber(progress.done), fmtNumber(progress.total)),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (!waiting && !cellularAllowed) {
            Text(stringResource(R.string.import_main_status_wifi_only), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
    }
}

private fun fmtNumber(n: Int): String = java.text.NumberFormat.getInstance(java.util.Locale.KOREA).format(n)

@Composable
@Preview(showBackground = true)
fun MainListScreenPreview() {
    val fakeData = listOf(
        UrlData(id = 1, url = "https://google.com", title = "Google", tagList = listOf("검색"), isBookMark = true),
        UrlData(id = 2, url = "https://youtube.com", title = "YouTube", tagList = listOf("영상")),
        UrlData(id = 3, url = "https://github.com", title = "GitHub", tagList = null),
    )
    val pagingItems = flowOf(PagingData.from(fakeData)).collectAsLazyPagingItems()
    SaveUrlTheme(darkTheme = false) {
        MainListScreen(
            mainListPagingData = pagingItems,
            categoryNames = listOf("개발", "뉴스"),
        )
    }
}

/** 업데이트 후 첫 실행 자동 분류 카드 (튜토리얼 카드 자리, 36번 §4.2) */
@Composable
private fun AutoCategoryLaunchCard(count: Int, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    TutorialEntryCard(
        onOpen = onOpen,
        onDismiss = onDismiss,
        modifier = modifier,
        content = EntryCardContent(
            title = stringResource(R.string.autocat_launch_card_title, count),
            body = stringResource(R.string.autocat_launch_card_body),
            action = stringResource(R.string.autocat_launch_card_action),
            icon = Icons.Outlined.Bolt,
            dismissDescription = stringResource(R.string.autocat_launch_card_dismiss_cd),
        ),
    )
}
