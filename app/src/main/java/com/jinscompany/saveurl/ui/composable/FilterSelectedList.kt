package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilterSelectedList(data: List<String>, onClick: () -> Unit = {}) {
    LazyRow (
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        item { Spacer(modifier = Modifier.width(8.dp)) }
        item {
            OutlinedButton(
                onClick = { onClick.invoke() },
                modifier = Modifier
                    .animateItem()
                    .wrapContentSize()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Rounded.FilterAlt,
                    contentDescription = "filter",
                    tint = Color.LightGray
                )
            }
        }
        itemsIndexed(items = data) { index, txt ->
            OutlinedButton(
                onClick = { },
                modifier = Modifier
                    .animateItem()
                    .wrapContentSize()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                if (txt == "북마크") {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "icon",
                        tint = Color.Black
                    )
                }
                else {
                    Text(
                        txt,
                        color = Color.Black,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.width(8.dp)) }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun FilterSelectedListPreview() {
    FilterSelectedList(data = listOf("전체", "최신순"))
}
