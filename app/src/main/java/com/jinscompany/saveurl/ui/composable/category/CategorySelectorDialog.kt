package com.jinscompany.saveurl.ui.composable.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.lock.LocalCategoryLockSession
import com.jinscompany.saveurl.ui.lock.rememberOpenLockedCategories
import com.jinscompany.saveurl.ui.composable.AppBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SheetHeader
import com.jinscompany.saveurl.ui.composable.TextAction
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import kotlinx.coroutines.launch

/**
 * 카테고리 선택 시트 (Stitch 17): 라디오 리스트, 단일 선택.
 * "선택 안 함" = [FilterDefaults.CATEGORY_ALL] (기존: 선택된 칩을 다시 누르면 전체로 해제되던 동작과 같은 값).
 * 시트를 내려 닫아도 현재 선택값으로 dismiss 를 호출한다 (기존 동작 유지).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectorDialog(
    dismiss: (String) -> Unit,
    categoryList: List<String>,
    goToCateEdit: () -> Unit,
    selectedItem: String,
    /** 잠금 카테고리. 세션이 닫혀 있으면 "잠금 카테고리에 저장…" 한 행으로 접고, 인증하면 같은 시트 안에서 펼친다 */
    lockedCategoryList: List<String> = emptyList(),
    /** 헤더 설명 (기본 "하나만 선택할 수 있어요"). 규칙이 고른 상태면 "인스타그램 규칙이 '맛집'을 골랐어요" */
    description: String? = null,
    /** 머리의 "편집" (카테고리 편집 화면으로). 공유 빠른 저장 시트에서는 앱 화면을 띄울 수 없어 숨긴다 (44번 §3.4) */
    showEdit: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectItem by remember { mutableStateOf(selectedItem) }
    val isUnlocked by LocalCategoryLockSession.current.isUnlocked.collectAsState()
    val openLocked = rememberOpenLockedCategories("category_selector_open")

    AppBottomSheet(onDismissRequest = { dismiss.invoke(selectItem) }, sheetState = sheetState, dialogWidth = 400.dp) {
        CategorySelectorContent(
            categoryList = categoryList,
            lockedCategoryList = lockedCategoryList,
            isUnlocked = isUnlocked,
            description = description,
            onOpenLocked = { openLocked(R.string.prompt_save_to_locked) {} },
            selected = selectItem,
            onSelect = { selectItem = it },
            onEdit = if (showEdit) {
                { scope.launch { sheetState.hide() }.invokeOnCompletion { goToCateEdit.invoke() } }
            } else null,
            onConfirm = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss.invoke(selectItem) }
            }
        )
    }
}

@Composable
private fun CategorySelectorContent(
    categoryList: List<String>,
    lockedCategoryList: List<String> = emptyList(),
    isUnlocked: Boolean = false,
    description: String? = null,
    onOpenLocked: () -> Unit = {},
    selected: String,
    onSelect: (String) -> Unit,
    /** null = "편집" 숨김 */
    onEdit: (() -> Unit)?,
    onConfirm: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = "카테고리 선택",
            description = description ?: "하나만 선택할 수 있어요",
            action = onEdit?.let { edit -> { TextAction("편집", onClick = edit) } }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.Gutter - 8.dp)
        ) {
            val rows = listOf(FilterDefaults.CATEGORY_ALL) + categoryList.filter { it != FilterDefaults.CATEGORY_ALL }
            rows.forEachIndexed { index, name ->
                RadioRow(
                    label = if (name == FilterDefaults.CATEGORY_ALL) "선택 안 함" else name,
                    selected = selected == name || (name == FilterDefaults.CATEGORY_ALL && selected.isEmpty()),
                    onClick = { onSelect(name) },
                )
                if (index < rows.lastIndex) {
                    HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            if (lockedCategoryList.isNotEmpty()) {
                HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
                if (isUnlocked) {
                    lockedCategoryList.forEachIndexed { index, name ->
                        RadioRow(label = name, selected = selected == name, onClick = { onSelect(name) }, locked = true)
                        if (index < lockedCategoryList.lastIndex) {
                            HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                } else {
                    SaveToLockedRow(onClick = onOpenLocked)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(
            text = "선택 완료",
            onClick = onConfirm,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.padding(horizontal = AppDimens.Gutter)
        )
        Spacer(modifier = Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

/** 세션이 닫혀 있을 때 잠금 카테고리를 접은 행 (이름을 보이지 않는다) */
@Composable
private fun SaveToLockedRow(onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(R.string.picker_save_to_locked), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(stringResource(R.string.picker_save_to_locked_desc), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, locked: Boolean = false) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accentTint else colors.accentTint.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (locked) {
            Icon(
                Icons.Outlined.Lock, contentDescription = null,
                tint = if (selected) colors.accent else colors.textSecondary,
                modifier = Modifier.padding(end = 6.dp).size(14.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) colors.accent else colors.accent.copy(alpha = 0f))
                .border(1.5.dp, if (selected) colors.accent else colors.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "선택됨", tint = colors.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategorySelectorPreview() {
    SaveUrlTheme(darkTheme = false) {
        CategorySelectorContent(
            categoryList = listOf("개발", "뉴스", "레시피"), lockedCategoryList = listOf("쇼핑"),
            selected = "레시피", onSelect = {}, onEdit = {}, onConfirm = {},
        )
    }
}
