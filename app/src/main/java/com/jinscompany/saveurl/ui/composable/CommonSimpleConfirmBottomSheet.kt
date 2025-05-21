package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
fun CommonSimpleBottomSheet(
    title: String,
    description: String,
    confirmTxt: String,
    cancelTxt: String,
    confirm: () -> Unit,
    cancel: () -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { cancel.invoke() },
        sheetState = modalBottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(
            color = Color.LightGray
        ) },
        containerColor = Color.DarkGray
    ) {
        CommonSimpleBottomSheetView(
            title = title,
            description = description,
            confirmTxt = confirmTxt,
            cancelTxt = cancelTxt,
            confirm = {
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    confirm.invoke()
                }
            },
            cancel = {
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    cancel.invoke()
                }
            }
        )
    }
}

@Composable
fun CommonSimpleBottomSheetView(
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    title: String,
    description: String,
    confirmTxt: String,
    cancelTxt: String,
    confirm: () -> Unit,
    cancel: () -> Unit
) {
    Column (
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = bottomPadding, top = 12.dp)
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.LightGray,)
        Text(description, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.size(36.dp))
        Row {
            CommonNegativeButton(
                onClick = cancel,
                text = cancelTxt,
                modifier = Modifier.weight(1f)
            )
            CommonPositiveButton(
                onClick = confirm,
                text = confirmTxt,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun CommonSimpleBottomSheetPreview() {
    CommonSimpleBottomSheetView(
        title = "휴지통 기능 설정",
        description = "삭제된 항목이 표시됩니다. 이 항목은 7일 후에 자동으로 삭제 됩니다. 삭제된 항목은 복구할 수 없기 때문에 복원 기능을 통해 링크 관리를 할 수 있습니다.",
        confirmTxt = "확인",
        cancel = {},
        confirm = {},
        cancelTxt = "취소"
    )
}