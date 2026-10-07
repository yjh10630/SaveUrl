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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.FilterDefaults
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
    selectedItem: String
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectItem by remember { mutableStateOf(selectedItem) }

    AppBottomSheet(onDismissRequest = { dismiss.invoke(selectItem) }, sheetState = sheetState) {
        CategorySelectorContent(
            categoryList = categoryList,
            selected = selectItem,
            onSelect = { selectItem = it },
            onEdit = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { goToCateEdit.invoke() }
            },
            onConfirm = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss.invoke(selectItem) }
            }
        )
    }
}

@Composable
private fun CategorySelectorContent(
    categoryList: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = "카테고리 선택",
            description = "하나만 선택할 수 있어요",
            action = { TextAction("편집", onClick = onEdit) }
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

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
        CategorySelectorContent(listOf("개발", "뉴스", "레시피"), "레시피", {}, {}, {})
    }
}
