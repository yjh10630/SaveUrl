package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.composable.category.CategoryItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterBottomSheet(
    options: List<String>,
    clickItem: (String) -> Unit,
    dismiss: () -> Unit,
    currentSelectedItem: String
) {

    val modalBottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val selectTxt = { text: String ->
        scope.launch {
            modalBottomSheetState.hide()
        }.invokeOnCompletion {
            clickItem.invoke(text)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = modalBottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(
            color = Color.LightGray
        ) },
        containerColor = Color.DarkGray
    ) {
        SearchFilterView(
            options = options,
            itemClick = {
                selectTxt.invoke(it)
            },
            currentSelectedItem = currentSelectedItem
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFilterView(
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    options: List<String> = listOf(),
    itemClick: (String) -> Unit,
    currentSelectedItem: String
) {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = bottomPadding)
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            FlowRow(modifier = Modifier.padding(8.dp)) {
                options.forEach {
                    CategoryItem(
                        text = it,
                        onClick = {
                            itemClick.invoke(it)
                        },
                        isSelected = currentSelectedItem == it
                    )
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
    SearchFilterView(itemClick = {}, currentSelectedItem = "전체", options = listOf("전체", "북마크", "내용", "제목"))
}