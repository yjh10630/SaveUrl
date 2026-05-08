package com.jinscompany.saveurl.ui.composable.category

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowScope.CategoryItem(
    text: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .wrapContentSize()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.LightGray else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun CategoryFlowRowPreview() {
    val options = listOf("전체", "북마크", "내용", "제목", "전체", "북마크", "내용", "제목")
    FlowRow(modifier = Modifier.padding(8.dp)) {
        options.forEach {
            CategoryItem(text = it, onClick = {}, isSelected = false)
        }
    }
}
