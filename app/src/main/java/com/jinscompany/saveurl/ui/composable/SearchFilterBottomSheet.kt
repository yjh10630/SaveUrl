package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterBottomSheet(
    options: List<String>,
    clickItem: (String) -> Unit,
    dismiss: () -> Unit
) {

    val modalBottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()


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
            SelectorTextButtonGroup(options = listOf("북마크", "전체"), clickItem = { txt ->
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    clickItem.invoke(txt)
                }
            }, settingOnClick = {}, isSettingIcon = false)
            /*SelectorTextButtonGroup(options = options, clickItem = { txt ->
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    clickItem.invoke(txt)
                }
            })*/
        }
    }

}