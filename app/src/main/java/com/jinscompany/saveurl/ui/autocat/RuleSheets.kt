package com.jinscompany.saveurl.ui.autocat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.autocat.RuleKey
import com.jinscompany.saveurl.domain.autocat.RuleMatchType
import com.jinscompany.saveurl.domain.autocat.TenantKind
import com.jinscompany.saveurl.domain.model.AutoCatLinkRow
import com.jinscompany.saveurl.ui.composable.AppBottomSheet
import com.jinscompany.saveurl.ui.composable.AppSwitch
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SheetHeader
import com.jinscompany.saveurl.ui.composable.sheetFieldColor
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme

/**
 * 규칙 시트들을 그린다. 시트가 끝나면 [onEvent] 로 알린다 (스낵바·확인 문구는 여는 화면이 띄움).
 * 폰은 바텀시트, 2분할은 가운데 대화상자(폭 400dp) — [AppBottomSheet] 가 고른다.
 */
@Composable
fun RuleSheetHost(viewModel: RuleSheetViewModel, onEvent: (RuleSheetEvent) -> Unit) {
    val sheet by viewModel.sheet.collectAsState()
    LaunchedEffect(viewModel) { viewModel.events.collect { onEvent(it) } }
    when (val current = sheet) {
        null -> Unit
        is RuleSheet.Editor -> RuleEditorSheet(current, viewModel)
        is RuleSheet.Apply -> ApplyExistingSheet(current, viewModel)
        is RuleSheet.Fix -> FixRuleSheet(current, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dialogWidth = 400.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            content = content,
        )
    }
}

// ---------------- 규칙 만들기·수정 (§3) ----------------

@Composable
private fun RuleEditorSheet(editor: RuleSheet.Editor, viewModel: RuleSheetViewModel) {
    val colors = AppTheme.colors
    if (editor.confirmDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.setConfirmDelete(false) },
            title = { Text(stringResource(R.string.autocat_delete_rule_title, editor.siteName)) },
            text = { Text(stringResource(R.string.autocat_delete_rule_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete() }) { Text(stringResource(R.string.btn_delete), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setConfirmDelete(false) }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
    SheetFrame(onDismiss = { if (editor.picking) viewModel.setPicking(false) else viewModel.dismiss() }) {
        if (editor.picking) {
            CategoryPicker(editor, viewModel)
            return@SheetFrame
        }
        val isCreate = editor.mode == RuleEditorMode.CREATE
        if (isCreate) {
            SheetHeader(title = stringResource(R.string.autocat_sheet_title_new), description = stringResource(R.string.autocat_sheet_desc))
        } else {
            SheetHeader(
                title = stringResource(R.string.autocat_sheet_title_edit),
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.autocat_enabled_label), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        AppSwitch(checked = editor.enabled, onCheckedChange = { viewModel.setEnabled(it) })
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        if (isCreate && editor.scopeKeys.size > 1) {
            SectionCaption(stringResource(R.string.autocat_scope_label))
            Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter - 8.dp)) {
                editor.scopeKeys.forEach { key ->
                    ScopeRadioRow(label = scopeLabel(key), selected = key == editor.selectedKey, onClick = { viewModel.selectScope(key) })
                }
            }
        } else {
            SiteRow(
                key = editor.selectedKey,
                trailing = if (!isCreate) {
                    if (editor.overriddenCount > 0) stringResource(R.string.autocat_rule_stats, editor.appliedCount, editor.overriddenCount)
                    else if (editor.appliedCount > 0) stringResource(R.string.autocat_rule_stats_applied, editor.appliedCount)
                    else stringResource(R.string.autocat_rule_stats_none)
                } else null,
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionCaption(stringResource(R.string.autocat_category_label))
        CategoryField(name = editor.category.name, onClick = { viewModel.setPicking(true) })
        Spacer(Modifier.height(16.dp))

        if (isCreate) {
            CreatePreviewCard(editor, onToggleMove = { viewModel.setMoveUnfiled(!editor.moveUnfiled) })
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.autocat_create),
                onClick = { viewModel.create() },
                modifier = Modifier.padding(horizontal = AppDimens.Gutter),
            )
            TextButton(onClick = { viewModel.dismiss() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.autocat_not_now), color = colors.textSecondary)
            }
        } else {
            EditDistribution(editor)
            Spacer(Modifier.height(16.dp))
            val unfiled = editor.distribution?.unfiled?.size ?: 0
            OutlineButton(
                text = if (unfiled > 0) stringResource(R.string.autocat_apply_existing_btn, unfiled)
                    else stringResource(R.string.autocat_apply_existing_none),
                enabled = unfiled > 0,
                onClick = { viewModel.openApply() },
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.autocat_save),
                onClick = { viewModel.save() },
                modifier = Modifier.padding(horizontal = AppDimens.Gutter),
            )
            TextButton(onClick = { viewModel.setConfirmDelete(true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.autocat_delete_rule), color = colors.danger)
            }
        }
    }
}

@Composable
private fun scopeLabel(key: RuleKey): String {
    val owner = key.owner
    if (key.type == RuleMatchType.HOST || owner == null) {
        return stringResource(R.string.autocat_scope_host, AutoCategoryKeys.siteName(key.host))
    }
    return when (AutoCategoryKeys.tenantOf(key.host)) {
        TenantKind.BLOG -> stringResource(R.string.autocat_scope_owner_blog, owner)
        TenantKind.CAFE -> stringResource(R.string.autocat_scope_owner_cafe, owner)
        TenantKind.STORE -> stringResource(R.string.autocat_scope_owner_store, owner)
        TenantKind.AUTHOR -> stringResource(R.string.autocat_scope_owner_author, "@$owner")
        TenantKind.WRITER -> stringResource(R.string.autocat_scope_owner_writer, "@$owner")
        TenantKind.ORG, null -> stringResource(R.string.autocat_scope_owner_org, owner)
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 6.dp),
    )
}

@Composable
private fun ScopeRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accentTint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CheckCircle(selected)
    }
}

@Composable
private fun CheckCircle(selected: Boolean) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else Color.Transparent)
            .border(1.5.dp, if (selected) colors.accent else colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
    }
}

/** 지구본 + 사이트 이름 + 회색 키 (+ 오른쪽 통계) */
@Composable
private fun SiteRow(key: RuleKey, trailing: String?) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(sheetFieldColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(AutoCategoryKeys.displayName(key), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (key.type == RuleMatchType.HOST) stringResource(R.string.autocat_host_sub, key.pattern) else key.pattern,
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** 56dp 카테고리 줄: 값 + "바꾸기 ▾" */
@Composable
private fun CategoryField(name: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Gutter)
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(sheetFieldColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.accent,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.autocat_category_change), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
    }
}

/** 카테고리 고르기 ("선택 안 함"·"미분류"·잠금 카테고리 없음) */
@Composable
private fun ColumnScope.CategoryPicker(editor: RuleSheet.Editor, viewModel: RuleSheetViewModel) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.setPicking(false) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.autocat_back_cd), tint = colors.textPrimary)
        }
        Text(stringResource(R.string.autocat_category_label), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
    }
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter - 8.dp)) {
        editor.categories.forEachIndexed { index, option ->
            ScopeRadioRow(label = option.name, selected = option.id == editor.category.id, onClick = { viewModel.selectCategory(option) })
            if (index < editor.categories.lastIndex) {
                HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

/** 만들기: 내 링크로 미리 보기 카드 (§3.2-4) */
@Composable
private fun CreatePreviewCard(editor: RuleSheet.Editor, onToggleMove: () -> Unit) {
    val colors = AppTheme.colors
    SectionCaption(stringResource(R.string.autocat_preview_label))
    val distribution = editor.distribution
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Gutter)
            .clip(RoundedCornerShape(16.dp))
            .background(sheetFieldColor())
            .padding(16.dp),
    ) {
        when {
            distribution == null -> SkeletonBar()
            distribution.total == 0 -> Text(stringResource(R.string.autocat_preview_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            else -> {
                val chosen = editor.category.name
                val chosenCount = distribution.countOf(chosen)
                val other = distribution.otherCount(chosen)
                val unfiled = distribution.unfiled.size
                Text(stringResource(R.string.autocat_preview_count, distribution.total), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                StackedBar(total = distribution.total, chosen = chosenCount, other = other, unfiled = unfiled)
                Spacer(Modifier.height(10.dp))
                Legend(distribution, chosen)
                if (other > 0) {
                    Spacer(Modifier.height(10.dp))
                    InfoLine(stringResource(R.string.autocat_preview_warn, distribution.total, other))
                }
                if (unfiled > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggleMove).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (editor.moveUnfiled) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null, tint = if (editor.moveUnfiled) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.autocat_preview_unfiled, unfiled, chosen), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar() {
    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(AppTheme.colors.outline))
}

private val OtherBarColor = Color(0xFF9CA3AF)
private val UnfiledBarColor = Color(0xFFD1D5DB)

@Composable
private fun StackedBar(total: Int, chosen: Int, other: Int, unfiled: Int) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.outline)) {
        listOf(chosen to colors.accent, other to OtherBarColor, unfiled to UnfiledBarColor).forEach { (count, color) ->
            if (count > 0) Box(Modifier.weight(count.toFloat() / total).height(8.dp).background(color))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(distribution: RuleDistribution, chosen: String) {
    val colors = AppTheme.colors
    val items = buildList {
        val chosenCount = distribution.countOf(chosen)
        if (chosenCount > 0) add(Triple(chosen, chosenCount, colors.accent))
        distribution.byCategory.filter { it.first != chosen }.forEach { add(Triple(it.first, it.second, OtherBarColor)) }
        if (distribution.unfiled.isNotEmpty()) add(Triple(stringResource(R.string.autocat_none_label), distribution.unfiled.size, UnfiledBarColor))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.take(3).forEach { (name, count, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.autocat_legend_item, name, count), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        if (items.size > 3) {
            Text(stringResource(R.string.autocat_legend_more, items.size - 3), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

/** 수정: 내 링크 분포 (카테고리별 가로 막대 줄, §3.3-4) */
@Composable
private fun EditDistribution(editor: RuleSheet.Editor) {
    val colors = AppTheme.colors
    SectionCaption(stringResource(R.string.autocat_distribution_label))
    val distribution = editor.distribution
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Gutter)
            .clip(RoundedCornerShape(16.dp))
            .background(sheetFieldColor())
            .padding(16.dp),
    ) {
        if (distribution == null) {
            SkeletonBar()
            return@Column
        }
        Text(
            stringResource(R.string.autocat_distribution_title, AutoCategoryKeys.siteName(editor.selectedKey.host), distribution.total),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = colors.textPrimary,
        )
        if (distribution.total == 0) return@Column
        Spacer(Modifier.height(10.dp))
        val max = (distribution.byCategory.maxOfOrNull { it.second } ?: 0).coerceAtLeast(distribution.unfiled.size).coerceAtLeast(1)
        val rows = distribution.byCategory.map { Triple(it.first, it.second, if (it.first == editor.category.name) colors.accent else OtherBarColor) } +
            (if (distribution.unfiled.isNotEmpty()) listOf(Triple(stringResource(R.string.autocat_none_label), distribution.unfiled.size, UnfiledBarColor)) else emptyList())
        rows.forEach { (name, count, color) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(Modifier.fillMaxWidth(count.toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                }
                Text("$count", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(start = 8.dp).width(28.dp))
            }
        }
        val other = distribution.otherCount(editor.category.name)
        if (other > 0) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.autocat_distribution_keep, other), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun OutlineButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Gutter)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) colors.accent else colors.textSecondary)
    }
}

// ---------------- 기존 링크에 적용 (§6) ----------------

@Composable
private fun ApplyExistingSheet(apply: RuleSheet.Apply, viewModel: RuleSheetViewModel) {
    val colors = AppTheme.colors
    SheetFrame(onDismiss = { viewModel.cancelApply() }) {
        SheetHeader(
            title = stringResource(R.string.autocat_apply_title),
            description = stringResource(R.string.autocat_apply_preview, apply.rows.size, apply.categoryName),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.autocat_suggest_row, AutoCategoryKeys.displayName(apply.key), apply.categoryName),
            style = MaterialTheme.typography.labelLarge, color = colors.accent,
            modifier = Modifier.padding(horizontal = AppDimens.Gutter).clip(CircleShape).background(colors.accentTint).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter)) {
            Text(stringResource(R.string.autocat_apply_list_label), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.autocat_apply_count, apply.rows.size), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        apply.rows.take(3).forEach { row -> ApplyRow(row, apply) }
        if (apply.rows.size > 3) {
            Text(stringResource(R.string.autocat_apply_more, apply.rows.size - 3), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.padding(horizontal = AppDimens.Gutter)) { InfoLine(stringResource(R.string.autocat_apply_undo_note)) }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.padding(horizontal = AppDimens.Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(sheetFieldColor())
                    .clickable { viewModel.cancelApply() },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.autocat_apply_cancel), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary) }
            PrimaryButton(
                text = stringResource(R.string.autocat_apply_confirm, apply.rows.size),
                onClick = { viewModel.confirmApply() },
                enabled = apply.rows.isNotEmpty(),
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun ApplyRow(row: AutoCatLinkRow, apply: RuleSheet.Apply) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = row.imgUrl, contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(sheetFieldColor()),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title?.takeIf { it.isNotBlank() } ?: row.url.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val from = if (row.category == com.jinscompany.saveurl.domain.model.CategoryModel.UNCATEGORIZED) row.category!! else stringResource(R.string.autocat_none_label)
            Text(
                stringResource(R.string.autocat_apply_row_meta, AutoCategoryKeys.keysOf(row.url)?.host.orEmpty(), from, apply.categoryName),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------- 규칙을 고칠까요? (§7) ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FixRuleSheet(fix: RuleSheet.Fix, viewModel: RuleSheetViewModel) {
    val colors = AppTheme.colors
    val overrides = fix.recent.count { it.second }
    SheetFrame(onDismiss = { viewModel.dismiss() }) {
        SheetHeader(title = stringResource(R.string.autocat_fix_title), description = stringResource(R.string.autocat_fix_body, fix.siteName, overrides))
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter).clip(RoundedCornerShape(16.dp))
                .background(sheetFieldColor()).padding(16.dp),
        ) {
            Text(stringResource(R.string.autocat_fix_recent), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fix.recent.forEach { (name, changed) ->
                    Text(
                        text = name ?: stringResource(R.string.autocat_none_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (changed) colors.accent else colors.textSecondary,
                        modifier = Modifier.clip(CircleShape).background(if (changed) colors.accentTint else colors.background)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.autocat_fix_current, fix.siteName, fix.rule.categoryName, fix.rule.rule.appliedCount),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        val common = fix.common
        if (common != null) {
            FixOption(Icons.Outlined.SwapHoriz, stringResource(R.string.autocat_fix_change_to, common.name),
                stringResource(R.string.autocat_fix_change_to_sub, fix.siteName, common.name)) { viewModel.fixChangeToCommon() }
        } else {
            FixOption(Icons.Outlined.SwapHoriz, stringResource(R.string.autocat_fix_change_pick),
                stringResource(R.string.autocat_fix_change_pick_sub)) { viewModel.fixPickCategory() }
        }
        FixOption(Icons.Outlined.Block, stringResource(R.string.autocat_fix_disable), stringResource(R.string.autocat_fix_disable_sub)) { viewModel.fixDisable() }
        FixOption(Icons.Outlined.CheckCircle, stringResource(R.string.autocat_fix_keep), stringResource(R.string.autocat_fix_keep_sub)) { viewModel.fixKeep() }
        TextButton(onClick = { viewModel.fixOpenSettings() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.autocat_fix_open_settings), color = colors.textSecondary)
        }
    }
}

@Composable
private fun FixOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = AppDimens.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}
