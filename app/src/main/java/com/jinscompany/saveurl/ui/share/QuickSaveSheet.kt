package com.jinscompany.saveurl.ui.share

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.autocat.RuleSheetEvent
import com.jinscompany.saveurl.ui.autocat.RuleSheetHost
import com.jinscompany.saveurl.ui.autocat.RuleSheetViewModel
import com.jinscompany.saveurl.ui.composable.LinkUrlCrawlerHidden
import com.jinscompany.saveurl.ui.composable.PreviewContentEditBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SecondaryButton
import com.jinscompany.saveurl.ui.composable.TextAction
import com.jinscompany.saveurl.ui.composable.category.CategorySelectorDialog
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.lock.rememberOpenLockedCategories
import com.jinscompany.saveurl.ui.revisit.ReminderPickRequest
import com.jinscompany.saveurl.ui.revisit.ReminderPicker
import com.jinscompany.saveurl.ui.save_screen.AlreadySaved
import com.jinscompany.saveurl.ui.save_screen.BookmarkRow
import com.jinscompany.saveurl.ui.save_screen.CategoryRow
import com.jinscompany.saveurl.ui.save_screen.CategorySource
import com.jinscompany.saveurl.ui.save_screen.LinkSaveIntent
import com.jinscompany.saveurl.ui.save_screen.LinkSaveUiEffect
import com.jinscompany.saveurl.ui.save_screen.LinkSaveUiState
import com.jinscompany.saveurl.ui.save_screen.LinkSaveViewModel
import com.jinscompany.saveurl.ui.save_screen.LinkUrlPreviewUiState
import com.jinscompany.saveurl.ui.save_screen.QuickSaved
import com.jinscompany.saveurl.ui.save_screen.ReminderRow
import com.jinscompany.saveurl.ui.save_screen.components.AutoCategoryAssist
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 저장 완료 막대가 떠 있다가 닫히는 시간 (44번 §3.5) */
private const val SAVED_BAR_MS = 1_500L

/**
 * 공유 빠른 저장 시트 (44번 §3·§4·§5). 보던 앱 위의 반투명 [ShareActivity] 안에 그린다.
 * 판단 로직(자동 분류, 잠금, 중복, 미리보기)은 저장 화면의 [LinkSaveViewModel] 을 그대로 쓴다 (QUICK 모드).
 *
 * @param onClose 시트가 내려간 뒤 Activity 를 닫는다 (보던 앱으로 돌아감)
 * @param onHandoff 앱(자기 작업)으로 넘긴다 ([자세히]·[수정하기]·[앱에서 보기]). 넘긴 뒤 시트는 닫힌다
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSaveSheet(url: String, onClose: () -> Unit, onHandoff: (AppHandoff) -> Unit) {
    // 링크마다 다른 ViewModel (singleTop 시트에 새 링크가 오면 새로 시작, 45번 독립 리뷰 R4)
    val viewModel = hiltViewModel<LinkSaveViewModel>(key = "quick:$url")
    val ruleSheetViewModel = hiltViewModel<RuleSheetViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val openLocked = rememberOpenLockedCategories("quick_save_open_locked")

    // 같은 ViewModel 이면 다시 시작하지 않는다 (회전)
    LaunchedEffect(Unit) { viewModel.startQuick(url) }

    // 숨은 WebView 수집 (지금 저장 화면과 같음). 회전 뒤에도 이어지게 saveable
    var crawlerUrl by rememberSaveable { mutableStateOf("") }
    var categorySelector by remember { mutableStateOf<LinkSaveUiEffect.OpenCategorySelector?>(null) }
    var previewEditor by remember { mutableStateOf<UrlData?>(null) }
    var reminderRequest by remember { mutableStateOf<ReminderPickRequest?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is LinkSaveUiEffect.StartCrawling -> crawlerUrl = effect.url
                is LinkSaveUiEffect.OpenCategorySelector -> categorySelector = effect
                is LinkSaveUiEffect.OpenRuleSheet -> ruleSheetViewModel.open(effect.request)
                is LinkSaveUiEffect.ShowToast -> Toast.makeText(context, effect.messageRes, Toast.LENGTH_SHORT).show()
                is LinkSaveUiEffect.OpenPreviewContentEdit -> previewEditor = effect.urlData
                // 미리보기 실패는 시트 안 "제목 직접 입력" 으로 보인다 (자동으로 입력 시트를 띄우지 않음, §3.3)
                is LinkSaveUiEffect.ShowCrawlFailedDialog, is LinkSaveUiEffect.ShowDuplicateDialog,
                is LinkSaveUiEffect.GotoNextScreen -> Unit
            }
        }
    }

    fun dismiss(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onClose() }
    }

    fun handoff(target: AppHandoff) {
        onHandoff(target)
    }

    // 저장 완료 막대: 1.5초 뒤 저절로 닫힘
    LaunchedEffect(uiState.quickSaved) {
        if (uiState.quickSaved != null) {
            delay(SAVED_BAR_MS)
            dismiss()
        }
    }

    ReminderPicker(
        request = reminderRequest,
        onDismiss = { reminderRequest = null },
        onPicked = { _, preset, at -> viewModel.onIntent(LinkSaveIntent.SetReminder(preset, at)) },
        onTurnOff = { viewModel.onIntent(LinkSaveIntent.ClearReminder) },
        onNotAllowed = { Toast.makeText(context, R.string.notif_off_snack, Toast.LENGTH_SHORT).show() },
    )

    if (crawlerUrl.isNotEmpty()) {
        LinkUrlCrawlerHidden(
            url = crawlerUrl,
            onSuccess = {
                viewModel.onIntent(LinkSaveIntent.WebViewCrawlerDataResult(it))
                crawlerUrl = ""
            },
            onError = {
                viewModel.onIntent(LinkSaveIntent.WebViewCrawlerDataResult())
                crawlerUrl = ""
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        sheetMaxWidth = 560.dp,
        containerColor = AppTheme.colors.background,
        // 저장 완료 막대일 때는 뒤를 어둡게 하지 않는다
        scrimColor = if (uiState.quickSaved != null) Color.Transparent else Color.Black.copy(alpha = 0.4f),
        dragHandle = if (uiState.quickSaved != null) null else ({ SheetHandle() }),
    ) {
        val saved = uiState.quickSaved
        val already = uiState.alreadySaved
        when {
            saved != null -> SavedBar(
                saved = saved,
                onTap = { dismiss() },
                onViewInApp = singleClick { handoff(AppHandoff.OpenList(saved.categoryName)) },
            )
            already != null -> AlreadySavedContent(
                state = uiState,
                already = already,
                onClose = { dismiss() },
                onEdit = singleClick {
                    val target = AppHandoff.OpenEdit(already.link.url ?: uiState.userInputUrl)
                    if (already.hiddenByLock) openLocked(R.string.prompt_edit_locked_link) { handoff(target) } else handoff(target)
                },
                onViewInApp = singleClick {
                    val target = AppHandoff.OpenList(already.link.category)
                    if (already.hiddenByLock) openLocked(R.string.prompt_open_locked_link) { handoff(target) } else handoff(target)
                },
            )
            else -> QuickContent(
                state = uiState,
                onClose = { dismiss() },
                event = viewModel::onIntent,
                onOpenReminder = {
                    val title = (uiState.linkUrlPreviewUiState as? LinkUrlPreviewUiState.LinkUrlData)?.urlData?.title
                    reminderRequest = ReminderPickRequest(
                        linkId = -1,
                        linkTitle = title.orEmpty().ifBlank { uiState.userInputUrl },
                        currentAt = uiState.reminderAt,
                    )
                },
                onMore = singleClick {
                    handoff(
                        AppHandoff.OpenSave(
                            url = uiState.userInputUrl.ifBlank { url },
                            prefill = SavePrefill(
                                categoryName = uiState.categoryName,
                                categoryByUser = uiState.categorySource == CategorySource.USER,
                                isBookMark = uiState.isBookMark,
                                remindAt = uiState.reminderAt,
                                reminderPreset = uiState.reminderPreset,
                            ),
                        )
                    )
                },
            )
        }
        Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }

    // 시트 위에 뜨는 것들 (카테고리 선택, 규칙 시트, 제목 직접 입력)
    RuleSheetHost(ruleSheetViewModel) { event ->
        if (event is RuleSheetEvent.Created) {
            viewModel.onIntent(LinkSaveIntent.RuleCreated(event.ruleId, event.siteName, event.categoryName, event.move))
        }
    }
    categorySelector?.let { selector ->
        val categories = selector.categories
        CategorySelectorDialog(
            categoryList = categories.filterNot { it.isLocked }.map { it.name },
            lockedCategoryList = categories.filter { it.isLocked }.map { it.name },
            description = selector.ruleDescription?.let { (site, category) -> stringResource(R.string.autocat_picker_desc_rule, site, category) },
            dismiss = {
                viewModel.onIntent(LinkSaveIntent.CategorySelectedItem(it))
                categorySelector = null
            },
            goToCateEdit = {},
            showEdit = false,
            selectedItem = categories.firstOrNull { it.isSelected }?.name ?: FilterDefaults.CATEGORY_ALL,
        )
    }
    previewEditor?.let { data ->
        PreviewContentEditBottomSheet(
            dismiss = { previewEditor = null },
            data = data,
            saveData = {
                viewModel.onIntent(LinkSaveIntent.PreviewContentEditData(it))
                previewEditor = null
            },
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppTheme.colors.outline)
    )
}

@Composable
private fun SheetTitle(title: String, onClose: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = AppDimens.Gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.quick_save_close), tint = colors.textSecondary)
        }
    }
}

/** 링크 저장 시트 본문 (Stitch 01·02·03·07·08) */
@Composable
private fun QuickContent(
    state: LinkSaveUiState,
    onClose: () -> Unit,
    event: (LinkSaveIntent) -> Unit,
    onOpenReminder: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(stringResource(R.string.quick_save_title), onClose)
        // 가로 폰 등 높이가 낮으면 내용만 스크롤하고 버튼은 아래에 고정
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PreviewCard(
                preview = state.linkUrlPreviewUiState,
                url = state.userInputUrl,
                onEditTitle = singleClick { event(LinkSaveIntent.OpenPreviewContentEdit) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryRow(
                categoryName = state.categoryName,
                isLocked = state.categoryName in state.lockedCategoryNames,
                onClick = singleClick { event(LinkSaveIntent.OpenCategorySelector(state.categoryName)) },
            )
            AutoCategoryAssist(
                row = state.autoCatRow,
                onOpenCategory = singleClick { event(LinkSaveIntent.OpenCategorySelector(state.categoryName)) },
                onMakeRule = singleClick { event(LinkSaveIntent.MakeRuleClick) },
                onUpdateRule = singleClick { event(LinkSaveIntent.UpdateRuleChip) },
                onDisableRule = singleClick { event(LinkSaveIntent.DisableRuleChip) },
                onOnce = { event(LinkSaveIntent.OnceChip) },
                onUndo = singleClick { event(LinkSaveIntent.UndoAutoCat) },
            )
            BookmarkRow(isBookMark = state.isBookMark, onToggle = { event(LinkSaveIntent.BookMarkToggle(it)) })
            ReminderRow(reminderAt = state.reminderAt, onClick = singleClick(onClick = onOpenReminder))
        }
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton(text = stringResource(R.string.quick_save_more), onClick = onMore, modifier = Modifier.weight(1f))
            // 미리보기를 불러오는 중에도 누를 수 있다 (URL 만으로 저장하고 나중에 채움, §3.3)
            PrimaryButton(
                text = stringResource(R.string.quick_save_save),
                onClick = { event(LinkSaveIntent.QuickSave) },
                enabled = !state.isSaving && state.userInputUrl.isNotBlank(),
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/** 미리보기 카드: 불러오는 중(회색 자리) / 불러옴 / 못 가져옴(회색, 제목 직접 입력) */
@Composable
private fun PreviewCard(preview: LinkUrlPreviewUiState, url: String, onEditTitle: (() -> Unit)?) {
    val colors = AppTheme.colors
    val data = (preview as? LinkUrlPreviewUiState.LinkUrlData)?.urlData
    val isLoading = preview !is LinkUrlPreviewUiState.LinkUrlData
    val site = data?.siteName?.takeIf { it.isNotBlank() } ?: hostOf(data?.url ?: url)
    val failed = !isLoading && data?.title.isNullOrBlank()
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(colors.outline.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                val image = data?.imgUrl
                if (!image.isNullOrBlank()) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
                } else if (!isLoading) {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.textSecondary)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(colors.outline.copy(alpha = 0.5f)))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(colors.outline.copy(alpha = 0.5f)))
                    }
                    failed -> Text(site, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else -> Text(data?.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Text(site, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            isLoading -> Text(
                stringResource(R.string.quick_save_loading),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            failed && onEditTitle != null -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.quick_save_preview_failed), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
                TextAction(stringResource(R.string.quick_save_edit_title), onClick = onEditTitle, color = colors.accent)
            }
        }
    }
}

/** 이미 저장한 링크 (Stitch 04). 닫힌 잠금 링크면 저장된 제목·카테고리·날짜를 보이지 않는다 */
@Composable
private fun AlreadySavedContent(
    state: LinkSaveUiState,
    already: AlreadySaved,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onViewInApp: () -> Unit,
) {
    val colors = AppTheme.colors
    val link = already.link
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(stringResource(R.string.quick_save_already_title), onClose)
        PreviewCard(preview = state.linkUrlPreviewUiState, url = state.userInputUrl.ifBlank { link.url.orEmpty() }, onEditTitle = null)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            val category = link.category?.takeIf { LinkSaveUiState.isRealCategory(it) }
            val text = when {
                already.hiddenByLock -> stringResource(R.string.quick_save_already_hidden)
                category != null -> stringResource(R.string.quick_save_already_category, category, link.getDate())
                else -> stringResource(R.string.quick_save_already_date, link.getDate())
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton(text = stringResource(R.string.quick_save_close), onClick = onClose, modifier = Modifier.weight(1f))
            PrimaryButton(text = stringResource(R.string.quick_save_edit), onClick = onEdit, modifier = Modifier.weight(1f))
        }
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            TextAction(stringResource(R.string.quick_save_view_in_app), onClick = onViewInApp)
        }
    }
}

/** 저장 완료 한 줄 (Stitch 06). 어디든 누르면 닫히고, [앱에서 보기] 는 앱으로 */
@Composable
private fun SavedBar(saved: QuickSaved, onTap: () -> Unit, onViewInApp: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onTap)
            .padding(start = AppDimens.Gutter, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = when {
            saved.isLocked -> stringResource(R.string.quick_save_done_locked)
            saved.categoryName != null -> stringResource(R.string.quick_save_done_category, saved.categoryName)
            else -> stringResource(R.string.quick_save_done)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextAction(stringResource(R.string.quick_save_view_in_app), onClick = onViewInApp, color = colors.accent)
    }
}

/** 사이트 이름 대신 쓸 호스트 (www. 제외) */
internal fun hostOf(url: String): String =
    runCatching { java.net.URI(url.trim()).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
