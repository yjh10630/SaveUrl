package com.jinscompany.saveurl.ui.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.android.gms.ads.AdView
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.AdMobBannerAd
import com.jinscompany.saveurl.ui.composable.FullScreenLoading
import com.jinscompany.saveurl.ui.composable.LinkUrlListSection
import com.jinscompany.saveurl.ui.composable.SearchFilterBottomSheet
import com.jinscompany.saveurl.ui.composable.SearchHeaderUserInputKeyword
import com.jinscompany.saveurl.ui.composable.SearchHeaderUserSelectFilterInfo
import com.jinscompany.saveurl.ui.composable.SearchResultSimpleText

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel(), popBackStack: () -> Unit) {

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var filterTxt by remember { mutableStateOf<String>(viewModel.filterList[0]) }
    val filterOptions = viewModel.filterList
    var filterShowBottomSheet by remember { mutableStateOf(false) }
    val searchResult = viewModel.searchResultFlow?.collectAsLazyPagingItems()
    val itemCnt = searchResult?.itemCount ?: 0

    val adView = remember { AdView(context) }

    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold { paddingValues ->
        if (filterShowBottomSheet) {
            SearchFilterBottomSheet(
                options = filterOptions,
                currentSelectedItem = filterTxt,
                dismiss = { filterShowBottomSheet = false },
                clickItem = {
                    filterTxt = it
                    filterShowBottomSheet = false
                }
            )
        }
        SearchScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.DarkGray),
            onFilterClick = { filterShowBottomSheet = true },
            popBackStack = popBackStack,
            focusRequester = focusRequester,
            itemCnt = itemCnt,
            siteTypeList = listOf(),
            searchResult = searchResult,
            filterTxt = filterTxt,
            searchKeyword = { keyword, filter -> viewModel.search(keyword, filter) },
            adView = adView,
        )
    }
}

/**
 * Used PreView
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    popBackStack: () -> Unit,
    focusRequester: FocusRequester = FocusRequester(),
    itemCnt: Int = 0,
    onFilterClick: () -> Unit = {},
    siteTypeList: List<String> = listOf(),
    filterTxt: String = "전체",
    searchResult: LazyPagingItems<UrlData>? = null,
    searchKeyword: (String, String) -> Unit,
    adView: AdView,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier,
    ) {
        SearchHeaderUserInputKeyword(
            popBackStack = popBackStack,
            focusRequester = focusRequester,
            focusManager = focusManager,
            userEnterSearchKeyword = searchKeyword
        )
        SearchHeaderUserSelectFilterInfo(
            searchResultItemCnt = itemCnt,
            onFilterClick = onFilterClick,
            selectedFilterTxt = filterTxt,
            siteTypeList = siteTypeList
        )
        AdMobBannerAd(adView = adView)
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = Color.Gray,
            thickness = 1.dp,
        )
        searchResult?.let {
            AnimatedContent(
                targetState = searchResult,
                label = ""
            ) { items ->
                when {
                    items.loadState.refresh is LoadState.Loading -> FullScreenLoading()
                    items.loadState.refresh is LoadState.Error -> SearchResultSimpleText(text = "검색결과 에러 입니다.\n잠시 후 다시 시도해주세요.")
                    items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading -> SearchResultSimpleText(
                        text = "검색 결과가 없습니다."
                    )
                    else -> {
                        Column {
                            Spacer(modifier = Modifier.size(12.dp))
                            LinkUrlListSection(
                                listState = rememberLazyListState(),
                                items = items,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.url))
                                    context.startActivity(intent)
                                },
                                longOnClick = {}
                            )
                        }

                    }
                }
            }
        } ?: run {
            SearchResultSimpleText(text = "검색어를 입력해 주세요.")
        }
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF444444,
)
private fun SearchScreenPreview() {
    val context = LocalContext.current
    SearchScreen(
        popBackStack = {},
        siteTypeList = listOf("YouTube", "Naver"),
        searchKeyword = { keyword, filter -> },
        adView = AdView(context))
}