package com.jinscompany.saveurl.ui.main

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.android.gms.ads.AdView
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.AdMobBannerAd
import com.jinscompany.saveurl.ui.composable.CommonSimpleMenuBottomSheet
import com.jinscompany.saveurl.ui.composable.FilterSelectedList
import com.jinscompany.saveurl.ui.composable.LinkUrlListSection
import com.jinscompany.saveurl.ui.composable.MainHeaderSection
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.filter.FilterScreenBottomSheet
import com.jinscompany.saveurl.ui.main.MainListIntent.ClipboardUrlCheck
import com.jinscompany.saveurl.ui.main.MainListIntent.FetchCategoryData
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToAppSetting
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToCategorySettingScreen
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToLinkInsertScreen
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToOutLinkWebSite
import com.jinscompany.saveurl.ui.main.MainListIntent.GoToSearchScreen
import com.jinscompany.saveurl.ui.main.MainListIntent.NewFilterData
import com.jinscompany.saveurl.ui.main.MainListIntent.ShowLinkInfoDialog
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.APP_SETTING
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.EDIT_CATEGORY
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SEARCH
import com.jinscompany.saveurl.ui.navigation.navigateToAppSetting
import com.jinscompany.saveurl.ui.navigation.navigateToEditCategory
import com.jinscompany.saveurl.ui.navigation.navigateToSaveLink
import com.jinscompany.saveurl.ui.navigation.navigateToSearch
import com.jinscompany.saveurl.ui.navigation.navigateToStaticWeb
import com.jinscompany.saveurl.ui.theme.Brown
import com.jinscompany.saveurl.utils.extractUrlFromText
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
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackBarHostState = remember { SnackbarHostState() }

    val mainListPagingData = when (val uiState = viewModel.mainListUiState) {
        is MainListUiState.Success -> uiState.urlFlowState.collectAsLazyPagingItems()
        else -> null
    }
    
    val uiEffect = viewModel.mainListEffect
    var filterDialog by remember { mutableStateOf<String?>(null) }
    var linkInfoDialog by remember { mutableStateOf<SimpleMenuModel?>(null) }

    val adView = remember { AdView(context) }

    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                    clipboard.primaryClip?.getItemAt(0)?.text?.let {
                        val url = extractUrlFromText(it.toString())
                        if (url?.isNotEmpty() == true) {
                            viewModel.onIntent(ClipboardUrlCheck(url))
                        }
                    }
                }
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
                is MainListUiEffect.OutLinkWebSite -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                    context.startActivity(intent)
                }
                is MainListUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is MainListUiEffect.UrlShare -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, effect.url)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "링크를 공유할 앱을 선택하세요")
                    context.startActivity(shareIntent)
                }
                MainListUiEffect.ListRefresh -> mainListPagingData?.refresh()
                is MainListUiEffect.ShowSnackBarSaveUrl -> {
                    coroutineScope.launch {
                        val result = snackBarHostState
                            .showSnackbar(
                                message = "클립보드에 복사된 링크 저장\n${effect.url}",
                                duration = SnackbarDuration.Short,
                                actionLabel = "저장"
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(
                onClick = singleClick {
                    var url = ""
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                        clipboard.primaryClip?.getItemAt(0)?.text?.let {
                            url = extractUrlFromText(it.toString()) ?: ""
                        }
                    }
                    viewModel.onIntent(GoToLinkInsertScreen(url))
                },
                containerColor = Brown,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = ""
                )
            }
        }
    ) { paddingValues ->
        filterDialog?.let {
            FilterScreenBottomSheet(
                dismiss = { filterDialog = null },
                initSelectedData = viewModel.filterSelectedItems,
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
            onSearchClick = { viewModel.onIntent(GoToSearchScreen) },
            onAppSettingClick = { viewModel.onIntent(GoToAppSetting) },
            onLinkItemClick = { url -> viewModel.onIntent(GoToOutLinkWebSite(url))},
            onLinkItemLongClick = { urlData: UrlData -> viewModel.onIntent(ShowLinkInfoDialog(urlData)) },
            onFilterOpen = { filterDialog = "" },
            filterSelectedData = viewModel.filterSelectedItems.getMainSelectedList(),
            listState = listState,
            adView = adView
        )
    }
}

@Composable
fun MainListScreen(
    mainListPagingData: LazyPagingItems<UrlData>? = flowOf(PagingData.empty<UrlData>()).collectAsLazyPagingItems(),
    paddingValues: PaddingValues = PaddingValues(),
    onSearchClick: () -> Unit = {},
    onAppSettingClick: () -> Unit = {},
    onLinkItemClick: (String?) -> Unit = {},
    onLinkItemLongClick: (UrlData) -> Unit = {},
    onFilterOpen: () -> Unit = {},
    filterSelectedData: List<String> = listOf(),
    listState: LazyListState = rememberLazyListState(),
    adView: AdView
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color.DarkGray)
    ) {
        MainHeaderSection(searchIconClick = onSearchClick, appSettingClick = onAppSettingClick)
        FilterSelectedList(
            data = filterSelectedData,
            onClick = onFilterOpen
        )
        AdMobBannerAd(adView = adView)
        Spacer(modifier = Modifier.size(12.dp))
        mainListPagingData?.let {
            LinkUrlListSection(
                listState = listState,
                items = it,
                onClick = { onLinkItemClick.invoke(it.url) },
                longOnClick = {
                    onLinkItemLongClick.invoke(it)
                })
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun MainListScreenPreview() {
    val context = LocalContext.current
    val fakeData = listOf(
        UrlData(id = 1, url = "https://google.com", title = "Google", tagList = listOf("검색")),
        UrlData(id = 2, url = "https://youtube.com", title = "YouTube", tagList = listOf("영상")),
        UrlData(id = 3, url = "https://github.com", title = "GitHub", tagList = null),
    )
    val fakeDataCategory = listOf(
        CategoryModel(id = 0, name = "오구",),
        CategoryModel(id = 0, name = "구구",),
        CategoryModel(id = 0, name = "테테테 ",)
    )

    val pagingItems = flowOf(PagingData.from(fakeData)).collectAsLazyPagingItems()
    MainListScreen(
        mainListPagingData = pagingItems,
        adView = AdView(context)
    )
}