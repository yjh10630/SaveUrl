package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.domain.model.UrlData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkInsertUrlResult(
    data: UrlData,
    isShowCategorySelector: () -> Unit,
    focusClear: () -> Unit,
    onRemoveTagTxt: (String) -> Unit,
    onInsertTagTxt: (List<String>) -> Unit,
    isBookMark: (Boolean) -> Unit
) {
    var tag by rememberSaveable { mutableStateOf("sdad") }
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
        Row (
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = tag,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Bookmark"
                    )
                },
                trailingIcon = {
                    if (tag.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(end = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable {
                                        val tagList = tag
                                            .split(",")
                                            .map { it.trim() }
                                            .filter { it.isNotEmpty() }
                                        onInsertTagTxt.invoke(tagList)
                                        tag = ""
                                        focusClear.invoke()
                                    },
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "addTag"
                            )
                            Icon(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { tag = "" },
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
                        focusClear.invoke()
                    }
                ),
                textStyle = TextStyle(color = Color.LightGray),
                label = { Text("태그 입력") },
                placeholder = { Text("태그 입력") },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.LightGray,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.LightGray,
                    unfocusedLabelColor = Color.Gray,
                    focusedLeadingIconColor = Color.LightGray,
                    unfocusedLeadingIconColor = Color.Gray,
                    focusedTrailingIconColor = Color.LightGray,
                    unfocusedTrailingIconColor = Color.Gray
                ),
                supportingText = { Text("콤마 ( , ) 를 사용해서 여러개 등록 가능해요!", color = Color.Gray) }
            )
            /*OutlinedButton(
                onClick = {
                    val tagList = tag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onInsertTagTxt.invoke(tagList)
                    tag = ""
                    focusClear.invoke()
                },
                modifier = Modifier
                    .wrapContentSize()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    if (tag.trim().isEmpty()) Color.Transparent else Color.Gray
                ),
                contentPadding = PaddingValues(
                    horizontal = 10.dp,
                    vertical = 4.dp
                ),
            ) {
                Text("#", fontSize = 30.sp, color = if (tag.trim().isEmpty()) Color.Gray else Color.White)
            }*/
        }
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