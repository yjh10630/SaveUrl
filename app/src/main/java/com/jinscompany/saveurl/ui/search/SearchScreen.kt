package com.jinscompany.saveurl.ui.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.main.components.DefaultLinkItem
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.openUrlInBrowser
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** 폰 검색 화면 (전체 화면). 필터 없이 검색한다 (기존 동작). */
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel(), popBackStack: () -> Unit) {
    SearchScreenStateful(viewModel = viewModel, popBackStack = popBackStack, isListPane = false)
}

/**
 * 2분할 검색의 왼쪽 패널: 검색창 + 범위 칩 + 결과. 결과에는 오른쪽 필터 패널([SearchFilterPanel]) 값이 바로 반영된다.
 * ViewModel 은 검색 백스택 항목의 것(오른쪽 필터 패널과 같은 인스턴스)이다.
 */
@Composable
fun SearchListPane(onBack: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    SearchScreenStateful(viewModel = viewModel, popBackStack = onBack, isListPane = true)
}

@Composable
private fun SearchScreenStateful(viewModel: SearchViewModel, popBackStack: () -> Unit, isListPane: Boolean) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val selectedFilter = viewModel.selectedScope
    val filterOptions = viewModel.filterList
    val searchResult = viewModel.searchResultFlow?.collectAsLazyPagingItems()

    LaunchedEffect(isListPane) {
        viewModel.setFilterEnabled(isListPane)
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        bottomBar = { AdBannerBar() }
    ) { paddingValues ->
        SearchScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(AppTheme.colors.background),
            popBackStack = popBackStack,
            focusRequester = focusRequester,
            filterOptions = filterOptions,
            selectedFilter = selectedFilter,
            onFilterSelect = { viewModel.onScopeChange(it) },
            keyword = viewModel.keyword,
            onKeywordChange = { viewModel.onKeywordChange(it) },
            searchResult = searchResult,
            searchKeyword = { keyword -> viewModel.onIntent(SearchIntent.Search(keyword, selectedFilter)) },
            context = context,
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    popBackStack: () -> Unit,
    focusRequester: FocusRequester = FocusRequester(),
    filterOptions: List<String> = listOf("전체", "제목", "내용", "태그"),
    selectedFilter: String = "전체",
    onFilterSelect: (String) -> Unit = {},
    keyword: String = "",
    onKeywordChange: (String) -> Unit = {},
    searchResult: LazyPagingItems<UrlData>? = null,
    searchKeyword: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val itemCnt = searchResult?.itemCount ?: 0

    LaunchedEffect(keyword, selectedFilter) {
        snapshotFlow { keyword }
            .debounce(300)
            .distinctUntilChanged()
            .collect { searchKeyword(it) }
    }

    Column(modifier = modifier) {
        // 검색창: ← [🔍 알약형 입력칸 ⓧ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = AppDimens.Gutter, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = popBackStack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = colors.textPrimary)
            }
            SearchField(
                value = keyword,
                onValueChange = onKeywordChange,
                onSearch = {
                    focusManager.clearFocus()
                    searchKeyword(keyword)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }

        // 검색 범위 칩 (전체/제목/내용/태그)
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppDimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions) { filter ->
                ScopeChip(text = filter, selected = selectedFilter == filter, onClick = { onFilterSelect(filter) })
            }
        }

        // 결과 수
        if (keyword.isNotEmpty() && searchResult != null) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) { append("$itemCnt") }
                    append("개 결과")
                },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = AppDimens.Gutter, end = AppDimens.Gutter, top = 16.dp, bottom = 10.dp)
            )
            HorizontalDivider(color = colors.outline, thickness = 1.dp)
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            if (keyword.isEmpty() || searchResult == null) {
                item {
                    SearchStateMessage(
                        icon = Icons.Outlined.Search,
                        title = "저장된 링크를 검색해보세요",
                        message = "제목, 내용, 태그로 찾을 수 있어요"
                    )
                }
            } else {
                when {
                    searchResult.loadState.refresh is LoadState.Loading && itemCnt == 0 -> {
                        item {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    searchResult.loadState.refresh is LoadState.Error -> {
                        item {
                            SearchStateMessage(
                                icon = Icons.Outlined.ErrorOutline,
                                title = "검색 중 오류가 발생했어요",
                                message = "잠시 후 다시 시도해주세요."
                            )
                        }
                    }
                    itemCnt == 0 && searchResult.loadState.refresh is LoadState.NotLoading -> {
                        item {
                            SearchStateMessage(
                                icon = Icons.Outlined.SearchOff,
                                title = "\"$keyword\"에 대한 검색 결과가 없어요",
                                message = "다른 검색어나 검색 범위를 선택해 보세요."
                            )
                        }
                    }
                    else -> {
                        items(
                            count = itemCnt,
                            key = { index -> searchResult.peek(index)?.id ?: index }
                        ) { index ->
                            // LazyPagingItems[index] 로 접근해야 다음 페이지가 로드됨
                            val item = searchResult[index] ?: return@items
                            DefaultLinkItem(
                                data = item,
                                modifier = Modifier.animateItem(),
                                highlight = keyword,
                                onClick = { context.openUrlInBrowser(item.url) },
                                onLongClick = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Search, contentDescription = null,
                    tint = if (value.isNotEmpty()) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("링크 검색", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Cancel, contentDescription = "지우기", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    )
}

@Composable
private fun ScopeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) colors.onAccent else colors.textSecondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SearchStateMessage(icon: ImageVector, title: String, message: String) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.accentTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
@Preview(showBackground = true)
private fun SearchScreenPreview() {
    SaveUrlTheme(darkTheme = false) {
        SearchScreen(popBackStack = {}, searchKeyword = {})
    }
}
