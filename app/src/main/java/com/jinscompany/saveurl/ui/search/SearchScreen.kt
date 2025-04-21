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
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.FullScreenLoading
import com.jinscompany.saveurl.ui.composable.LinkUrlListSection
import com.jinscompany.saveurl.ui.composable.SearchFilterBottomSheet
import com.jinscompany.saveurl.ui.composable.SearchHeaderUserInputKeyword
import com.jinscompany.saveurl.ui.composable.SearchHeaderUserSelectFilterInfo
import com.jinscompany.saveurl.ui.composable.SearchResultEmptyOrError

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel(), popBackStack: () -> Unit) {

    val focusRequester = remember { FocusRequester() }
    var filterTxt by remember { mutableStateOf<String>(viewModel.filterList[0]) }
    var itemCnt by remember { mutableIntStateOf(0) }
    val filterOptions = viewModel.filterList
    val searchResultTargetState = viewModel.searchResultUiState.value
    var filterShowBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(viewModel.searchResultUiState.value) {
        itemCnt =
            (viewModel.searchResultUiState.value as? SearchScreenUiState.Success)?.data?.count()
                ?: 0
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
            searchResultTargetState = searchResultTargetState,
            filterTxt = filterTxt
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
    searchResultTargetState: SearchScreenUiState<List<UrlData>> = SearchScreenUiState.Init,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier,
    ) {
        SearchHeaderUserInputKeyword(
            popBackStack = popBackStack,
            focusRequester = focusRequester,
            focusManager = focusManager,
            userEnterSearchKeyword = { keyword, filter -> }
        )
        SearchHeaderUserSelectFilterInfo(
            searchResultItemCnt = itemCnt,
            onFilterClick = onFilterClick,
            selectedFilterTxt = filterTxt,
            siteTypeList = siteTypeList
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = Color.Gray,
            thickness = 1.dp,
        )
        AnimatedContent(
            targetState = searchResultTargetState,
            label = ""
        ) { targetState ->
            when (targetState) {
                is SearchScreenUiState.Empty -> SearchResultEmptyOrError(isEmpty = true)
                is SearchScreenUiState.Error -> SearchResultEmptyOrError(isEmpty = false)
                is SearchScreenUiState.Init -> {}
                is SearchScreenUiState.Loading -> FullScreenLoading()
                is SearchScreenUiState.Success -> {
                    Column {
                        Spacer(modifier = Modifier.size(12.dp))
                        LinkUrlListSection(
                            listState = rememberLazyListState(),
                            items = targetState.data,
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
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF444444,
)
private fun SearchScreenPreview() {
    SearchScreen(popBackStack = {}, siteTypeList = listOf("YouTube", "Naver"), searchResultTargetState = SearchScreenUiState.Empty)
}