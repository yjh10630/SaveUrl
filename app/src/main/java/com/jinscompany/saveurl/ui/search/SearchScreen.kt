package com.jinscompany.saveurl.ui.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.AdMobBannerAd
import com.jinscompany.saveurl.ui.composable.FullScreenLoading
import com.jinscompany.saveurl.ui.composable.LinkUrlItem
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppBackground
import com.jinscompany.saveurl.ui.theme.AppChipSelected
import com.jinscompany.saveurl.ui.theme.AppChipUnselected
import com.jinscompany.saveurl.ui.theme.AppDivider
import com.jinscompany.saveurl.ui.theme.AppPrimary
import com.jinscompany.saveurl.ui.theme.AppSurface
import com.jinscompany.saveurl.ui.theme.AppTextPrimary
import com.jinscompany.saveurl.ui.theme.AppTextSecondary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel(), popBackStack: () -> Unit) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var selectedFilter by remember { mutableStateOf(viewModel.filterList[0]) }
    val filterOptions = viewModel.filterList
    val searchResult = viewModel.searchResultFlow?.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        bottomBar = { AdMobBannerAd() }
    ) { paddingValues ->
        SearchScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(AppBackground),
            popBackStack = popBackStack,
            focusRequester = focusRequester,
            filterOptions = filterOptions,
            selectedFilter = selectedFilter,
            onFilterSelect = { selectedFilter = it },
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
    searchResult: LazyPagingItems<UrlData>? = null,
    searchKeyword: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var keyword by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val itemCnt = searchResult?.itemCount ?: 0
    val snapshot = searchResult?.itemSnapshotList

    val dateLabels = snapshot?.mapIndexed { index, item ->
        val label = item?.getDate() ?: ""
        val prevLabel = if (index > 0) snapshot[index - 1]?.getDate() ?: "" else ""
        label to (label != prevLabel)
    }

    LaunchedEffect(keyword, selectedFilter) {
        snapshotFlow { keyword }
            .debounce(300)
            .distinctUntilChanged()
            .collect { searchKeyword(it) }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 검색창
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = popBackStack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppTextSecondary,
                    )
                }
                OutlinedTextField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    placeholder = { Text("링크 검색", color = AppTextSecondary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (keyword.isNotEmpty()) AppPrimary else AppTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(visible = keyword.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { keyword = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "지우기",
                                    tint = AppTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        searchKeyword(keyword)
                    }),
                    textStyle = TextStyle(color = AppTextPrimary, fontSize = 15.sp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppDivider,
                        focusedContainerColor = AppSurface,
                        unfocusedContainerColor = AppSurface,
                        cursorColor = AppPrimary,
                    )
                )
            }
        }

        // 필터 칩
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { filter ->
                    val selected = selectedFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterSelect(filter) },
                        label = {
                            Text(
                                text = filter,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppChipSelected,
                            selectedLabelColor = Color.White,
                            containerColor = AppChipUnselected,
                            labelColor = AppTextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = Color.Transparent,
                            borderColor = Color.Transparent,
                        )
                    )
                }
            }
        }

        // 결과 수 표시
        if (keyword.isNotEmpty() && searchResult != null) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$itemCnt",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTextPrimary
                    )
                    Text(
                        text = "개 결과",
                        fontSize = 13.sp,
                        color = AppTextSecondary,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }
        }

        // 검색 결과 목록
        if (keyword.isEmpty()) {
            item { SearchEmptyHint() }
        } else if (searchResult == null) {
            item { SearchEmptyHint() }
        } else {
            when {
                searchResult.loadState.refresh is LoadState.Loading -> {
                    item { FullScreenLoading() }
                }
                searchResult.loadState.refresh is LoadState.Error -> {
                    item {
                        SearchStateMessage(
                            icon = "⚠️",
                            message = "검색 중 오류가 발생했어요.\n잠시 후 다시 시도해주세요."
                        )
                    }
                }
                itemCnt == 0 && searchResult.loadState.refresh is LoadState.NotLoading -> {
                    item {
                        SearchStateMessage(
                            icon = "🔍",
                            message = "\"$keyword\"에 대한\n검색 결과가 없어요."
                        )
                    }
                }
                else -> {
                    if (snapshot != null && dateLabels != null) {
                        items(
                            count = snapshot.size,
                            key = { index -> snapshot[index]?.id ?: index }
                        ) { index ->
                            val item = snapshot[index] ?: return@items
                            val (dateLabel, isNewDate) = dateLabels[index]

                            if (isNewDate) {
                                if (index != 0) Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = dateLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTextSecondary,
                                    modifier = Modifier.padding(
                                        start = 20.dp, end = 20.dp,
                                        bottom = 8.dp,
                                        top = if (index == 0) 0.dp else 4.dp
                                    )
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            LinkUrlItem(
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 16.dp),
                                data = item,
                                onClick = { _ ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                    context.startActivity(intent)
                                },
                                longOnClick = {},
                                tagRemoveClick = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = AppDivider,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "저장된 링크를 검색해보세요",
                fontSize = 15.sp,
                color = AppTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "제목, 내용, 태그로 찾을 수 있어요",
                fontSize = 13.sp,
                color = AppTextSecondary.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SearchStateMessage(icon: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 40.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = AppTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
private fun SearchScreenPreview() {
    val context = LocalContext.current
    SearchScreen(
        popBackStack = {},
        searchKeyword = {},
    )
}
