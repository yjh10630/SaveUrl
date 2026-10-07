package com.jinscompany.saveurl.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.ui.composable.AppBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SelectableChip
import com.jinscompany.saveurl.ui.composable.SheetHeader
import com.jinscompany.saveurl.ui.composable.TextAction
import com.jinscompany.saveurl.ui.main.FilterState
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 필터 시트 (Stitch 12). 기존 4개 탭(카테고리/정렬/사이트/태그)을 한 시트의 섹션으로 쌓았다.
 * 선택 로직과 확인/초기화/카테고리 편집 이동은 [FilterViewModel] 그대로다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreenBottomSheet(
    dismiss: () -> Unit,
    initSelectedData: FilterParams,
    onConfirm: (List<String>, String, List<String>, List<String>) -> Unit,
    viewModel: FilterViewModel = hiltViewModel<FilterViewModel>(),
    goToCategorySetting: () -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.onIntent(FilterIntent.InitData(initSelectedData))
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is FilterUiEffect.Confirm -> {
                    scope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        onConfirm.invoke(effect.category, effect.sort, effect.site, effect.tag)
                    }
                }
                FilterUiEffect.GoToCategorySetting -> {
                    scope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        goToCategorySetting.invoke()
                    }
                }
            }
        }
    }

    AppBottomSheet(onDismissRequest = { dismiss.invoke() }, sheetState = modalBottomSheetState) {
        BoxWithConstraints {
            val maxHeight = this@BoxWithConstraints.maxHeight * 0.9f
            FilterScreenBottomSheet(
                modifier = Modifier.heightIn(max = maxHeight),
                data = state,
                onConfirm = { viewModel.onIntent(FilterIntent.Confirm) },
                onClickCategory = { viewModel.onIntent(FilterIntent.ToggleCategory(it)) },
                onClickSort = { viewModel.onIntent(FilterIntent.ToggleSort(it)) },
                onClickSite = { viewModel.onIntent(FilterIntent.ToggleSite(it)) },
                onClickClear = { viewModel.onIntent(FilterIntent.Clear) },
                onClickTag = { viewModel.onIntent(FilterIntent.ToggleTag(it)) },
                goToCategorySetting = { viewModel.onIntent(FilterIntent.GoToCategorySetting) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterScreenBottomSheet(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit = {},
    onClickCategory: (String) -> Unit = {},
    onClickSort: (String) -> Unit = {},
    onClickSite: (String) -> Unit = {},
    onClickTag: (String) -> Unit = {},
    onClickClear: () -> Unit = {},
    data: FilterUiState,
    goToCategorySetting: () -> Unit = {}
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        SheetHeader(title = "필터", action = { TextAction("초기화", onClick = onClickClear) })
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.Gutter, vertical = 8.dp)
        ) {
            // 정렬: 2칸 세그먼트
            FilterSection(title = FilterTab.SORT.label) {
                SortSegment(
                    options = data.sortState.options,
                    selected = data.sortState.selected.value,
                    onClick = onClickSort
                )
            }
            FilterSection(
                title = "${FilterTab.CATEGORY.label} (복수 선택)",
                action = { TextAction("편집", onClick = goToCategorySetting) }
            ) {
                ChipFlow(data.categoryState.options, data.categoryState.selected, onClickCategory)
            }
            FilterSection(title = FilterTab.SITE.label) {
                if (data.siteState.options.isEmpty()) EmptyHint("저장된 사이트가 없어요")
                else ChipFlow(data.siteState.options, data.siteState.selected, onClickSite)
            }
            FilterSection(title = FilterTab.TAG.label) {
                if (data.tagState.options.isEmpty()) EmptyHint("저장된 태그가 없어요")
                else ChipFlow(data.tagState.options, data.tagState.selected, onClickTag, prefix = "#")
            }
        }
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
        PrimaryButton(
            text = "결과 보기",
            onClick = onConfirm,
            modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 12.dp)
        )
        Spacer(modifier = Modifier.height(4.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

@Composable
private fun FilterSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(options: List<String>, selected: List<String>, onClick: (String) -> Unit, prefix: String = "") {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SelectableChip(text = prefix + option, selected = option in selected, onClick = { onClick(option) })
        }
    }
}

@Composable
private fun SortSegment(options: List<String>, selected: String, onClick: (String) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.accent else colors.surface)
                    .clickable { onClick(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.onAccent else colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
}

@Composable
@Preview(showBackground = true)
fun FilterScreenPreview() {
    SaveUrlTheme(darkTheme = false) {
        FilterScreenBottomSheet(
            data = FilterUiState(
                categoryState = FilterState.MultiSelect(listOf("북마크", "전체", "개발"), mutableStateListOf("개발")),
                sortState = FilterState.SingleSelect(listOf("최신순", "과거순"), mutableStateOf("최신순")),
                siteState = FilterState.MultiSelect(listOf("naver.com", "youtube.com"), mutableStateListOf("youtube.com")),
            )
        )
    }
}
