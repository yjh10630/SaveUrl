package com.jinscompany.saveurl.ui.trash

import com.jinscompany.saveurl.ui.theme.AppDivider
import com.jinscompany.saveurl.ui.theme.AppTextSecondary
import com.jinscompany.saveurl.ui.theme.AppTextPrimary
import com.jinscompany.saveurl.ui.theme.AppBackground
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.paging.LoadState
import com.jinscompany.saveurl.SaveUrlApplication
import com.jinscompany.saveurl.domain.model.TrashRetention
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.AppSwitch
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.main.components.DefaultLinkItem
import com.jinscompany.saveurl.ui.main.components.displayDomain
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.jinscompany.saveurl.ui.composable.CommonSimpleBottomSheet
import com.jinscompany.saveurl.ui.composable.CommonSimpleMenuBottomSheet
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import com.jinscompany.saveurl.data.mapper.toUrlData
import com.jinscompany.saveurl.ui.composable.filterNotIsInstance
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun TrashScreen(
    state: StateFlow<TrashUiState>,
    uiEffect: SharedFlow<TrashUiEffect>,
    event: (TrashIntent) -> Unit
) {
    val context = LocalContext.current
    val uiState by state.collectAsState()
    val items = uiState.trashList.collectAsLazyPagingItems()
    var showAlert by remember { mutableStateOf<TrashViewModel.AlertDataModel?>(null) }
    var showMenuAlert by remember { mutableStateOf<SimpleMenuModel?>(null) }
    val snackBarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        uiEffect.filterNotIsInstance<TrashUiEffect.GotoNextScreen>().collectLatest { effect ->
            when (effect) {
                is TrashUiEffect.AskFromUserTrashItemDelete -> {}
                is TrashUiEffect.AskFromUserTrashItemRestore -> {}
                TrashUiEffect.AskTrashItemAllDelete -> {}
                is TrashUiEffect.AskFromUserTrashStateChange -> {
                    showAlert = effect.alertDataModel
                }
                TrashUiEffect.ForceCommonBottomSheetHide -> {
                    showAlert = null
                }
                is TrashUiEffect.ShowMoreBottomSheet -> {
                    showMenuAlert = effect.model
                }
                is TrashUiEffect.ShowSnackBar -> {
                    val message = effect.txtRes?.let { context.getString(it, *effect.formatArgs.toTypedArray()) } ?: effect.txt
                    val result = snackBarHostState
                        .showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                            actionLabel = context.getString(com.jinscompany.saveurl.R.string.btn_confirm)
                        )
                    when (result) {
                        SnackbarResult.ActionPerformed -> {}
                        SnackbarResult.Dismissed -> {}
                    }
                }
            }
        }
    }

    val colors = AppTheme.colors
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        bottomBar = { AdBannerBar() },
    ) { paddingValues ->
        showMenuAlert?.let {
            CommonSimpleMenuBottomSheet(
                model = it,
                dismiss = { showMenuAlert = null }
            )
        }
        showAlert?.let { alert ->
            CommonSimpleBottomSheet(
                title = alert.titleRes?.let { stringResource(it) } ?: alert.title,
                description = alert.descriptionRes?.let { stringResource(it) } ?: alert.description,
                confirmTxt = alert.confirmTxtRes?.let { stringResource(it) } ?: alert.confirmTxt,
                cancelTxt = alert.cancelTxtRes?.let { stringResource(it) } ?: alert.cancelTxt,
                // 확인/취소 후 시트를 composition 에서 내린다. (기존에는 취소 시 숨겨진 시트가 남아 뒤로가기를 가로채던 문제가 있었음)
                confirm = {
                    showAlert = null
                    alert.confirm()
                },
                cancel = {
                    showAlert = null
                    alert.cancel()
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues),
        ) {
            AppTopBar(
                title = "휴지통",
                onNavigationClick = { event.invoke(TrashIntent.GoToPopBackStack) },
                showDivider = true,
                actions = {
                    IconButton(onClick = { event.invoke(TrashIntent.MoreClick) }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "더보기", tint = colors.textPrimary)
                    }
                }
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    RetentionBanner()
                    TrashToggleRow(
                        isActive = uiState.isActivate,
                        onToggle = { event.invoke(TrashIntent.AskTrashState(it)) }
                    )
                    HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = AppDimens.Gutter))
                    if (items.itemCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = AppDimens.Gutter, end = AppDimens.Gutter, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("삭제된 링크", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.weight(1f))
                            Text("길게 눌러 복원·삭제", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        }
                    }
                }
                if (items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
                    item { TrashEmptyState() }
                }
                // itemSnapshotList 접근은 페이지 로드를 트리거하지 않으므로 items[index] 로 접근해야 다음 페이지가 로드됨
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.id }
                ) { index ->
                    val item = items[index] ?: return@items
                    val data = item.toUrlData()
                    Column(modifier = Modifier.animateItem()) {
                        DefaultLinkItem(
                            data = data,
                            onClick = { Toast.makeText(context, R.string.trash_item_open_restore_required, Toast.LENGTH_SHORT).show() },
                            onLongClick = { event.invoke(TrashIntent.AskFromUserLinkLongClickShowAlert(item)) },
                            metaOverride = { TrashMeta(domain = data.displayDomain(), deleteDate = item.deleteDate) },
                            trailing = {},
                        )
                        if (index < items.itemCount - 1) {
                            HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = AppDimens.Gutter))
                        }
                    }
                }
                if (items.itemCount > 0) {
                    item {
                        Text(
                            "휴지통에서 삭제한 링크는 복구할 수 없어요.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

/** "ⓘ 삭제한 링크는 7일 동안 보관된 뒤 자동으로 지워져요." */
@Composable
private fun RetentionBanner() {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Gutter, vertical = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.accentTint)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                append("삭제한 링크는 ")
                withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) { append("${TrashRetention.RELEASE_DAYS}일") }
                append(" 동안 보관된 뒤 자동으로 지워져요.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
    }
}

/** 휴지통 사용 스위치 (기존 헤더 스위치를 라벨 있는 행으로 옮김, 동작은 동일) */
@Composable
private fun TrashToggleRow(isActive: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isActive) }
            .padding(start = AppDimens.Gutter, end = AppDimens.Gutter, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("휴지통 사용", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(
                if (isActive) "삭제한 링크를 휴지통에 보관해요" else "삭제하면 바로 지워져요",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }
        AppSwitch(checked = isActive, onCheckedChange = onToggle)
    }
}

/** "naver.com · 6일 후 삭제" (24시간 이내면 빨간 점 + danger 색) */
@Composable
private fun TrashMeta(domain: String, deleteDate: Long) {
    val colors = AppTheme.colors
    val remaining = TrashRetention.remaining(deleteDate, System.currentTimeMillis(), SaveUrlApplication.DEBUG)
    val label = when (remaining) {
        is TrashRetention.Remaining.Days -> "${remaining.days}일 후 삭제"
        TrashRetention.Remaining.WithinADay -> "24시간 이내 삭제"
        TrashRetention.Remaining.Expired -> "곧 삭제"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (domain.isNotEmpty()) {
            Text(
                "$domain · ",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (remaining.isUrgent) {
            Box(Modifier.padding(end = 4.dp).size(6.dp).clip(CircleShape).background(colors.danger))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (remaining.isUrgent) FontWeight.SemiBold else FontWeight.Medium,
            color = if (remaining.isUrgent) colors.danger else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TrashEmptyState() {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(colors.accentTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("휴지통이 비어 있어요", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "삭제한 링크는 여기에서 복원할 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun TrashScreenPreview() {
    val dummyEffect = object : SharedFlow<TrashUiEffect> {
        override val replayCache: List<TrashUiEffect> = emptyList()
        override suspend fun collect(collector: FlowCollector<TrashUiEffect>): Nothing {
            throw UnsupportedOperationException("Not supported in preview")
        }
    }
    val dummyUiState = object: StateFlow<TrashUiState> {
        override val replayCache: List<TrashUiState>
            get() = emptyList()
        override val value: TrashUiState
            get() = TrashUiState()
        override suspend fun collect(collector: FlowCollector<TrashUiState>): Nothing {
            throw UnsupportedOperationException("Not supported in preview")
        }
    }

    TrashScreen(uiEffect = dummyEffect, state = dummyUiState) { intent -> }
}