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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.composable.category.CategoryItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterBottomSheet(
    options: List<String>,
    clickItem: (String) -> Unit,
    dismiss: () -> Unit,
    currentSelectedItem: String
) {

    val modalBottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
        dragHandle = { BottomSheetDefaults.DragHandle() },
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
                                selectTxt.invoke(it)
                            },
                            isSelected = currentSelectedItem == it
                        )
                    }
                }
            }

        }
    }

}