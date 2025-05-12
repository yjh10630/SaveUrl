package com.jinscompany.saveurl.ui.filter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.ui.composable.category.BookmarkItem
import com.jinscompany.saveurl.ui.composable.category.CategoryItem
import com.jinscompany.saveurl.ui.main.FilterKey
import com.jinscompany.saveurl.ui.theme.Brown
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreenBottomSheet(
    dismiss: () -> Unit,
    initSelectedData: FilterParams,
    onConfirm: (List<String>, String) -> Unit,
    viewModel: FilterViewModel = hiltViewModel<FilterViewModel>(),
    goToCategorySetting: () -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val scope = rememberCoroutineScope()
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.onIntent(FilterIntent.InitData(initSelectedData))
        viewModel.uiEffect.collectLatest { effect ->
            when(effect) {
                is FilterUiEffect.Confirm -> {
                    scope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        onConfirm.invoke(effect.category, effect.sort)
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {dismiss.invoke()},
        sheetState = modalBottomSheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.LightGray
            )
        },
        containerColor = Color.DarkGray,
    ) {
        BoxWithConstraints {
            val maxHeight = maxHeight * 0.9f
            FilterScreenBottomSheet(
                modifier = Modifier.heightIn(max = maxHeight),
                data = state,
                onConfirm = { viewModel.onIntent(FilterIntent.Confirm) },
                onClickCategory = { viewModel.onIntent(FilterIntent.ToggleCategory(it)) },
                onClickSort = { viewModel.onIntent(FilterIntent.ToggleSort(it)) },
                onClickClear = { viewModel.onIntent(FilterIntent.Clear) }
            )
        }
    }
}

@Composable
fun FilterScreenBottomSheet(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit = {},
    onClickCategory: (String) -> Unit = {},
    onClickSort: (String) -> Unit = {},
    onClickClear: () -> Unit = {},
    data: FilterUiState,
) {
    Column (modifier = modifier) {

        // 카테고리
        Text(FilterKey.CATEGORY.name)
        LazyRow {
            val categoryState = data.categoryState
            categoryState.options.forEach { option ->
                item {
                    val selected = option in categoryState.selected
                    if (option == "북마크") {
                        BookmarkItem(
                            onClick = { onClickCategory.invoke(option) },
                            isSelected = selected
                        )
                    } else {
                        CategoryItem(
                            text = option,
                            onClick = { onClickCategory.invoke(option) },
                            isSelected = selected,
                        )    
                    }
                }
            }
        }
        //정렬
        Text(FilterKey.SORT.name)
        LazyRow {
            val sortState = data.sortState
            sortState.options.forEach { option ->
                item {
                    val selected = sortState.selected.value == option
                    CategoryItem(
                        text = option,
                        onClick = { onClickSort.invoke(option) },
                        isSelected = selected,
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color.Gray,
            thickness = 1.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceAround
        ) {
            OutlinedButton(
                onClick = onClickClear,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(Color.Transparent),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "초기화",
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = {
                    onConfirm.invoke()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(width = 1.dp, color = Brown),
                colors = ButtonDefaults.buttonColors(
                    //if (selections.any { it.value.isEmpty() }) Color.DarkGray else Brown
                    Brown
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "선택",
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun FilterScreenPreview() {
    FilterScreenBottomSheet(data = FilterUiState())
}