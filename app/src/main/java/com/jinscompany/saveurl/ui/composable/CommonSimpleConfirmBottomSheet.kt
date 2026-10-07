package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import kotlinx.coroutines.launch

/** 제목 + 설명 + [취소][확인] 확인 시트 */
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
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    AppBottomSheet(onDismissRequest = { cancel.invoke() }, sheetState = sheetState) {
        CommonSimpleBottomSheetView(
            title = title,
            description = description,
            confirmTxt = confirmTxt,
            cancelTxt = cancelTxt,
            confirm = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { confirm.invoke() }
            },
            cancel = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { cancel.invoke() }
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
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = bottomPadding + 16.dp)) {
        SheetHeader(title = title, description = description)
        Spacer(modifier = Modifier.height(28.dp))
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(text = cancelTxt, onClick = cancel, modifier = Modifier.weight(1f))
            PrimaryButton(text = confirmTxt, onClick = confirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
@Preview(showBackground = true)
fun CommonSimpleBottomSheetPreview() {
    SaveUrlTheme(darkTheme = false) {
        CommonSimpleBottomSheetView(
            title = "휴지통 기능 설정",
            description = "삭제된 항목이 표시됩니다. 이 항목은 7일 후에 자동으로 삭제 됩니다.",
            confirmTxt = "확인",
            cancel = {},
            confirm = {},
            cancelTxt = "취소"
        )
    }
}
