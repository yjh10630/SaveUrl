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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.jinscompany.saveurl.ui.composable.AdMobBannerAd
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
import com.jinscompany.saveurl.ui.navigation.navigateToStaticWeb
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.openUrlInBrowser
import com.jinscompany.saveurl.utils.tutorialUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun MainListScreen(
    navController: NavHostController,
    viewModel: MainListViewModel = hiltViewModel(),
    sharedViewModel: SharedViewModel = hiltViewModel(LocalActivity.current as MainActivity),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackBarHostState = remember { SnackbarHostState() }

    val mainListUiState by viewModel.mainListUiState.collectAsState()
    val filterSelectedItems by viewModel.filterSelectedItems.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val categoryNames by viewModel.categoryNames.collectAsState()
    val viewMode by sharedViewModel.listViewMode.collectAsState()

    val mainListPagingData = when (val uiState = mainListUiState) {
        is MainListUiState.Success -> uiState.urlFlowState.collectAsLazyPagingItems()
        else -> null
    }

    val uiEffect = viewModel.mainListEffect
    var filterDialog by remember { mutableStateOf<String?>(null) }
    var linkInfoDialog by remember { mutableStateOf<SimpleMenuModel?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onIntent(MainListIntent.ReadClipboard)
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
                                navController.navigateToSaveLink(url = effect.url)
                            } else {
                                navController.navigateToSaveLink()
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
                is MainListUiEffect.StaticWebOpen -> {
                    val encodedUrl = URLEncoder.encode(effect.url, "UTF-8")
                    navController.navigateToStaticWeb(encodedUrl)
                }
            }
        }
    }

    val scrollToTop = navController.currentBackStackEntry?.arguments?.getBoolean("scrollToTop") ?: false
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            listState.animateScrollToItem(0)  // 스크롤을 최상단으로 이동
            // 초기화
            navController.previousBackStackEntry?.arguments?.putBoolean("scrollToTop", false)
        }
    }

    // 탭/필터가 바뀌면 새 목록의 처음부터 보여준다
    LaunchedEffect(selectedTab, filterSelectedItems) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }

    val colors = AppTheme.colors
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        bottomBar = {
            // 광고 슬롯: 기존 AdMobBannerAd(내부 navigationBarsPadding / 생명주기 처리 포함)를 그대로 사용
            Column(modifier = Modifier.background(colors.surface)) {
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                AdMobBannerAd()
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            val onFabClick = singleClick { viewModel.onIntent(GoToLinkInsertScreen("")) }
            if (viewMode == ListViewMode.COMPACT) {
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
        linkInfoDialog?.let {
            CommonSimpleMenuBottomSheet(
                model = it,
                dismiss = { linkInfoDialog = null }
            )
        }

        MainListScreen(
            mainListPagingData = mainListPagingData,
            paddingValues = paddingValues,
            viewMode = viewMode,
            onViewModeChange = { sharedViewModel.setListViewMode(it) },
            selectedTab = selectedTab,
            onTabSelect = { viewModel.onIntent(MainListIntent.SelectTab(it)) },
            filterParams = filterSelectedItems,
            categoryNames = categoryNames,
            onSearchClick = { viewModel.onIntent(GoToSearchScreen) },
            onAppSettingClick = { viewModel.onIntent(GoToAppSetting) },
            onLinkItemClick = { url -> viewModel.onIntent(GoToOutLinkWebSite(url)) },
            onLinkItemLongClick = { urlData: UrlData -> viewModel.onIntent(ShowLinkInfoDialog(urlData)) },
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
            onTutorialClick = { viewModel.onIntent(GoToOutLinkWebSite(tutorialUrl)) },
            listState = listState,
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
    onSearchClick: () -> Unit = {},
    onAppSettingClick: () -> Unit = {},
    onLinkItemClick: (String?) -> Unit = {},
    onLinkItemLongClick: (UrlData) -> Unit = {},
    onFilterOpen: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onSortChange: (String) -> Unit = {},
    onSaveClick: () -> Unit = {},
    onTutorialClick: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = AppTheme.colors
    val selectedCategories = filterParams.categories
    // "전체" + 사용자 카테고리. 필터 시트에서 고른 값 중 목록에 없는 항목(예: 북마크)은 앞쪽에 함께 보여준다
    val chips = buildList {
        add(FilterDefaults.CATEGORY_ALL)
        selectedCategories.filter { it != FilterDefaults.CATEGORY_ALL && it !in categoryNames }.forEach { add(it) }
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
        CategoryChipRow(
            chips = chips,
            selected = selectedCategories,
            hasDetailFilter = detailFilters.isNotEmpty(),
            onCategoryClick = onCategoryClick,
            onFilterClick = onFilterOpen,
        )
        ActiveFilterRow(filters = detailFilters)
        if (!isEmpty) {
            SortRow(countLabel = countLabel, sort = filterParams.sort, onSortChange = onSortChange)
        }

        if (isEmpty) {
            Box(modifier = Modifier.fillMaxSize()) {
                MainEmptyState(
                    type = emptyType,
                    onSaveClick = onSaveClick,
                    onTutorialClick = onTutorialClick,
                    modifier = Modifier.padding(top = 48.dp)
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
                        LinkListItem(
                            data = item,
                            viewMode = viewMode,
                            onClick = { onLinkItemClick.invoke(item.url) },
                            onLongClick = { onLinkItemLongClick.invoke(item) },
                            showDivider = index < snapshot.size - 1,
                        )
                    }
                }
            }
        }
    }
}

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
