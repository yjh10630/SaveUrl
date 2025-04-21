package com.jinscompany.saveurl.ui.search

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jinscompany.saveurl.ui.composable.FullScreenLoading
import com.jinscompany.saveurl.ui.composable.LinkUrlListSection
import com.jinscompany.saveurl.ui.composable.SearchFilterBottomSheet
import com.jinscompany.saveurl.ui.composable.SearchResultEmptyOrError

@Composable
fun SearchScreen(navController: NavHostController) {

    val viewModel = hiltViewModel<SearchViewModel>()
    val context = LocalContext.current
    var keyword by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var filterTxt by remember { mutableStateOf<String>(viewModel.filterList[0]) }
    var filterShowBottomSheet by remember { mutableStateOf(false) }
    var itemCnt by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(viewModel.searchResultUiState.value) {
        itemCnt = (viewModel.searchResultUiState.value as? SearchScreenUiState.Success)?.data?.count() ?: 0
    }

    Scaffold { paddingValues ->
        if (filterShowBottomSheet) {
            SearchFilterBottomSheet(
                options = viewModel.filterList,
                dismiss = { filterShowBottomSheet = false },
                clickItem = {
                    filterTxt = it
                    filterShowBottomSheet = false
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.DarkGray),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                OutlinedTextField(
                    modifier = Modifier
                        .weight(weight = 1f, fill = true)
                        .padding(top = 12.dp, bottom = 12.dp, end = 12.dp, start = 6.dp)
                        .focusRequester(focusRequester),
                    value = keyword,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "keyword"
                        )
                    },
                    trailingIcon = {
                        if (keyword.isNotEmpty()) {
                            IconButton(onClick = {
                                keyword = ""
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "remove"
                                )
                            }
                        }
                    },
                    onValueChange = { keyword = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            viewModel.search(keyword, filterTxt)
                        },
                    ),
                    label = { Text("링크 검색") },
                    placeholder = { Text("링크 검색") }
                )
            }
            Row (
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${itemCnt}",
                    modifier = Modifier.padding(start = 24.dp, end = 6.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("개",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                VerticalDivider(modifier = Modifier
                    .height(12.dp)
                    .padding(start = 12.dp, end = 12.dp), color = Color.Gray, thickness = 1.dp)
                Text(
                    text = filterTxt,
                    modifier = Modifier
                        .clip(shape = RoundedCornerShape(size = 6.dp))
                        .clickable { filterShowBottomSheet = true }
                        .background(Color.LightGray)
                        .padding(
                            vertical = 6.dp,
                            horizontal = 8.dp,
                        ),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = Color.Gray, thickness = 1.dp)
            AnimatedContent(targetState = viewModel.searchResultUiState.value, label = "") { targetState ->
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
}

@Composable
@Preview
private fun SearchScreenPreview() {
    SearchScreen(rememberNavController())
}