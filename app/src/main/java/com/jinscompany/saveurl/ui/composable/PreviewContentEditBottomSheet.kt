package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.edittext.MultiLineEditText
import com.jinscompany.saveurl.ui.composable.edittext.SingleLineEditText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewContentEditBottomSheet(
    dismiss: () -> Unit = {},
    data: UrlData = UrlData(),
    saveData: (UrlData) -> Unit,
    title: String = "링크 수정하기",
    subtitle: String = "수정할 웹 사이트 의 링크정보를 입력해 주세요.",
) {
    var urlData by remember { mutableStateOf(data) }
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(key1 = modalBottomSheetState.isVisible, effect = {
        onDispose { keyboardController?.hide() }
    })

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = modalBottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.LightGray) },
        containerColor = Color.DarkGray,
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.noRippleClickable { focusManager.clearFocus() }
        ) {
            val maxHeight = this@BoxWithConstraints.maxHeight * 0.9f
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                    Text(subtitle, fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    SingleLineEditText(
                        txt = urlData.siteName ?: "",
                        hint = "사이트 이름",
                        focusClear = { focusManager.clearFocus() },
                        onValueChange = { urlData = urlData.copy(siteName = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SingleLineEditText(
                        txt = urlData.title ?: "",
                        hint = "제목",
                        focusClear = { focusManager.clearFocus() },
                        onValueChange = { urlData = urlData.copy(title = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MultiLineEditText(
                        focusClear = { focusManager.clearFocus() },
                        hint = "내용",
                        txt = urlData.description ?: "",
                        onValueChange = { urlData = urlData.copy(description = it) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    CommonPositiveButton(
                        onClick = {
                            scope.launch {
                                focusManager.clearFocus()
                                modalBottomSheetState.hide()
                            }.invokeOnCompletion {
                                saveData.invoke(urlData)
                            }
                        },
                        enabled = true,
                        text = "수정",
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
@Preview
fun PreviewContentEditBottomSheetPreview() {
    MultiLineEditText(
        focusClear = {},
        hint = "내용",
        txt = "내요입니다",
        onValueChange = {}
    )
}
