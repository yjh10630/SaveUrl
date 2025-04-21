package com.jinscompany.saveurl.ui.composable.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategorySelectorDialog(
    dismiss: (String) -> Unit,
    categoryList: List<String>,
    goToCateEdit: () -> Unit,
    selectedItem: String
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var selectItem by remember { mutableStateOf<String>(selectedItem) }

    ModalBottomSheet(
        onDismissRequest = { dismiss.invoke(selectItem) },
        sheetState = modalBottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = Color.DarkGray
    ) {
        Column(
            modifier = Modifier
                .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = bottomPadding)
                .fillMaxWidth()
                .wrapContentHeight(),

        ) {
            Text("카테고리 선택", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "분류할 카테고리를 선택해 주세요. (하나만 선택이 가능)",
                fontSize = 14.sp, color = Color.Gray
            )
            TextButton(onClick = {
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    goToCateEdit.invoke()
                }
            }) {
                Text("카테고리 편집", style = TextStyle(textDecoration = TextDecoration.Underline))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()) // 스크롤 가능하게 함
                    .padding(vertical = 16.dp)
            ) {
                FlowRow(modifier = Modifier.padding(8.dp)) {
                    categoryList.forEach {
                        CategoryItem(
                            text = it,
                            onClick = {
                                if (selectItem == it) selectItem = "전체"
                                else selectItem = it
                            },
                            isSelected = selectItem == it
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        dismiss.invoke(selectItem)
                    }
                },
                enabled = selectItem.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text(
                    "선택",
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}