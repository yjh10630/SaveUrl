package com.jinscompany.saveurl.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.ui.filter.FilterIntent
import com.jinscompany.saveurl.ui.filter.FilterScreenBottomSheet
import com.jinscompany.saveurl.ui.filter.FilterUiEffect
import com.jinscompany.saveurl.ui.filter.FilterUiState
import com.jinscompany.saveurl.ui.filter.FilterViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/**
 * 2분할 검색의 오른쪽: 항상 펼친 필터 패널 (Stitch 7번).
 * 폰 필터 시트와 같은 필터 상태/화면(FilterViewModel + 시트 내용)을 그대로 쓰고, 칩을 바꾸면 즉시 왼쪽 결과에 반영한다.
 * "결과 보기" 버튼은 없고 "초기화"만 있다. 카테고리 "편집" 은 오른쪽 패널에 카테고리 편집을 연다.
 */
@Composable
fun SearchFilterPanel(
    onEditCategory: () -> Unit,
    searchViewModel: SearchViewModel = hiltViewModel(),
    filterViewModel: FilterViewModel = hiltViewModel(key = "searchFilterPanel"),
) {
    LaunchedEffect(Unit) {
        // 패널에 들어올 때마다(카테고리 편집에서 돌아온 경우 포함) 선택지(카테고리·사이트·태그)를 다시 읽는다
        filterViewModel.onIntent(FilterIntent.InitData(searchViewModel.filterParams))
        filterViewModel.uiEffect.collectLatest { effect ->
            if (effect is FilterUiEffect.GoToCategorySetting) onEditCategory()
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { filterViewModel.uiState }
            // 선택지를 읽기 전(빈 상태)의 값으로 저장된 필터를 덮어쓰지 않는다
            .filter { it.categoryState.options.isNotEmpty() }
            .collectLatest { state -> searchViewModel.setFilter(state.toFilterParams()) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 20.dp)
    ) {
        FilterScreenBottomSheet(
            data = filterViewModel.uiState,
            asPanel = true,
            horizontalPadding = 24.dp,
            onClickCategory = { filterViewModel.onIntent(FilterIntent.ToggleCategory(it)) },
            onClickSort = { filterViewModel.onIntent(FilterIntent.ToggleSort(it)) },
            onClickSite = { filterViewModel.onIntent(FilterIntent.ToggleSite(it)) },
            onClickTag = { filterViewModel.onIntent(FilterIntent.ToggleTag(it)) },
            onClickClear = { filterViewModel.onIntent(FilterIntent.Clear) },
            goToCategorySetting = { filterViewModel.onIntent(FilterIntent.GoToCategorySetting) },
        )
    }
}

private fun FilterUiState.toFilterParams() = FilterParams(
    categories = categoryState.selected.toList(),
    sort = sortState.selected.value,
    siteList = siteState.selected.toList(),
    tagList = tagState.selected.toList(),
)
