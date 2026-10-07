package com.jinscompany.saveurl.ui.save_screen.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.composable.AppTextField
import com.jinscompany.saveurl.ui.composable.FieldLabel
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme

/**
 * 태그 입력 + 등록된 태그 칩.
 * 기존 동작 유지: ✓ 버튼으로 쉼표 구분 태그를 추가하고, 칩을 누르면 삭제한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserInputTagSection(
    tagList: List<String>,
    focusClear: () -> Unit,
    onInsertTagTxt: (List<String>) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    var tag by rememberSaveable { mutableStateOf("") }
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter)) {
        FieldLabel("태그")
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = tag,
            onValueChange = { tag = it },
            placeholder = "태그 입력 후 쉼표(,)로 구분",
            onClear = { tag = "" },
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focusClear.invoke() }),
            trailing = {
                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = {
                        val list = tag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onInsertTagTxt.invoke(list)
                        tag = ""
                        focusClear.invoke()
                    }
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "태그 추가", tint = colors.accent, modifier = Modifier.size(22.dp))
                }
            }
        )
        if (tagList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tagList.forEach { item ->
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.accentTint)
                            .clickable { onRemoveTag(item) }
                            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#$item", style = MaterialTheme.typography.bodyMedium, color = colors.accent, maxLines = 1)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Rounded.Close, contentDescription = "삭제", tint = colors.accent, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
