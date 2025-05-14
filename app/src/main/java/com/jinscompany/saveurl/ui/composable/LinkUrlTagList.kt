package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

//todo 선택시 아래로 확장 될 수 있도록 변경 예정
@Composable
fun LinkUrlTagList(modifier: Modifier = Modifier, tagList: List<String>, editMode: Boolean = false, removeClick: (String) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(tagList.size) {

        if (tagList.isNotEmpty()) {
            listState.animateScrollToItem(tagList.lastIndex)
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState
    ) {
        itemsIndexed(tagList) { index, item ->
            TagItem(modifier = Modifier.animateItem(), item, editMode, removeClick)
            if (index < tagList.lastIndex) {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@Composable
fun TagItem(modifier: Modifier, tag: String, editMode: Boolean, removeClick: (String) -> Unit) {
    Box(
        modifier = Modifier.noRippleClickable {
            removeClick.invoke(tag)
        }
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
            Text(tag, fontSize = 14.sp, fontWeight = FontWeight.Thin, color = Color.LightGray)
            if (editMode) {
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    modifier = Modifier.size(14.dp),
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "remove",
                    tint = Color.LightGray
                )
            }
        }
    }

}

@Composable
@Preview
fun TagItemPreview() {
    TagItem(Modifier, "조선일보", true, {})
}

@Composable
@Preview
fun TagListPreview() {
    LinkUrlTagList(tagList = listOf("조선일보", "네이버", "네이트", "카카오톡", "인스타그램", "운동", "러닝"), editMode = true, removeClick = {})
}