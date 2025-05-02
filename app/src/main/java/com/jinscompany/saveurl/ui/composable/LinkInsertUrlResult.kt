package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.domain.model.UrlData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkInsertUrlResult(
    data: UrlData,
    isShowCategorySelector: () -> Unit,
    focusClear: () -> Unit,
    onRemoveTagTxt: (String) -> Unit,
    onInsertTagTxt: (String) -> Unit,
    isBookMark: (Boolean) -> Unit
) {
    var tag by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        LinkUrlItem(
            Modifier,
            data,
            {},
            {},
            tagEditMode = true,
            tagRemoveClick = { tag ->
                onRemoveTagTxt.invoke(tag)
            })
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            IconButton(
                onClick = {
                    isBookMark.invoke(!data.isBookMark)
                }
            ) {
                Icon(
                    imageVector = if (data.isBookMark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "bookmark",
                    tint = Color.LightGray
                )
            }
            OutlinedButton(
                onClick = { isShowCategorySelector.invoke() },
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(Color.Transparent),
                contentPadding = PaddingValues(
                    horizontal = 10.dp,
                    vertical = 4.dp
                ),
            ) {
                Text(data.category ?: "전체", color = Color.LightGray)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "categorySelect",
                    tint = Color.LightGray
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = tag,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = "Bookmark"
                )
            },
            trailingIcon = {
                if (tag.isNotEmpty()) {
                    IconButton(onClick = {
                        tag = ""
                    }) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "tagCancel"
                        )
                    }
                }
            },
            onValueChange = { tag = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    onInsertTagTxt.invoke(tag)
                    tag = ""
                    focusClear.invoke()
                }
            ),
            textStyle = TextStyle(color = Color.LightGray),
            label = { Text("태그를 달아 주세요.") },
            placeholder = { Text("태그를 달아 주세요.") },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color.LightGray,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.LightGray,
                unfocusedLabelColor = Color.Gray,
                focusedLeadingIconColor = Color.LightGray,
                unfocusedLeadingIconColor = Color.Gray,
                focusedTrailingIconColor = Color.LightGray,
                unfocusedTrailingIconColor = Color.Gray
            )
        )
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun LinkInsertUrlResultPreview() {
    LinkInsertUrlResult(
        data = UrlData(
            id = 0,
            title = "테스트 데이터",
            description = "테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.테스트 데이터 입니다.",
            tagList = listOf("안녕", "테스트 데이터", "나는 테그 영역"),
            siteName = "나는테스트일보입니다.",
            isBookMark = true,
            category = "테스트"
        ),
        isShowCategorySelector = {},
        focusClear = {},
        onInsertTagTxt = {},
        onRemoveTagTxt = {},
        isBookMark = {}
    )
}