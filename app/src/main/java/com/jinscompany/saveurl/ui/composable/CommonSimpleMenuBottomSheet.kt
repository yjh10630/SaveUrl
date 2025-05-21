package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonSimpleMenuBottomSheet(
    model: SimpleMenuModel,
    dismiss: () -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { dismiss.invoke() },
        sheetState = modalBottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(
            color = Color.LightGray
        ) },
        containerColor = Color.DarkGray
    ) {
        CommonSimpleMenuView(
            menuList = model.menuList,
            titleTxt = model.titleTxt,
            descriptionTxt = model.descriptionTxt,
            event = { index ->
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    dismiss.invoke()
                    model.menuList[index].event.invoke()
                }
            }
        )
    }
}

@Composable
fun CommonSimpleMenuView(
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    titleTxt: String? = null,
    descriptionTxt: String? = null,
    menuList: List<SimpleMenuModel.MenuModel>,
    event: (Int) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = bottomPadding)
    ) {
        if (!titleTxt.isNullOrEmpty()) {
            item {
                Text(titleTxt, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.LightGray,)

            }
        }
        if (!descriptionTxt.isNullOrEmpty()) {
            item {
                Text(
                    descriptionTxt,
                    fontSize = 14.sp, color = Color.Gray
                )
            }
        }
        itemsIndexed (items = menuList) { index, item ->
            TextButton(
                onClick = { event.invoke(index) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = item.txtColor
                )
            ) {
                Text(item.txt, fontWeight = if (item.isBold) FontWeight.Bold else FontWeight.Medium)
            }
            if (index < menuList.size - 1) {
                Spacer(modifier = Modifier.height(2.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(2.dp))
            }

        }
    }
}

data class SimpleMenuModel(
    val menuList: List<MenuModel>,
    val titleTxt: String? = null,
    val descriptionTxt: String? = null,
) {
    data class MenuModel(
        val txt: String,
        val event: () -> Unit,
        val txtColor: Color,
        val isBold: Boolean = false
    )
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun CommonSimpleMenuBottomSheetPreview() {
    CommonSimpleMenuView(
        titleTxt = "하하하하",
        descriptionTxt = "허허허허",
        menuList = listOf(
        SimpleMenuModel.MenuModel(txt = "전체 삭제", event = {}, txtColor = Color.LightGray, ),
        SimpleMenuModel.MenuModel(txt = "전체 복원", event = {}, txtColor = Color.LightGray, ),
    ))
}