package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import kotlinx.coroutines.launch

/** 미리보기 정보(사이트 이름/제목/내용) 직접 수정 시트. 크롤링 실패 시에도 같은 시트를 쓴다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewContentEditBottomSheet(
    dismiss: () -> Unit = {},
    data: UrlData = UrlData(),
    saveData: (UrlData) -> Unit,
    title: String = "링크 정보 수정",
    subtitle: String = "목록에 보일 사이트 이름, 제목, 내용을 고칠 수 있어요.",
) {
    var urlData by remember { mutableStateOf(data) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(key1 = sheetState.isVisible, effect = {
        onDispose { keyboardController?.hide() }
    })

    AppBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .noRippleClickable { focusManager.clearFocus() }
        ) {
            SheetHeader(title = title, description = subtitle)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.Gutter, vertical = 16.dp)
            ) {
                EditField(label = "사이트 이름", initial = urlData.siteName.orEmpty(), focusClear = { focusManager.clearFocus() }) {
                    urlData = urlData.copy(siteName = it)
                }
                Spacer(modifier = Modifier.height(16.dp))
                EditField(label = "제목", initial = urlData.title.orEmpty(), focusClear = { focusManager.clearFocus() }) {
                    urlData = urlData.copy(title = it)
                }
                Spacer(modifier = Modifier.height(16.dp))
                EditField(
                    label = "내용", initial = urlData.description.orEmpty(), singleLine = false,
                    focusClear = { focusManager.clearFocus() }
                ) { urlData = urlData.copy(description = it) }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "목록에는 2줄까지만 보여요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.textSecondary
                )
            }
            PrimaryButton(
                text = "수정",
                onClick = {
                    scope.launch {
                        focusManager.clearFocus()
                        sheetState.hide()
                    }.invokeOnCompletion {
                        saveData.invoke(urlData)
                    }
                },
                modifier = Modifier.padding(horizontal = AppDimens.Gutter)
            )
            Spacer(
                modifier = Modifier.height(
                    16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
private fun EditField(
    label: String,
    initial: String,
    singleLine: Boolean = true,
    focusClear: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    var content by rememberSaveable { mutableStateOf(initial) }
    FieldLabel(label)
    Spacer(modifier = Modifier.height(8.dp))
    AppTextField(
        value = content,
        onValueChange = {
            content = it
            onValueChange(it)
        },
        placeholder = label,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        maxLines = if (singleLine) 1 else 6,
        onClear = {
            content = ""
            onValueChange("")
        },
        imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        keyboardActions = KeyboardActions(onDone = { focusClear() }),
    )
}

@Composable
@Preview(showBackground = true)
private fun EditFieldPreview() {
    SaveUrlTheme(darkTheme = false) {
        Column(Modifier.padding(20.dp)) {
            EditField("제목", "초보도 쉽게 따라하는 김치찌개", focusClear = {}) {}
        }
    }
}
