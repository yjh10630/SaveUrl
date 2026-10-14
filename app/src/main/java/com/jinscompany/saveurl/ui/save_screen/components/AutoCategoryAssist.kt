package com.jinscompany.saveurl.ui.save_screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.save_screen.AutoCatDone
import com.jinscompany.saveurl.ui.save_screen.AutoCatRow
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme

/**
 * 저장 화면 카테고리 줄 아래 보조 영역 (36번 §2.2·§2.3).
 * 자동 라벨 / "앞으로 항상" 버튼 / 바꿈 칩 / 확인 문구 중 하나를 그린다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoCategoryAssist(
    row: AutoCatRow,
    onOpenCategory: () -> Unit,
    onMakeRule: () -> Unit,
    onUpdateRule: () -> Unit,
    onDisableRule: () -> Unit,
    onOnce: () -> Unit,
    onUndo: () -> Unit,
) {
    if (row == AutoCatRow.Hidden) return
    val colors = AppTheme.colors
    val modifier = Modifier.fillMaxWidth().padding(start = AppDimens.Gutter, end = AppDimens.Gutter, bottom = 14.dp)
    when (row) {
        AutoCatRow.Hidden -> Unit
        is AutoCatRow.Auto -> {
            val cd = stringResource(R.string.autocat_badge_cd, row.siteName)
            FlowRow(
                modifier = modifier
                    .clickable(onClick = onOpenCategory)
                    .clearAndSetSemantics { contentDescription = cd; role = Role.Button },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.height(24.dp).clip(CircleShape).background(colors.accentTint).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.autocat_badge, row.siteName), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accent, maxLines = 1)
                }
                Text(stringResource(R.string.autocat_badge_change), fontSize = 12.sp, color = colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        is AutoCatRow.MakeRule -> {
            Row(
                modifier = modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accentTint)
                    .clickable(onClick = onMakeRule)
                    .semantics { role = Role.Button }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.autocat_make_rule, row.siteName, row.categoryName),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium,
                    color = colors.accent, maxLines = 2,
                )
            }
        }
        is AutoCatRow.Override -> {
            Column(modifier = modifier) {
                Text(stringResource(R.string.autocat_rule_is, row.siteName, row.ruleCategoryName), fontSize = 12.sp, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.chosenName != null) {
                        AssistChip(text = stringResource(R.string.autocat_update_rule_chip, row.chosenName), accent = true, showSwap = true, onClick = onUpdateRule)
                    } else {
                        AssistChip(text = stringResource(R.string.autocat_disable_rule_chip), accent = true, onClick = onDisableRule)
                    }
                    AssistChip(text = stringResource(R.string.autocat_once_chip), accent = false, onClick = onOnce)
                }
            }
        }
        is AutoCatRow.Done -> {
            val text = when (val done = row.done) {
                is AutoCatDone.RuleCreated -> if (done.moved > 0) stringResource(R.string.autocat_make_rule_done_moved, done.siteName, done.categoryName, done.moved)
                    else stringResource(R.string.autocat_make_rule_done, done.siteName, done.categoryName)
                is AutoCatDone.RuleUpdated -> stringResource(R.string.autocat_make_rule_done, done.siteName, done.categoryName)
                is AutoCatDone.RuleDisabled -> stringResource(R.string.autocat_rule_disabled_inline, done.siteName)
                is AutoCatDone.Once -> done.categoryName?.let { stringResource(R.string.autocat_once_done, it) }
                    ?: stringResource(R.string.autocat_once_done_none)
            }
            val isConfirm = row.done !is AutoCatDone.Once
            Row(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                if (isConfirm && row.done !is AutoCatDone.RuleDisabled) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text, fontSize = 12.sp, color = if (isConfirm) colors.accent else colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (row.canUndo) {
                    Text(
                        stringResource(R.string.autocat_undo), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.accent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onUndo).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistChip(text: String, accent: Boolean, onClick: () -> Unit, showSwap: Boolean = false) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .border(1.dp, colors.outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSwap) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 13.sp, color = if (accent) colors.accent else colors.textSecondary, maxLines = 1)
    }
}
