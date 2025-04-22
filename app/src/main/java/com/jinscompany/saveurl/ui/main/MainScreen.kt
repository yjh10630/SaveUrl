package com.jinscompany.saveurl.ui.main

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.navigation.compose.rememberNavController
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.LinkItemInfoDialog
import com.jinscompany.saveurl.ui.composable.LinkUrlListSection
import com.jinscompany.saveurl.ui.composable.MainHeaderSection
import com.jinscompany.saveurl.ui.composable.SelectorTextButtonGroup
import com.jinscompany.saveurl.ui.navigation.navigateToEditCategory
import com.jinscompany.saveurl.ui.navigation.navigateToSaveLink
import com.jinscompany.saveurl.ui.navigation.navigateToSearch
import com.jinscompany.saveurl.utils.extractUrlFromText
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    navController: NavHostController,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val viewModel = hiltViewModel<MainViewModel>()
    val context = LocalContext.current

    var linkItemEditDialog by remember { mutableStateOf<UrlData?>(null) }

    val listState = rememberLazyListState()

    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pagedItems = viewModel.urlDataResultFlow?.collectAsLazyPagingItems()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                    clipboard.primaryClip?.getItemAt(0)?.text?.let {
                        val url = extractUrlFromText(it.toString())
                        if (url?.isNotEmpty() == true) {
                            viewModel.checkUrl(url)
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
        viewModel.getLinkUrlList()
        viewModel.getCategoryList()
        viewModel.showSnackBar.collect { url ->
            coroutineScope.launch {
                val result = snackBarHostState
                    .showSnackbar(
                        message = "클립보드에 복사된 링크 저장\n${url}",
                        duration = SnackbarDuration.Short,
                        actionLabel = "저장"
                    )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        navController.navigateToSaveLink(url = url)
                    }
                    SnackbarResult.Dismissed -> {}
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
                onClick = {
                    navController.navigateToSaveLink()
                },
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
                onClickShare = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, urlData.url)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "링크를 공유할 앱을 선택하세요")
                    context.startActivity(shareIntent)
                },
                onClickEdit = { navController.navigateToSaveLink(url = urlData.url) },
                onClickDelete = { viewModel.deleteLinkUrl(urlData)},
                dismiss = { linkItemEditDialog = null }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.DarkGray)
        ) {
            MainHeaderSection(searchIconClick = { navController.navigateToSearch() })
            SelectorTextButtonGroup(
                options = listOf("북마크", "전체") + viewModel.categoryItemsState.value.map { it.name },
                clickItem = {
                    viewModel.categoryItemClickEvent(it)
                },
                settingOnClick = {
                    navController.navigateToEditCategory()
                },
                isSettingIcon = true,
                isEditMode = viewModel.categoryEditModeState.value
            )
            Spacer(modifier = Modifier.size(12.dp))
            LinkUrlListSection(
                listState = listState,
                items = pagedItems ?: flowOf(PagingData.empty<UrlData>()).collectAsLazyPagingItems(),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.url))
                    context.startActivity(intent)
                }, longOnClick = {
                    linkItemEditDialog = it
                })
        }
    }
}

@Composable
@Preview
private fun MainScreenPreview() {
    MainScreen(rememberNavController())
}