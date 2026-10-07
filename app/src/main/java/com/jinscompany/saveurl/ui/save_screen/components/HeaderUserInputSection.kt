package com.jinscompany.saveurl.ui.save_screen.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.composable.AppTextField
import com.jinscompany.saveurl.ui.composable.FieldLabel
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme

/** 링크 주소 입력. 포커스를 잃을 때 값이 있으면 크롤링을 시작한다 (기존 동작 유지) */
@Composable
fun HeaderUserInputSection(
    url: String,
    userInputStartCrawler: (String) -> Unit,
    focusClear: () -> Unit,
) {
    var linkUrl by rememberSaveable { mutableStateOf("") }
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        linkUrl = url
    }
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
        FieldLabel("링크 주소")
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = linkUrl,
            onValueChange = { linkUrl = it },
            placeholder = "https://",
            leadingIcon = Icons.Outlined.Link,
            onClear = { linkUrl = "" },
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focusClear.invoke() }),
            modifier = Modifier.onFocusChanged { focusState ->
                // 포커스를 "잃을 때"만 크롤링한다. onFocusChanged 는 처음 그려질 때도 (포커스 없음으로) 불리므로,
                // 회전·접기/펼치기로 화면이 다시 그려질 때 다시 크롤링되어 사용자가 고른 카테고리 등이 추천값으로
                // 되돌아가지 않도록 직전 상태를 본다.
                val lostFocus = wasFocused && !focusState.isFocused
                wasFocused = focusState.isFocused
                if (lostFocus && linkUrl.isNotEmpty()) {
                    userInputStartCrawler.invoke(linkUrl)
                }
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "링크를 붙여넣으면 자동으로 정보를 가져와요",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }
    }
}
