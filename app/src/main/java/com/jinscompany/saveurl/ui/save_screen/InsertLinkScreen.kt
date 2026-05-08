package com.jinscompany.saveurl.ui.save_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdView
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.composable.AdMobBannerAd
import com.jinscompany.saveurl.ui.composable.CommonPositiveButton
import com.jinscompany.saveurl.ui.composable.LinkUrlCrawlerHidden
import com.jinscompany.saveurl.ui.composable.LinkUrlTagList
import com.jinscompany.saveurl.ui.composable.PreviewContentEditBottomSheet
import com.jinscompany.saveurl.ui.composable.category.CategorySelectorDialog
import com.jinscompany.saveurl.ui.composable.filterNotIsInstance
import com.jinscompany.saveurl.ui.save_screen.components.HeaderUserInputSection
import com.jinscompany.saveurl.ui.save_screen.components.LinkOptionsSection
import com.jinscompany.saveurl.ui.save_screen.components.PreviewSection
import com.jinscompany.saveurl.ui.save_screen.components.UserInputTagSection
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalContext

@Composable
fun InsertLinkScreen(
    state: StateFlow<LinkSaveUiState>,
    uiEffect: SharedFlow<LinkSaveUiEffect>,
    event: (LinkSaveIntent) -> Unit
) {
    val context = LocalContext.current
    val uiState by state.collectAsState()
    var startCrawlerUrl by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val listState: LazyListState = rememberLazyListState()

    var openCategorySelector by remember { mutableStateOf<List<CategoryModel>?>(null) }
    var openPreviewContentEditor by remember { mutableStateOf<UrlData?>(null) }

    val adView = remember { AdView(context) }

    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    LaunchedEffect(Unit) {
        uiEffect.filterNotIsInstance<LinkSaveUiEffect.GotoNextScreen>()
            .collectLatest {
                when (it) {
                    is LinkSaveUiEffect.StartCrawling -> startCrawlerUrl = it.url
                    is LinkSaveUiEffect.OpenCategorySelector -> openCategorySelector = it.categories
                    is LinkSaveUiEffect.OpenPreviewContentEdit -> openPreviewContentEditor = it.urlData
                }
            }
    }

    Scaffold { paddingValue ->

        if (openPreviewContentEditor != null) {
            PreviewContentEditBottomSheet(
                dismiss = { openPreviewContentEditor = null },
                data = openPreviewContentEditor ?: UrlData(),
                saveData = {
                    event.invoke(LinkSaveIntent.PreviewContentEditData(it))
                    openPreviewContentEditor = null
                }
            )
        }
        if (openCategorySelector != null) {
            CategorySelectorDialog(
                categoryList = openCategorySelector?.map { it.name } ?: listOf(),
                dismiss = {
                    event.invoke(LinkSaveIntent.CategorySelectedItem(it))
                    openCategorySelector = null
                },
                goToCateEdit = {
                    event.invoke(LinkSaveIntent.CategoryEdit)
                    openPreviewContentEditor = null
                },
                selectedItem = openCategorySelector?.firstOrNull { it.isSelected }?.name ?: FilterDefaults.CATEGORY_ALL
            )
        }

        if (startCrawlerUrl.isNotEmpty()) {
            LinkUrlCrawlerHidden(
                url = startCrawlerUrl,
                onSuccess = {
                    event.invoke(LinkSaveIntent.WebViewCrawlerDataResult(it))
                    startCrawlerUrl = ""
                },
                onError = {
                    event.invoke(LinkSaveIntent.WebViewCrawlerDataResult())
                    startCrawlerUrl = ""
                    Firebase.crashlytics.log("Crawling Error Url > $startCrawlerUrl")
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.DarkGray)
                .padding(paddingValue)
                .imePadding(),
            state = listState
        ) {
            item {
                IconButton(onClick = { event.invoke(LinkSaveIntent.ScreenBackPress) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.LightGray,
                    )
                }
            }
            item {
                HeaderUserInputSection(
                    url = uiState.userInputUrl,
                    userInputStartCrawler = { event.invoke(LinkSaveIntent.StartCrawling(it)) },
                    focusClear = { focusManager.clearFocus() },
                )
            }
            item { AdMobBannerAd(adView = adView) }
            item { Spacer(modifier = Modifier.height(6.dp)) }
            item {
                PreviewSection(
                    state = uiState.linkUrlPreviewUiState,
                    event = { event.invoke(LinkSaveIntent.UserForcedEndCrawling) },
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                LinkUrlTagList(
                    modifier = Modifier.padding(24.dp),
                    tagList = uiState.tagList,
                    editMode = true,
                    removeClick = { event.invoke(LinkSaveIntent.UserRemoveTag(it)) })
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                LinkOptionsSection(
                    state = uiState.linkUrlPreviewUiState,
                    isBookMark = uiState.isBookMark,
                    categoryName = uiState.categoryName,
                    bookMarkClick = { event.invoke(LinkSaveIntent.BookMarkToggle(it)) },
                    categoryClick = { event.invoke(LinkSaveIntent.OpenCategorySelector(it)) },
                    editorClick = { event.invoke(LinkSaveIntent.OpenPreviewContentEdit) }
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                UserInputTagSection(
                    focusClear = { focusManager.clearFocus() },
                    onInsertTagTxt = { event.invoke(LinkSaveIntent.UserInputTag(it)) })
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
            item {
                CommonPositiveButton(
                    onClick = { event.invoke(LinkSaveIntent.SaveLink) },
                    enabled = uiState.linkUrlPreviewUiState is LinkUrlPreviewUiState.LinkUrlData,
                    text = if (uiState.isEditScreen) "수정" else "저장",
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0XFF444444)
fun InsertLinkScreenPreview() {
    val dummyEffect = object : SharedFlow<LinkSaveUiEffect> {
        override val replayCache: List<LinkSaveUiEffect> = emptyList()
        override suspend fun collect(collector: FlowCollector<LinkSaveUiEffect>): Nothing {
            throw UnsupportedOperationException("Not supported in preview")
        }
    }
    val dummyUiState = object : StateFlow<LinkSaveUiState> {
        override val replayCache: List<LinkSaveUiState>
            get() = emptyList()
        override val value: LinkSaveUiState
            get() = LinkSaveUiState(linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading)

        override suspend fun collect(collector: FlowCollector<LinkSaveUiState>): Nothing {
            throw UnsupportedOperationException("Not supported in preview")
        }
    }
    InsertLinkScreen(
        state = dummyUiState,
        uiEffect = dummyEffect,
        event = {}
    )
}
