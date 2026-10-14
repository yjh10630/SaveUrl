package com.jinscompany.saveurl.ui.importer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.importer.CategoryMode
import com.jinscompany.saveurl.domain.importer.GroupRule
import com.jinscompany.saveurl.domain.importer.ImportFormat
import com.jinscompany.saveurl.domain.importer.ImportGroup
import com.jinscompany.saveurl.domain.importer.ImportPlanner
import com.jinscompany.saveurl.domain.importer.PreviewItem
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.ui.composable.AppBottomSheet
import com.jinscompany.saveurl.ui.composable.AppTextField
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.composable.CommonSimpleBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SheetHeader
import com.jinscompany.saveurl.ui.composable.TextAction
import com.jinscompany.saveurl.ui.composable.sheetFieldColor
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** 보낸 사람 칩: 8명까지 보이고 나머지는 "+n명 더" 시트 */
private const val VISIBLE_SENDERS = 8

/** 미리보기 (34번 §S5, Stitch 05·06·11) */
@Composable
internal fun ImportPreviewScreen(viewModel: ImportViewModel, step: ImportStep.Preview, onClose: () -> Unit) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val preview = step.preview
    val selection = step.selection
    val categories by viewModel.categories.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val existingNames = remember(categories) { viewModel.existingCategoryNames() }

    val selectedCount = ImportPlanner.selectedItems(preview, selection).size
    val allExist = preview.counts.found > 0 && preview.counts.existing == preview.counts.found
    val rows = remember(step) { ImportViewModel.visibleRows(step) }

    var showLeave by rememberSaveable { mutableStateOf(false) }
    var showSendersSheet by rememberSaveable { mutableStateOf(false) }
    /** 카테고리 선택 시트 대상: "single" = 모두 한 카테고리에, 그 밖 = 묶음 키(폴더 합치기) */
    var pickCategoryFor by rememberSaveable { mutableStateOf<String?>(null) }
    var editGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val close: () -> Unit = { if (allExist || preview.counts.found == 0) onClose() else showLeave = true }
    BackHandler { close() }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.import_preview_title),
                onNavigationClick = close,
                navigationIcon = Icons.Filled.Close,
                navigationContentDescription = stringResource(R.string.import_close),
            )
        },
        bottomBar = {
            ImportBottomArea {
                if (allExist) {
                    PrimaryButton(text = stringResource(R.string.import_close), onClick = onClose)
                } else {
                    Text(
                        stringResource(if (preview.hasTitles && preview.items.all { !it.title.isNullOrBlank() }) R.string.import_fill_note_image else R.string.import_fill_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                    PrimaryButton(
                        text = if (selectedCount == 0) stringResource(R.string.import_commit_empty) else stringResource(R.string.import_commit, fmtCount(selectedCount)),
                        enabled = selectedCount > 0,
                        onClick = { viewModel.commit() },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(modifier = Modifier.widthIn(max = 560.dp).fillMaxSize()) {
                item(key = "summary") {
                    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
                        VSpace(4)
                        FormatBadge(icon = badgeIcon(preview.format), text = badgeText(preview.format, preview.sourceCount))
                        VSpace(12)
                        Text(
                            stringResource(R.string.import_found, fmtCount(preview.counts.found)),
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                        )
                        duplicateLine(preview.counts.existing, preview.counts.inFileDuplicates, preview.counts.nonHttp)?.let {
                            VSpace(4)
                            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textSecondary)
                        }
                        if (preview.format == ImportFormat.KAKAO) {
                            VSpace(12)
                            PrivacyLine(stringResource(R.string.import_privacy_kakao), boxed = true)
                        }
                        if (preview.truncated) {
                            VSpace(10)
                            AccentNote(stringResource(R.string.import_limit_banner))
                        }
                        if (allExist) {
                            VSpace(12)
                            AccentNote(stringResource(R.string.import_all_exist))
                        }
                    }
                }
                if (!allExist) {
                    if (preview.senders.isNotEmpty()) {
                        item(key = "senders") {
                            SendersSection(
                                senders = preview.senders,
                                selected = selection.selectedSenders,
                                onToggle = viewModel::toggleSender,
                                onAll = viewModel::setAllSenders,
                                onMore = { showSendersSheet = true },
                            )
                        }
                    }
                    item(key = "category") {
                        CategorySection(
                            step = step,
                            existingNames = existingNames,
                            onMode = viewModel::setCategoryMode,
                            onPickSingle = { pickCategoryFor = "single" },
                            onToggleGroup = { group ->
                                val rule = selection.groupRules[group.key] ?: GroupRule(true, group.defaultName)
                                viewModel.setGroupRule(group.key, rule.copy(included = !rule.included))
                            },
                            onEditGroup = { editGroupKey = it.key },
                        )
                    }
                    item(key = "date") { DateSection(step = step, onOriginal = viewModel::setUseOriginalDate) }
                    item(key = "list_header") {
                        ListHeader(
                            selectedCount = selectedCount,
                            searchOpen = searchOpen,
                            query = step.query,
                            onSearchToggle = { searchOpen = !searchOpen; if (!searchOpen) viewModel.setQuery("") },
                            onQuery = viewModel::setQuery,
                            allSelected = rows.filter { !it.exists }.all { it.key !in selection.unchecked },
                            onAll = viewModel::setAllItems,
                        )
                    }
                    items(rows, key = { "row:" + it.key }, contentType = { "row" }) { item ->
                        PreviewRow(
                            item = item,
                            checked = ImportPlanner.isSelected(item, preview, selection),
                            showTitle = preview.hasTitles,
                            onToggle = {
                                if (item.exists) Toast.makeText(context, R.string.import_row_exists_toast, Toast.LENGTH_SHORT).show()
                                else viewModel.toggleItem(item.key)
                            },
                        )
                    }
                    item(key = "bottom_space") { VSpace(16) }
                }
            }
        }
    }

    if (showLeave) {
        CommonSimpleBottomSheet(
            title = stringResource(R.string.import_leave_title),
            description = stringResource(R.string.import_leave_desc),
            confirmTxt = stringResource(R.string.import_leave_leave),
            cancelTxt = stringResource(R.string.import_leave_stay),
            confirm = { showLeave = false; onClose() },
            cancel = { showLeave = false },
        )
    }
    if (showSendersSheet) {
        SendersSheet(
            senders = preview.senders,
            selected = selection.selectedSenders,
            onToggle = viewModel::toggleSender,
            onDismiss = { showSendersSheet = false },
        )
    }
    pickCategoryFor?.let { target ->
        val current = when {
            target == "single" -> (selection.categoryMode as? CategoryMode.Single)?.name
            else -> selection.groupRules[target]?.targetName
        }
        ImportCategorySheet(
            visible = categories.visible,
            locked = if (isUnlocked) categories.locked else emptyList(),
            current = current,
            onPick = { name ->
                if (target == "single") viewModel.setCategoryMode(CategoryMode.Single(name))
                else {
                    val rule = selection.groupRules[target] ?: GroupRule(true, name)
                    viewModel.setGroupRule(target, rule.copy(targetName = name, included = true))
                }
                pickCategoryFor = null
            },
            onDismiss = { pickCategoryFor = null },
        )
    }
    editGroupKey?.let { key ->
        val group = preview.groups.firstOrNull { it.key == key }
        if (group == null) {
            editGroupKey = null
        } else {
            FolderEditSheet(
                group = group,
                rule = selection.groupRules[key] ?: GroupRule(true, group.defaultName),
                onRename = { name -> viewModel.setGroupRule(key, GroupRule(included = true, targetName = name)) },
                onMerge = { editGroupKey = null; pickCategoryFor = key },
                onUncategorized = { viewModel.setGroupRule(key, GroupRule(included = true, targetName = null)); editGroupKey = null },
                onDismiss = { editGroupKey = null },
            )
        }
    }
}

@Composable
private fun badgeIcon(format: ImportFormat) = when (format) {
    ImportFormat.KAKAO -> Icons.Outlined.ChatBubbleOutline
    ImportFormat.BOOKMARK, ImportFormat.TAKEOUT -> Icons.Outlined.Bookmark
    ImportFormat.BACKUP -> Icons.Outlined.Restore
    ImportFormat.TEXT -> Icons.AutoMirrored.Outlined.Notes
}

@Composable
private fun badgeText(format: ImportFormat, sourceCount: Int): String = when (format) {
    ImportFormat.KAKAO -> if (sourceCount > 1) stringResource(R.string.import_badge_kakao_multi, sourceCount) else stringResource(R.string.import_badge_kakao)
    ImportFormat.BOOKMARK -> stringResource(R.string.import_badge_bookmark)
    ImportFormat.TAKEOUT -> stringResource(R.string.import_badge_takeout)
    ImportFormat.BACKUP -> stringResource(R.string.import_badge_backup)
    ImportFormat.TEXT -> stringResource(R.string.import_badge_text)
}

/** "이미 저장된 링크 a개 · 이 파일 안에서 겹친 링크 b개는 뺐어요 · 열 수 없는 링크 c개는 건너뛰었어요" (0인 부분 생략) */
@Composable
private fun duplicateLine(existing: Int, inFile: Int, nonHttp: Int): String? {
    val parts = buildList {
        if (existing > 0) add(stringResource(R.string.import_dup_existing, fmtCount(existing)))
        if (inFile > 0) add(stringResource(R.string.import_dup_infile, fmtCount(inFile)))
        if (nonHttp > 0) add(stringResource(R.string.import_skipped_scheme, fmtCount(nonHttp)))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

// ---- 보낸 사람 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SendersSection(
    senders: List<Pair<String, Int>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onAll: (Boolean) -> Unit,
    onMore: () -> Unit,
) {
    val allSelected = senders.all { it.first in selected }
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
        SectionHeader(
            label = stringResource(R.string.import_senders),
            action = stringResource(if (allSelected) R.string.import_deselect_all_senders else R.string.import_select_all_senders),
            onAction = { onAll(!allSelected) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            senders.take(VISIBLE_SENDERS).forEach { (name, count) ->
                SenderChip(name = name, count = count, selected = name in selected, onClick = { onToggle(name) })
            }
            if (senders.size > VISIBLE_SENDERS) {
                SenderChip(name = stringResource(R.string.import_senders_more, senders.size - VISIBLE_SENDERS), count = null, selected = false, onClick = onMore)
            }
        }
    }
}

@Composable
private fun SenderChip(name: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val label = if (count != null) stringResource(R.string.import_sender_chip, name, fmtCount(count)) else name
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accentTint else colors.background)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.5f) else colors.outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label + if (selected) ", 선택됨" else "" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendersSheet(senders: List<Pair<String, Int>>, selected: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    var query by remember { mutableStateOf("") }
    AppBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(title = stringResource(R.string.import_senders), action = { TextAction(stringResource(R.string.import_folder_done), onClick = onDismiss, color = colors.accent) })
        VSpace(12)
        AppTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.import_senders_search),
            leadingIcon = Icons.Outlined.Search,
            onClear = { query = "" },
            containerColor = sheetFieldColor(),
            modifier = Modifier.padding(horizontal = AppDimens.Gutter),
        )
        VSpace(8)
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)) {
            items(senders.filter { query.isBlank() || it.first.contains(query, ignoreCase = true) }, key = { it.first }) { (name, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(name) }.padding(horizontal = AppDimens.Gutter - 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = name in selected, onCheckedChange = { onToggle(name) }, colors = importCheckboxColors())
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(fmtCount(count), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
}

// ---- 카테고리 ----

@Composable
private fun CategorySection(
    step: ImportStep.Preview,
    existingNames: Set<String>,
    onMode: (CategoryMode) -> Unit,
    onPickSingle: () -> Unit,
    onToggleGroup: (ImportGroup) -> Unit,
    onEditGroup: (ImportGroup) -> Unit,
) {
    val colors = AppTheme.colors
    val preview = step.preview
    val selection = step.selection
    val mode = selection.categoryMode
    val hasGroups = preview.groups.any { it.defaultName != null }
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
        SectionHeader(stringResource(R.string.import_category))
        if (!hasGroups) {
            // 카톡 대화 1개·일반 글: 줄 하나 "카테고리 › {이름}"
            val name = (mode as? CategoryMode.Single)?.name?.let { ImportPlanner.normalizeName(it) }
            val display = name ?: CategoryModel.UNCATEGORIZED
            SurfaceGroup {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = singleClick { onPickSingle() }).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.import_category), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Spacer(modifier = Modifier.weight(1f))
                    if (name != null && name !in existingNames) {
                        NewLabel()
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(display, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = colors.textSecondary)
                }
            }
            return@Column
        }
        val groupCount = preview.groups.count { it.defaultName != null }
        val perGroupLabel = when {
            preview.sourceCount > 1 -> stringResource(R.string.import_category_per_chat, groupCount)
            preview.format == ImportFormat.BACKUP -> stringResource(R.string.import_category_keep_backup, groupCount)
            else -> stringResource(R.string.import_category_per_folder, groupCount)
        }
        SurfaceGroup {
            RadioLine(perGroupLabel, selected = mode == CategoryMode.PerGroup) { onMode(CategoryMode.PerGroup) }
            RadioLine(
                stringResource(R.string.import_category_single) + ((mode as? CategoryMode.Single)?.let { " · " + (ImportPlanner.normalizeName(it.name) ?: CategoryModel.UNCATEGORIZED) } ?: ""),
                selected = mode is CategoryMode.Single,
            ) {
                if (mode is CategoryMode.Single) onPickSingle()
                else {
                    onMode(CategoryMode.Single(if (preview.format == ImportFormat.KAKAO) ImportPlanner.KAKAO_FALLBACK else ImportPlanner.BOOKMARK_SINGLE_DEFAULT))
                    onPickSingle()
                }
            }
            RadioLine(stringResource(R.string.import_category_uncategorized), selected = mode == CategoryMode.Uncategorized) { onMode(CategoryMode.Uncategorized) }
        }
        if (preview.format == ImportFormat.BACKUP) {
            VSpace(6)
            Text(stringResource(R.string.import_backup_keep_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        if (mode == CategoryMode.PerGroup) {
            VSpace(12)
            val includedGroups = preview.groups.filter { selection.groupRules[it.key]?.included != false }
            val includedLinks = ImportPlanner.selectedItems(preview, selection).size
            SurfaceGroup {
                Text(
                    stringResource(R.string.import_folder_header, includedGroups.count { it.defaultName != null }, fmtCount(includedLinks)),
                    style = MaterialTheme.typography.labelLarge, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                // "폴더 없는 링크" 는 항상 맨 아래
                preview.groups.sortedBy { it.defaultName == null }.forEach { group ->
                    GroupRow(
                        group = group,
                        rule = selection.groupRules[group.key] ?: GroupRule(true, group.defaultName),
                        existingNames = existingNames,
                        isChat = preview.sourceCount > 1,
                        onToggle = { onToggleGroup(group) },
                        onEdit = { onEditGroup(group) },
                    )
                }
                VSpace(4)
            }
        }
    }
}

@Composable
private fun NewLabel() {
    val colors = AppTheme.colors
    Text(
        stringResource(R.string.import_category_new_label),
        style = MaterialTheme.typography.labelSmall,
        color = colors.accent,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.accentTint).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupRow(
    group: ImportGroup,
    rule: GroupRule,
    existingNames: Set<String>,
    isChat: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = AppTheme.colors
    val isNoFolder = group.defaultName == null
    val target = ImportPlanner.normalizeName(rule.targetName)
    val subtitle = when {
        !rule.included -> stringResource(R.string.import_folder_excluded)
        isNoFolder || target == null -> stringResource(R.string.import_folder_to_uncat)
        target in existingNames -> stringResource(R.string.import_folder_merge, target)
        target != group.defaultName -> stringResource(R.string.import_folder_new) + " · " + target
        group.pathLabel != null && !isChat -> stringResource(R.string.import_folder_new_path, group.pathLabel)
        else -> stringResource(R.string.import_folder_new)
    }
    val merging = rule.included && target != null && target in existingNames && !isNoFolder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (rule.included) 1f else 0.5f)
            .clickable(enabled = !isNoFolder, onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (isChat) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.Folder, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.defaultName ?: stringResource(R.string.import_folder_none),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (merging) colors.accent else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmtCount(group.itemCount), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        if (!isNoFolder) {
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.import_folder_rename), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        Checkbox(checked = rule.included, onCheckedChange = { onToggle() }, colors = importCheckboxColors())
    }
}

/** 폴더 편집 시트: 이름 바꾸기 / 기존 카테고리에 합치기 / 미분류로 보내기 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderEditSheet(
    group: ImportGroup,
    rule: GroupRule,
    onRename: (String) -> Unit,
    onMerge: () -> Unit,
    onUncategorized: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(group.key) { mutableStateOf(rule.targetName ?: "") }
    val finish: () -> Unit = {
        onRename(name)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    AppBottomSheet(onDismissRequest = finish, sheetState = sheetState) {
        SheetHeader(title = stringResource(R.string.import_folder_sheet_title, group.defaultName ?: ""))
        VSpace(16)
        Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
            Text(stringResource(R.string.import_folder_rename), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            VSpace(6)
            AppTextField(value = name, onValueChange = { name = it.take(20) }, placeholder = group.defaultName ?: "", onClear = { name = "" }, containerColor = sheetFieldColor())
        }
        VSpace(8)
        SheetLink(stringResource(R.string.import_folder_merge_into)) {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onMerge() }
        }
        SheetLink(stringResource(R.string.import_folder_send_uncat)) {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onUncategorized() }
        }
        VSpace(12)
        Box(modifier = Modifier.padding(horizontal = AppDimens.Gutter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)) {
            PrimaryButton(text = stringResource(R.string.import_folder_done), onClick = finish)
        }
    }
}

@Composable
private fun SheetLink(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = singleClick { onClick() }).padding(horizontal = AppDimens.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = colors.textSecondary)
    }
}

/**
 * 가져오기 카테고리 선택: 맨 위 "+ 새 카테고리", 미분류, 일반 카테고리.
 * 잠금 카테고리는 세션이 열려 있을 때만(자물쇠와 함께) 보인다 — 닫혀 있으면 제안하지 않는다 (37번 6.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportCategorySheet(
    visible: List<String>,
    locked: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val pick: (String) -> Unit = { name -> scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(name) } }
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dialogWidth = 400.dp) {
        SheetHeader(title = stringResource(R.string.import_category_pick_title))
        VSpace(8)
        Column(
            modifier = Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)
        ) {
            if (creating) {
                Row(modifier = Modifier.padding(horizontal = AppDimens.Gutter), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(
                        value = newName, onValueChange = { newName = it.take(20) }, placeholder = stringResource(R.string.import_category_new_hint),
                        modifier = Modifier.weight(1f), containerColor = sheetFieldColor(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextAction(stringResource(R.string.import_category_confirm), onClick = { if (newName.isNotBlank()) pick(newName.trim()) }, color = colors.accent)
                }
                VSpace(4)
            } else {
                SheetLinkAccent(stringResource(R.string.import_category_new)) { creating = true }
            }
            CategoryRadio(CategoryModel.UNCATEGORIZED, selected = ImportPlanner.normalizeName(current) == null, locked = false) { pick(CategoryModel.UNCATEGORIZED) }
            visible.forEach { name -> CategoryRadio(name, selected = current == name, locked = false) { pick(name) } }
            locked.forEach { name -> CategoryRadio(name, selected = current == name, locked = true) { pick(name) } }
        }
    }
}

@Composable
private fun SheetLinkAccent(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = colors.accent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = AppDimens.Gutter, vertical = 14.dp),
    )
}

@Composable
private fun CategoryRadio(name: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = AppDimens.Gutter - 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary))
        if (locked) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- 날짜 ----

@Composable
private fun DateSection(step: ImportStep.Preview, onOriginal: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    val preview = step.preview
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
        SectionHeader(stringResource(R.string.import_date))
        if (!preview.hasDates) {
            Text(stringResource(R.string.import_date_none), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            return@Column
        }
        val original = step.selection.useOriginalDate
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(true to R.string.import_date_original, false to R.string.import_date_today).forEach { (value, label) ->
                val selected = original == value
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) colors.onAccent else colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accent else colors.surface)
                        .clickable { onOriginal(value) }
                        .padding(vertical = 9.dp),
                )
            }
        }
        val first = preview.firstDate
        val last = preview.lastDate
        val miss = ImportPlanner.dateMissCount(preview, step.selection)
        val parts = buildList {
            if (first != null && last != null) {
                val isBookmark = preview.format == ImportFormat.BOOKMARK || preview.format == ImportFormat.TAKEOUT
                add(stringResource(if (isBookmark) R.string.import_date_range_bookmark else R.string.import_date_range, fmtMonth(first), fmtMonth(last)))
            }
            if (original && miss > 0) add(stringResource(R.string.import_date_unread, miss))
        }
        if (parts.isNotEmpty()) {
            VSpace(6)
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

// ---- 목록 ----

@Composable
private fun ListHeader(
    selectedCount: Int,
    searchOpen: Boolean,
    query: String,
    onSearchToggle: () -> Unit,
    onQuery: (String) -> Unit,
    allSelected: Boolean,
    onAll: (Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.import_selected_count, fmtCount(selectedCount)),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSearchToggle, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.import_search_hint), tint = if (searchOpen) colors.accent else colors.textSecondary)
            }
            TextAction(
                stringResource(if (allSelected) R.string.import_deselect_all else R.string.import_select_all),
                onClick = { onAll(!allSelected) },
                color = colors.accent,
            )
        }
        if (searchOpen) {
            AppTextField(value = query, onValueChange = onQuery, placeholder = stringResource(R.string.import_search_hint), leadingIcon = Icons.Outlined.Search, onClear = { onQuery("") })
            VSpace(6)
        }
    }
}

@Composable
private fun PreviewRow(item: PreviewItem, checked: Boolean, showTitle: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    val exists = item.exists
    val existsLabel = stringResource(R.string.import_row_exists)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics(mergeDescendants = true) {}
            .padding(start = AppDimens.Gutter - 12.dp, end = AppDimens.Gutter, top = 4.dp, bottom = 4.dp)
            .alpha(if (exists) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = !exists, colors = importCheckboxColors())
        Column(modifier = Modifier.weight(1f)) {
            if (showTitle && !item.title.isNullOrBlank()) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.domain, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(item.domain, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.displayUrl, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (exists) {
            Text(
                existsLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.clip(CircleShape).background(colors.surface).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        } else if (item.date != null) {
            Text(fmtDay(item.date), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_NUMBERS), color = colors.textSecondary)
        }
    }
    HorizontalDivider(color = colors.outline.copy(alpha = 0.6f), modifier = Modifier.padding(start = AppDimens.Gutter + 36.dp, end = AppDimens.Gutter))
}
