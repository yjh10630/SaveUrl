package com.jinscompany.saveurl.ui.save_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.AppSwitch
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.composable.LinkUrlCrawlerHidden
import com.jinscompany.saveurl.ui.composable.PreviewContentEditBottomSheet
import com.jinscompany.saveurl.ui.composable.category.CategorySelectorDialog
import com.jinscompany.saveurl.ui.composable.filterNotIsInstance
import com.jinscompany.saveurl.ui.save_screen.components.HeaderUserInputSection
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
    // 화면 회전/다른 화면 이동 후 복귀 시에도 진행 중이던 WebView 크롤링이 이어지도록 saveable 로 유지
    var startCrawlerUrl by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var openCategorySelector by remember { mutableStateOf<List<CategoryModel>?>(null) }
    var openPreviewContentEditor by remember { mutableStateOf<UrlData?>(null) }
    var showCrawlFailedDialog by remember { mutableStateOf<UrlData?>(null) }
    var showDuplicateDialog by remember { mutableStateOf<UrlData?>(null) }

    LaunchedEffect(Unit) {
        uiEffect.filterNotIsInstance<LinkSaveUiEffect.GotoNextScreen>()
            .collectLatest {
                when (it) {
                    is LinkSaveUiEffect.StartCrawling -> startCrawlerUrl = it.url
                    is LinkSaveUiEffect.OpenCategorySelector -> openCategorySelector = it.categories
                    is LinkSaveUiEffect.OpenPreviewContentEdit -> openPreviewContentEditor = it.urlData
                    is LinkSaveUiEffect.ShowCrawlFailedDialog -> {
                        showCrawlFailedDialog = UrlData(url = it.url, description = it.url)
                    }
                    is LinkSaveUiEffect.ShowDuplicateDialog -> showDuplicateDialog = it.existing
                }
            }
    }

    if (showDuplicateDialog != null) {
        val existing = showDuplicateDialog!!
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = null },
            title = { Text("이미 저장된 URL") },
            text = {
                Text(
                    text = if (!existing.title.isNullOrEmpty()) {
                        "\"${existing.title}\"\n\n이미 저장된 링크예요. 그래도 저장할까요?"
                    } else {
                        "이미 저장된 링크예요. 그래도 저장할까요?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateDialog = null
                    event.invoke(LinkSaveIntent.ForceSaveLink)
                }) {
                    Text("그래도 저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = null }) {
                    Text("취소")
                }
            }
        )
    }

    val colors = AppTheme.colors
    val isSavable = uiState.linkUrlPreviewUiState is LinkUrlPreviewUiState.LinkUrlData
    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            // 저장 버튼은 스크롤 영역 밖에 고정하고, 광고와 최소 16dp + 버튼 영역 패딩만큼 떨어뜨려 오클릭을 줄인다.
            Column(modifier = Modifier.background(colors.background)) {
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                PrimaryButton(
                    text = if (uiState.isEditScreen) "수정" else "저장",
                    onClick = { event.invoke(LinkSaveIntent.SaveLink) },
                    enabled = isSavable,
                    modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 12.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                AdBannerBar()
            }
        }
    ) { paddingValue ->

        if (showCrawlFailedDialog != null) {
            PreviewContentEditBottomSheet(
                dismiss = { showCrawlFailedDialog = null },
                data = showCrawlFailedDialog ?: UrlData(),
                saveData = {
                    event.invoke(LinkSaveIntent.PreviewContentEditData(it))
                    showCrawlFailedDialog = null
                },
                title = "자동 분석 실패",
                subtitle = "링크 정보를 직접 입력해 주세요.",
            )
        }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValue)
                .consumeWindowInsets(paddingValue)
                .imePadding()
        ) {
            AppTopBar(
                title = if (uiState.isEditScreen) "링크 수정" else "링크 저장",
                onNavigationClick = { event.invoke(LinkSaveIntent.ScreenBackPress) },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                HeaderUserInputSection(
                    url = uiState.userInputUrl,
                    userInputStartCrawler = { event.invoke(LinkSaveIntent.StartCrawling(it)) },
                    focusClear = { focusManager.clearFocus() },
                )
                Spacer(modifier = Modifier.height(20.dp))
                PreviewSection(
                    state = uiState.linkUrlPreviewUiState,
                    event = { event.invoke(LinkSaveIntent.UserForcedEndCrawling) },
                    editorClick = singleClick { event.invoke(LinkSaveIntent.OpenPreviewContentEdit) },
                )
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = AppDimens.Gutter))
                CategoryRow(
                    categoryName = uiState.categoryName,
                    onClick = singleClick { event.invoke(LinkSaveIntent.OpenCategorySelector(uiState.categoryName)) }
                )
                HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = AppDimens.Gutter))
                Spacer(modifier = Modifier.height(20.dp))
                UserInputTagSection(
                    tagList = uiState.tagList,
                    focusClear = { focusManager.clearFocus() },
                    onInsertTagTxt = { event.invoke(LinkSaveIntent.UserInputTag(it)) },
                    onRemoveTag = { event.invoke(LinkSaveIntent.UserRemoveTag(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                BookmarkRow(
                    isBookMark = uiState.isBookMark,
                    onToggle = { event.invoke(LinkSaveIntent.BookMarkToggle(it)) }
                )
            }
        }
    }
}

/** "카테고리 ........ 레시피 ›" (선택 안 함 = 전체) */
@Composable
private fun CategoryRow(categoryName: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val isNone = categoryName.isEmpty() || categoryName == FilterDefaults.CATEGORY_ALL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimens.Gutter, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("카테고리", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(
            text = if (isNone) "선택 안 함" else categoryName,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isNone) FontWeight.Normal else FontWeight.SemiBold),
            color = if (isNone) colors.textSecondary else colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
    }
}

/** ☆ 즐겨찾기에 추가 ............ [스위치] */
@Composable
private fun BookmarkRow(isBookMark: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isBookMark) }
            .padding(horizontal = AppDimens.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isBookMark) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text("즐겨찾기에 추가", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        AppSwitch(checked = isBookMark, onCheckedChange = { onToggle(it) })
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
