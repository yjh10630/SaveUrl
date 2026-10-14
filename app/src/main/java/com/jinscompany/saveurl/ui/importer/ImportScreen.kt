package com.jinscompany.saveurl.ui.importer

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.importer.ImportError
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.composable.CommonSimpleBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.rememberStableActivityResultLauncher
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.utils.openUrlInBrowser

/**
 * 링크 가져오기 (34번 기획서). 한 라우트 안에서 [ImportStep] 에 따라 화면을 바꾼다.
 * 폰은 전체 화면(하단 광고), 2분할은 오른쪽 패널 (앱바가 제목 + ✕ 로 바뀌고 광고는 2분할 레이아웃이 한 번만 그림).
 *
 * @param onClose 가져오기를 닫는다 (진입한 곳으로)
 * @param onViewInList 완료 → 메인 목록 (MainListRequests 에 카테고리를 넣어 둔 뒤)
 */
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onClose: () -> Unit,
    onOpenHistory: () -> Unit,
    onViewInList: () -> Unit,
) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsState()
    val hasHistory by viewModel.hasHistory.collectAsState()

    // 파일 선택 화면에 다녀오는 동안 한 화면 ↔ 2분할이 바뀌어도 결과를 받도록 고정 키 (37번 리뷰 재검토와 같은 이유)
    val picker = rememberStableActivityResultLauncher(
        key = "import_pick_file",
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onFilePicked(it) } }
    val pickFile: () -> Unit = { runCatching { picker.launch(arrayOf("*/*")) } }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ImportEffect.Toast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                ImportEffect.Close -> onClose()
                ImportEffect.ViewInList -> onViewInList()
            }
        }
    }

    when (val s = step) {
        ImportStep.Home -> {
            LaunchedEffect(Unit) { viewModel.onHomeShown() }
            ImportHomeScreen(onBack = onClose, onPickFile = pickFile, hasHistory = hasHistory, onOpenHistory = onOpenHistory)
        }
        is ImportStep.Reading -> {
            BackHandler { viewModel.cancelReading() }
            if (s.visible) ReadingScreen(s, onCancel = { viewModel.cancelReading() })
            else Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background))
        }
        is ImportStep.PickSources -> {
            BackHandler { if (viewModel.entry == ImportEntry.SHARE) onClose() else viewModel.backToHome() }
            PickSourcesScreen(
                step = s,
                onClose = { if (viewModel.entry == ImportEntry.SHARE) onClose() else viewModel.backToHome() },
                onToggle = viewModel::toggleSource,
                onAll = viewModel::setAllSources,
                onConfirm = viewModel::confirmSources,
            )
        }
        is ImportStep.Preview -> ImportPreviewScreen(viewModel = viewModel, step = s, onClose = onClose)
        is ImportStep.Progress -> {
            var showCancel by rememberSaveable { mutableStateOf(false) }
            BackHandler { showCancel = true }
            if (s.visible) ProgressScreen(s, onCancel = { showCancel = true })
            else Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background))
            if (showCancel) {
                CommonSimpleBottomSheet(
                    title = stringResource(R.string.import_cancel_title),
                    description = stringResource(R.string.import_cancel_desc, fmtCount(s.done)),
                    confirmTxt = stringResource(R.string.import_cancel_confirm),
                    cancelTxt = stringResource(R.string.import_cancel_keep),
                    confirm = { showCancel = false; viewModel.cancelCommit() },
                    cancel = { showCancel = false },
                )
            }
        }
        is ImportStep.Done -> {
            BackHandler { onClose() }
            val fill by viewModel.doneFill.collectAsState()
            DoneScreen(
                summary = s.summary,
                fill = fill,
                onClose = onClose,
                onView = viewModel::viewInList,
                onUndo = viewModel::undoDone,
            )
        }
        is ImportStep.Error -> {
            BackHandler { if (s.backToPreview) viewModel.backToPreview() else onClose() }
            ErrorScreen(
                step = s,
                onClose = { if (s.backToPreview) viewModel.backToPreview() else onClose() },
                onPickAgain = pickFile,
                onHowTo = viewModel::backToHome,
                onBackToPreview = viewModel::backToPreview,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// S2 가져오기 홈 · 출처별 방법
// -------------------------------------------------------------------------------------------------

private enum class ImportSourceRow { KAKAO, BROWSER, BACKUP, TEXT }

@Composable
private fun ImportHomeScreen(onBack: () -> Unit, onPickFile: () -> Unit, hasHistory: Boolean, onOpenHistory: () -> Unit) {
    val colors = AppTheme.colors
    // 처음 열면 카카오톡 대화가 펼쳐져 있다. 한 번에 하나만
    var expanded by rememberSaveable { mutableStateOf<ImportSourceRow?>(ImportSourceRow.KAKAO) }
    Scaffold(
        containerColor = colors.background,
        topBar = { StatusBarSpaced { AppTopBar(title = stringResource(R.string.import_home_title), onNavigationClick = onBack) } },
        bottomBar = {
            ImportBottomArea {
                PrimaryButton(text = stringResource(R.string.import_pick_file), onClick = onPickFile)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.Gutter)
        ) {
            ImportContentWidth {
                VSpace(4)
                Text(stringResource(R.string.import_home_intro), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = colors.textSecondary)
                VSpace(10)
                PrivacyLine(stringResource(R.string.import_home_privacy))
                SectionHeader(stringResource(R.string.import_home_section))
                SurfaceGroup {
                    ImportSourceRow.entries.forEachIndexed { index, row ->
                        SourceRow(row = row, expanded = expanded == row, onToggle = { expanded = if (expanded == row) null else row })
                        if (index < ImportSourceRow.entries.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(color = colors.outline, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
                VSpace(12)
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.import_unsupported_browsers), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                if (hasHistory) {
                    VSpace(12)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = singleClick { onOpenHistory() })
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.import_history_entry), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = colors.textSecondary)
                    }
                }
                VSpace(24)
            }
        }
    }
}

@Composable
private fun SourceRow(row: ImportSourceRow, expanded: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    val (icon, title, subtitle) = when (row) {
        ImportSourceRow.KAKAO -> Triple(Icons.Outlined.ChatBubbleOutline, R.string.import_src_kakao_title, R.string.import_src_kakao_subtitle)
        ImportSourceRow.BROWSER -> Triple(Icons.Outlined.Bookmark, R.string.import_src_browser_title, R.string.import_src_browser_subtitle)
        ImportSourceRow.BACKUP -> Triple(Icons.Outlined.Restore, R.string.import_src_backup_title, R.string.import_src_backup_subtitle)
        ImportSourceRow.TEXT -> Triple(Icons.AutoMirrored.Outlined.Notes, R.string.import_src_text_title, R.string.import_src_text_subtitle)
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.background), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textSecondary)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.textSecondary)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
                when (row) {
                    ImportSourceRow.KAKAO -> KakaoHowTo()
                    ImportSourceRow.BROWSER -> BrowserHowTo()
                    ImportSourceRow.BACKUP -> HowToCard(steps = listOf(R.string.import_backup_step1, R.string.import_backup_step2), tips = listOf(R.string.import_backup_tip))
                    ImportSourceRow.TEXT -> HowToCard(steps = listOf(R.string.import_text_step1, R.string.import_text_step2), tips = listOf(R.string.import_text_tip))
                }
            }
        }
    }
}

@Composable
private fun KakaoHowTo() {
    val context = LocalContext.current
    val launchKakao = remember { context.packageManager.getLaunchIntentForPackage(KAKAO_PACKAGE) }
    HowToCard(
        steps = listOf(R.string.import_kakao_step1, R.string.import_kakao_step2, R.string.import_kakao_step3, R.string.import_kakao_step4),
        tips = listOf(R.string.import_kakao_tip_ios, R.string.import_kakao_tip_pc),
        action = launchKakao?.let { intent ->
            stringResource(R.string.import_kakao_open) to { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
        },
    )
}

@Composable
private fun BrowserHowTo() {
    val colors = AppTheme.colors
    val context = LocalContext.current
    var phone by rememberSaveable { mutableStateOf(false) }
    Column {
        // [PC가 있어요 | 폰만 있어요]
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(false to R.string.import_browser_tab_pc, true to R.string.import_browser_tab_phone).forEach { (isPhone, label) ->
                val selected = phone == isPhone
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) colors.onAccent else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accent else colors.background)
                        .clickable { phone = isPhone }
                        .padding(vertical = 9.dp),
                )
            }
        }
        VSpace(8)
        if (!phone) {
            HowToCard(
                steps = listOf(R.string.import_browser_pc_step1, R.string.import_browser_pc_step2, R.string.import_browser_pc_step3, R.string.import_browser_pc_step4),
                tips = listOf(R.string.import_browser_pc_tip1, R.string.import_browser_pc_tip2),
            )
        } else {
            HowToCard(
                steps = listOf(R.string.import_browser_phone_step1, R.string.import_browser_phone_step2, R.string.import_browser_phone_step3, R.string.import_browser_phone_step4),
                tips = listOf(R.string.import_browser_phone_tip1, R.string.import_browser_phone_tip2),
                action = stringResource(R.string.import_browser_phone_open) to { context.openUrlInBrowser(TAKEOUT_URL) },
            )
        }
    }
}

/** 흰 안쪽 카드: 번호 원 + 단계, 전구 팁, (선택) 끝의 텍스트 버튼 */
@Composable
private fun HowToCard(steps: List<Int>, tips: List<Int>, action: Pair<String, () -> Unit>? = null) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background).padding(14.dp)
    ) {
        steps.forEachIndexed { index, res ->
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = colors.onAccent, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            }
        }
        tips.forEach { res ->
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(res), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        if (action != null) {
            Text(
                action.first,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = singleClick { action.second() })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// S4 읽는 중
// -------------------------------------------------------------------------------------------------

@Composable
private fun ReadingScreen(step: ImportStep.Reading, onCancel: () -> Unit) {
    val colors = AppTheme.colors
    Scaffold(
        containerColor = colors.background,
        topBar = {
            StatusBarSpaced { AppTopBar(title = stringResource(R.string.import_home_title), onNavigationClick = onCancel, navigationIcon = Icons.Filled.Close, navigationContentDescription = stringResource(R.string.import_close)) }
        },
        bottomBar = { ImportBottomArea {} },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = AppDimens.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FileChip(step.file, Icons.Outlined.InsertDriveFile)
            VSpace(24)
            Text(stringResource(R.string.import_reading_title), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            VSpace(8)
            Text(
                stringResource(R.string.import_reading_count, fmtCount(step.found)),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR_NUMBERS),
                color = colors.accent,
            )
            VSpace(20)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(0.7f).clip(CircleShape),
                color = colors.accent,
                trackColor = colors.surface,
            )
            VSpace(20)
            PrivacyLine(stringResource(R.string.import_reading_privacy), modifier = Modifier.fillMaxWidth(0.85f))
            VSpace(16)
            Text(
                stringResource(R.string.import_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCancel).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// S3 대화 고르기 (zip 안 대화 여러 개 / 공유로 파일 여러 개)
// -------------------------------------------------------------------------------------------------

@Composable
private fun PickSourcesScreen(
    step: ImportStep.PickSources,
    onClose: () -> Unit,
    onToggle: (Int) -> Unit,
    onAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = AppTheme.colors
    val sources = step.outcome.sources
    val allKakao = sources.all { it.format == com.jinscompany.saveurl.domain.importer.ImportFormat.KAKAO }
    val allSelected = step.selected.size == sources.size
    Scaffold(
        containerColor = colors.background,
        topBar = {
            StatusBarSpaced { AppTopBar(title = stringResource(R.string.import_home_title), onNavigationClick = onClose, navigationIcon = Icons.Filled.Close, navigationContentDescription = stringResource(R.string.import_close)) }
        },
        bottomBar = {
            ImportBottomArea {
                PrimaryButton(
                    text = if (step.selected.isEmpty()) stringResource(R.string.import_zip_action_empty) else stringResource(R.string.import_zip_action, step.selected.size),
                    onClick = onConfirm,
                    enabled = step.selected.isNotEmpty(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = AppDimens.Gutter)
        ) {
            ImportContentWidth {
                VSpace(4)
                FileChip(step.file, Icons.Outlined.FolderZip, extra = null)
                VSpace(16)
                Text(
                    stringResource(if (allKakao) R.string.import_zip_title else R.string.import_multi_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                VSpace(6)
                Text(
                    stringResource(if (allKakao) R.string.import_zip_desc else R.string.import_multi_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                SectionHeader(
                    label = "",
                    action = stringResource(if (allSelected) R.string.import_deselect_all else R.string.import_select_all),
                    onAction = { onAll(!allSelected) },
                )
                SurfaceGroup {
                    sources.forEachIndexed { index, source ->
                        val checked = index in step.selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(index) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onToggle(index) }, colors = importCheckboxColors())
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    source.roomName ?: source.fileName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                    color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                val period = if (source.firstDate != null && source.lastDate != null) "${fmtMonth(source.firstDate)} ~ ${fmtMonth(source.lastDate)}" else null
                                Text(
                                    listOfNotNull(source.fileName, period).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                stringResource(R.string.import_zip_row_links, fmtCount(source.candidates.size)),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (checked) colors.accent else colors.textSecondary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                VSpace(12)
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.import_zip_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                VSpace(24)
            }
        }
    }
}

@Composable
internal fun importCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = AppTheme.colors.accent,
    uncheckedColor = AppTheme.colors.textSecondary,
    checkmarkColor = AppTheme.colors.onAccent,
    disabledCheckedColor = AppTheme.colors.outline,
    disabledUncheckedColor = AppTheme.colors.outline,
)

// -------------------------------------------------------------------------------------------------
// S7 가져오는 중
// -------------------------------------------------------------------------------------------------

@Composable
private fun ProgressScreen(step: ImportStep.Progress, onCancel: () -> Unit) {
    val colors = AppTheme.colors
    val fraction = if (step.total == 0) 0f else step.done.toFloat() / step.total
    Scaffold(
        containerColor = colors.background,
        topBar = {
            StatusBarSpaced { Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = AppDimens.Gutter), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.import_home_title), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            } }
        },
        bottomBar = { ImportBottomArea {} },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = AppDimens.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircleIcon(Icons.Outlined.MoveToInbox)
            VSpace(20)
            Text(stringResource(R.string.import_progress_title), style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            VSpace(6)
            Text(
                stringResource(R.string.import_progress_count, fmtCount(step.done), fmtCount(step.total)),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_NUMBERS),
                color = colors.textPrimary,
            )
            VSpace(16)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(CircleShape)
                    .semantics { contentDescription = "${(fraction * 10).toInt() * 10}%" },
                color = colors.accent,
                trackColor = colors.surface,
            )
            VSpace(16)
            Text(
                when (val c = step.categoryLabel) {
                    is ProgressCategory.Single -> stringResource(R.string.import_progress_category, c.name)
                    is ProgressCategory.Many -> stringResource(R.string.import_progress_categories, c.count)
                    ProgressCategory.Uncategorized -> stringResource(R.string.import_progress_uncategorized)
                    ProgressCategory.Hidden -> stringResource(R.string.import_progress_hidden)
                },
                style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, textAlign = TextAlign.Center,
            )
            VSpace(4)
            Text(stringResource(R.string.import_progress_background), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            VSpace(28)
            OutlineActionButton(text = stringResource(R.string.import_cancel), onClick = onCancel, modifier = Modifier.width(160.dp))
            VSpace(8)
            Text(stringResource(R.string.import_progress_cancel_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// S8 완료 · S9 되돌리기 확인
// -------------------------------------------------------------------------------------------------

@Composable
private fun DoneScreen(
    summary: ImportDoneSummary,
    fill: Pair<Int, Int>?,
    onClose: () -> Unit,
    onView: () -> Unit,
    onUndo: () -> Unit,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    var showUndo by rememberSaveable { mutableStateOf(false) }
    // 가로로 눕힌 폰처럼 높이가 낮으면 버튼 세 줄이 내용을 가리므로 스크롤 안 맨 아래로 옮긴다
    val shortScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP
    val actions: @Composable () -> Unit = {
        PrimaryButton(text = stringResource(R.string.import_done_view), onClick = onView)
        VSpace(10)
        OutlineActionButton(
            text = stringResource(R.string.import_done_undo),
            icon = Icons.AutoMirrored.Outlined.Undo,
            onClick = { showUndo = true },
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(8)
        Text(
            stringResource(R.string.import_done_undo_note),
            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Scaffold(
        containerColor = colors.background,
        topBar = {
            StatusBarSpaced { AppTopBar(title = stringResource(R.string.import_home_title), onNavigationClick = onClose, navigationIcon = Icons.Filled.Close, navigationContentDescription = stringResource(R.string.import_close)) }
        },
        bottomBar = { ImportBottomArea { if (!shortScreen) actions() } },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = AppDimens.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ImportContentWidth {
                VSpace(16)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircleIcon(Icons.Outlined.Check) }
                VSpace(16)
                Text(
                    stringResource(R.string.import_done_title, fmtCount(summary.inserted)),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                VSpace(4)
                Text(
                    stringResource(R.string.import_done_subtitle, summary.label, fmtLongDay(summary.createdAt)),
                    style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                VSpace(20)
                SurfaceGroup {
                    SummaryRow(
                        stringResource(R.string.import_done_row_category),
                        when (val c = summary.category) {
                            is DoneCategory.Single -> if (c.isNew) stringResource(R.string.import_done_category_new, c.name) else c.name
                            is DoneCategory.Many -> if (c.newCount > 0) stringResource(R.string.import_done_categories, c.count, c.newCount) else stringResource(R.string.import_done_categories_plain, c.count)
                            DoneCategory.Uncategorized -> stringResource(R.string.import_done_uncategorized)
                        },
                    )
                    SummaryRow(
                        stringResource(R.string.import_done_row_date),
                        if (summary.useOriginalDate && summary.firstDate != null && summary.lastDate != null) {
                            stringResource(R.string.import_done_date_original, "${fmtMonth(summary.firstDate)} ~ ${fmtMonth(summary.lastDate)}")
                        } else stringResource(R.string.import_done_date_today),
                    )
                    if (summary.skipped > 0) {
                        SummaryRow(stringResource(R.string.import_done_row_skipped), stringResource(R.string.import_done_skipped_value, fmtCount(summary.skipped)))
                    }
                    if (summary.dateMiss > 0) {
                        SummaryRow(stringResource(R.string.import_done_row_unread_date), stringResource(R.string.import_done_unread_value, summary.dateMiss))
                    }
                }
                val pending = fill?.first ?: 0
                if (summary.needsFill && summary.inserted > 0 && (fill == null || pending > 0)) {
                    VSpace(14)
                    FillCard(
                        done = fill?.let { it.second - it.first } ?: 0,
                        total = fill?.second ?: summary.inserted,
                        waiting = isMeteredNow(context),
                    )
                }
                if (shortScreen) {
                    VSpace(20)
                    actions()
                }
                VSpace(24)
            }
        }
    }
    if (showUndo) {
        val categorySentence = when {
            summary.createdCategories.isEmpty() -> ""
            summary.createdCategories.size == 1 && !summary.hideCreatedNames -> stringResource(R.string.import_undo_desc_category, summary.createdCategories.first())
            else -> stringResource(R.string.import_undo_desc_categories, summary.createdCategories.size)
        }
        CommonSimpleBottomSheet(
            title = stringResource(R.string.import_undo_title, fmtCount(summary.inserted)),
            description = stringResource(R.string.import_undo_desc, categorySentence),
            confirmTxt = stringResource(R.string.import_undo_confirm),
            cancelTxt = stringResource(R.string.import_undo_cancel),
            confirm = { showUndo = false; onUndo() },
            cancel = { showUndo = false },
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary, textAlign = TextAlign.End)
    }
}

/** 미리보기(제목·썸네일) 채우기 카드 (완료·기록 화면) */
@Composable
internal fun FillCard(done: Int, total: Int, waiting: Boolean, extra: (@Composable () -> Unit)? = null) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.outline, shape)
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.import_fill_card_title), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        VSpace(4)
        Text(
            stringResource(if (waiting) R.string.import_fill_card_waiting else R.string.import_fill_card_desc),
            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
        )
        VSpace(12)
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().clip(CircleShape),
            color = colors.accent,
            trackColor = colors.surface,
        )
        VSpace(6)
        Text(
            stringResource(R.string.import_progress_count, fmtCount(done), fmtCount(total)),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_NUMBERS),
            color = colors.textSecondary,
        )
        extra?.invoke()
    }
}

// -------------------------------------------------------------------------------------------------
// S11 오류
// -------------------------------------------------------------------------------------------------

@Composable
private fun ErrorScreen(
    step: ImportStep.Error,
    onClose: () -> Unit,
    onPickAgain: () -> Unit,
    onHowTo: () -> Unit,
    onBackToPreview: () -> Unit,
) {
    val colors = AppTheme.colors
    data class ErrorUi(val icon: ImageVector, val title: Int, val desc: Int, val showFormats: Boolean)
    val ui = when (step.error) {
        ImportError.NO_LINKS -> ErrorUi(Icons.Outlined.LinkOff, R.string.import_err_no_links_title, R.string.import_err_no_links_desc, true)
        ImportError.UNSUPPORTED -> ErrorUi(Icons.Outlined.Description, R.string.import_err_unsupported_title, R.string.import_err_unsupported_desc, true)
        ImportError.EMPTY -> ErrorUi(Icons.Outlined.InsertDriveFile, R.string.import_err_empty_title, R.string.import_err_empty_desc, false)
        ImportError.TOO_BIG -> ErrorUi(Icons.Outlined.WarningAmber, R.string.import_err_too_big_title, R.string.import_err_too_big_desc, false)
        ImportError.ZIP_EMPTY -> ErrorUi(Icons.Outlined.FolderZip, R.string.import_err_zip_empty_title, R.string.import_err_zip_empty_desc, true)
        ImportError.TAKEOUT_NO_BOOKMARKS -> ErrorUi(Icons.Outlined.Bookmark, R.string.import_err_takeout_title, R.string.import_err_takeout_desc, false)
        ImportError.IO -> ErrorUi(Icons.Outlined.ErrorOutline, R.string.import_err_io_title, R.string.import_err_io_desc, false)
        ImportError.STORAGE_FULL -> ErrorUi(Icons.Outlined.WarningAmber, R.string.import_err_storage_title, R.string.import_err_storage_desc, false)
    }
    Scaffold(
        containerColor = colors.background,
        topBar = {
            StatusBarSpaced { AppTopBar(title = stringResource(R.string.import_home_title), onNavigationClick = onClose, navigationIcon = Icons.Filled.Close, navigationContentDescription = stringResource(R.string.import_close)) }
        },
        bottomBar = {
            ImportBottomArea {
                if (step.backToPreview) PrimaryButton(text = stringResource(R.string.import_err_storage_back), onClick = onBackToPreview)
                else PrimaryButton(text = stringResource(R.string.import_err_pick_again), onClick = onPickAgain)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = AppDimens.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ImportContentWidth {
                VSpace(32)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircleIcon(ui.icon, tinted = false) }
                VSpace(20)
                Text(stringResource(ui.title), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(8)
                Text(stringResource(ui.desc), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (step.file != null) {
                    VSpace(16)
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FileChip(step.file, Icons.Outlined.InsertDriveFile) }
                }
                if (ui.showFormats) {
                    VSpace(20)
                    SurfaceGroup(modifier = Modifier) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.import_err_formats_title), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            VSpace(8)
                            listOf(
                                R.string.import_err_format_kakao, R.string.import_err_format_bookmark, R.string.import_err_format_takeout,
                                R.string.import_err_format_backup, R.string.import_err_format_text,
                            ).forEach {
                                Text("· " + stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
                if (!step.backToPreview) {
                    VSpace(12)
                    Text(
                        stringResource(R.string.import_err_howto),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = singleClick { onHowTo() })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                VSpace(24)
            }
        }
    }
}

/** 이보다 낮으면 완료 화면 버튼을 스크롤 안으로 */
private const val SHORT_SCREEN_DP = 480

private const val KAKAO_PACKAGE = "com.kakao.talk"
private const val TAKEOUT_URL = "https://takeout.google.com"
