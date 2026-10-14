package com.jinscompany.saveurl.ui.importer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.importer.ImportFormat
import com.jinscompany.saveurl.domain.model.ImportBatchState
import com.jinscompany.saveurl.domain.model.ImportBatchSummary
import com.jinscompany.saveurl.ui.composable.AppSwitch
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.composable.CommonSimpleBottomSheet
import com.jinscompany.saveurl.ui.composable.TextAction
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme

/** 가져오기 기록 (34번 §S10, Stitch 15) */
@Composable
fun ImportHistoryScreen(
    onBack: () -> Unit,
    viewModel: ImportHistoryViewModel = hiltViewModel(),
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val history by viewModel.history.collectAsState()
    val fill by viewModel.fill.collectAsState()
    val paused by viewModel.paused.collectAsState()
    val cellular by viewModel.cellular.collectAsState()
    var undoTarget by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = { StatusBarSpaced { AppTopBar(title = stringResource(R.string.import_history_title), onNavigationClick = onBack) } },
        bottomBar = { ImportBottomArea {} },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(modifier = Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = AppDimens.Gutter)) {
                item {
                    VSpace(4)
                    Text(stringResource(R.string.import_history_desc), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    VSpace(14)
                }
                if (fill.isActive) {
                    item {
                        FillCard(
                            done = fill.done,
                            total = fill.total,
                            waiting = !cellular && isMeteredNow(context),
                            extra = {
                                VSpace(8)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.import_history_cellular), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                        Text(stringResource(R.string.import_history_cellular_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                    AppSwitch(checked = cellular, onCheckedChange = viewModel::setCellular)
                                }
                                TextAction(
                                    stringResource(if (paused) R.string.import_history_resume else R.string.import_history_pause),
                                    onClick = { viewModel.setPaused(!paused) },
                                    color = colors.accent,
                                )
                            },
                        )
                        VSpace(14)
                    }
                }
                val list = history.orEmpty()
                if (history != null && list.isEmpty()) {
                    item { Text(stringResource(R.string.import_history_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
                }
                items(list, key = { it.id }) { batch ->
                    HistoryRow(batch = batch, running = viewModel.runningBatchId() == batch.id, onUndo = { undoTarget = batch.id })
                }
                item { VSpace(24) }
            }
        }
    }

    undoTarget?.let { id ->
        val batch = history?.firstOrNull { it.id == id }
        if (batch == null) {
            undoTarget = null
        } else {
            // 기록 화면은 잠금 여부를 따로 보지 않으므로 카테고리 이름 대신 개수로만 알린다
            val created = batch.createdCategories.count { it != com.jinscompany.saveurl.domain.model.CategoryModel.UNCATEGORIZED }
            val categorySentence = if (created == 0) "" else stringResource(R.string.import_undo_desc_categories, created)
            CommonSimpleBottomSheet(
                title = stringResource(R.string.import_undo_title, fmtCount(batch.linkCount)),
                description = stringResource(R.string.import_undo_desc, categorySentence),
                confirmTxt = stringResource(R.string.import_undo_confirm),
                cancelTxt = stringResource(R.string.import_undo_cancel),
                confirm = { undoTarget = null; viewModel.undo(id) },
                cancel = { undoTarget = null },
            )
        }
    }
}

@Composable
private fun HistoryRow(batch: ImportBatchSummary, running: Boolean, onUndo: () -> Unit) {
    val colors = AppTheme.colors
    val icon = when (runCatching { ImportFormat.valueOf(batch.source.uppercase()) }.getOrNull()) {
        ImportFormat.KAKAO -> Icons.Outlined.ChatBubbleOutline
        ImportFormat.BOOKMARK, ImportFormat.TAKEOUT -> Icons.Outlined.Bookmark
        ImportFormat.BACKUP -> Icons.Outlined.Restore
        else -> Icons.AutoMirrored.Outlined.Notes
    }
    val undone = batch.state == ImportBatchState.UNDONE
    val partial = batch.state == ImportBatchState.IN_PROGRESS && !running
    val status = when {
        undone -> null
        partial -> stringResource(R.string.import_history_partial)
        batch.pendingCount > 0 -> stringResource(R.string.import_history_fill, fmtCount(batch.linkCount - batch.pendingCount), fmtCount(batch.linkCount))
        batch.categoryCount > 1 -> stringResource(R.string.import_history_categories, batch.categoryCount)
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.surface), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(batch.label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    stringResource(R.string.import_history_meta, fmtShortDay(batch.createdAt), fmtCount(if (undone) batch.insertedCount else batch.linkCount)),
                    style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                )
                if (status != null) {
                    Text(" · ", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = if (partial) colors.accent else colors.textSecondary, maxLines = 1)
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (undone) {
            Text(stringResource(R.string.import_history_undone), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        } else if (!running && batch.linkCount > 0) {
            OutlineActionButton(text = stringResource(R.string.import_done_undo), onClick = onUndo, height = 36.dp)
        }
    }
}
