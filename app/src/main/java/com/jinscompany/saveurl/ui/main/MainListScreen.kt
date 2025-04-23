package com.jinscompany.saveurl.ui.main

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.LinkItemInfoDialog
import com.jinscompany.saveurl.ui.composable.LinkUrlListSection
import com.jinscompany.saveurl.ui.composable.MainHeaderSection
import com.jinscompany.saveurl.ui.composable.SelectorTextButtonGroup
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.EDIT_CATEGORY
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SEARCH
import com.jinscompany.saveurl.ui.navigation.navigateToEditCategory
import com.jinscompany.saveurl.ui.navigation.navigateToSaveLink
import com.jinscompany.saveurl.ui.navigation.navigateToSearch
import kotlinx.coroutines.flow.flowOf

@Composable
fun MainListScreen(
    navController: NavHostController,
    viewModel: MainListViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val mainListPagingData = when (val uiState = viewModel.mainListUiState) {
        is MainListUiState.Success -> uiState.urlFlowState.collectAsLazyPagingItems()
        else -> null
    }

    val mainCategoryData = when (val uiState = viewModel.mainCategoryUiState) {
        is MainCategoryUiState.Success -> uiState.categories
        else -> null
    }
    
    val uiEffect = viewModel.mainListEffect
    var linkItemEditDialog by remember { mutableStateOf<UrlData?>(null) }

    LaunchedEffect(Unit) {
        uiEffect.collect { effect ->
            when (effect) {
                is MainListUiEffect.NavigateToResult -> {
                    when (effect.route) {
                        EDIT_CATEGORY -> navController.navigateToEditCategory()
                        SAVE_LINK -> {
                            if (effect.url.isNullOrEmpty()) navController.navigateToSaveLink()
                            else navController.navigateToSaveLink(url = effect.url)
                        }
                        SEARCH -> navController.navigateToSearch()
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
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onIntent(MainListIntent.GoToLinkInsertScreen) },
                containerColor = Color(0xFF88540B),
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

        linkItemEditDialog?.let { urlData ->
            LinkItemInfoDialog(
                onClickShare = { viewModel.onIntent(MainListIntent.GotoOutShareUrl(urlData.url)) },
                onClickEdit = { viewModel.onIntent(MainListIntent.GoToLinkEditScreen(url = urlData.url)) },
                onClickDelete = { viewModel.onIntent(MainListIntent.DeleteLinkItem(urlData)) },
                dismiss = { linkItemEditDialog = null }
            )
        }

        MainListScreen(
            mainListPagingData = mainListPagingData,
            mainCategoryData = mainCategoryData,
            paddingValues = paddingValues,
            onSearchClick = { viewModel.onIntent(MainListIntent.GoToSearchScreen) },
            onCategoryClick = { name -> viewModel.onIntent(MainListIntent.CategoryClick(name))},
            onCategorySettingClick = { viewModel.onIntent(MainListIntent.GoToCategorySettingScreen) },
            onLinkItemClick = { url -> viewModel.onIntent(MainListIntent.GoToOutLinkWebSite(url))},
            onLinkItemLongClick = { urlData: UrlData -> linkItemEditDialog = urlData },
            listState = listState,
        )
    }
}

@Composable
fun MainListScreen(
    mainListPagingData: LazyPagingItems<UrlData>? = flowOf(PagingData.empty<UrlData>()).collectAsLazyPagingItems(),
    mainCategoryData: List<CategoryModel>? = listOf(),
    paddingValues: PaddingValues = PaddingValues(),
    onSearchClick: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onCategorySettingClick: () -> Unit = {},
    onLinkItemClick: (String?) -> Unit = {},
    onLinkItemLongClick: (UrlData) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color.DarkGray)
    ) {
        MainHeaderSection(searchIconClick = onSearchClick)
        mainCategoryData?.let { categories ->
            SelectorTextButtonGroup(
                options = listOf("북마크", "전체") + categories.map { it.name },
                clickItem = { onCategoryClick.invoke(it) },
                settingOnClick = onCategorySettingClick,
                isSettingIcon = true,
            )
            Spacer(modifier = Modifier.size(12.dp))
        }
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
    MainListScreen()
}