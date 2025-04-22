package com.jinscompany.saveurl.ui.composable.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun LazyItemScope.CategoryFillMaxWidthItem(
    text: String,
    contentCount: Int = 0,
    onClick: () -> Unit,
    deleteClick: (String) -> Unit,
    isSelected: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .animateItem()
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.LightGray else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text,
                    modifier = Modifier.wrapContentWidth().weight(1f, false).alignByBaseline(),
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    " (${contentCount})",
                    modifier = Modifier.alignByBaseline(),
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 10.sp,
                )
            }
            IconButton(
                onClick = { deleteClick.invoke(text) },
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "remove",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun LazyItemScope.CategoryItem(
    text: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isEditMode: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .animateItem()
            .wrapContentSize()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.LightGray else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 14.sp,
                maxLines = 1
            )
            if (isEditMode) {
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "remove",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun LazyItemScope.BookmarkItem(onClick: () -> Unit, isSelected: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .animateItem()
            .wrapContentSize()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.LightGray else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Default.Bookmark,
            contentDescription = "icon",
            tint = if (isSelected) Color.Black else Color.White
        )
    }
}

@Composable
fun LazyItemScope.MoreItem(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "moreCategory",
            tint = Color.White
        )
    }
}

@Composable
fun LazyItemScope.ConfirmCancelItem(
    checkOnClick: () -> Unit,
    cancelOnClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VerticalDivider(
            modifier = Modifier
                .height(30.dp)
                .padding(start = 12.dp, end = 12.dp), color = Color.LightGray, thickness = 2.dp
        )
        IconButton(onClick = checkOnClick) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "check",
                tint = Color.White
            )
        }
        IconButton(onClick = cancelOnClick) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.Cancel,
                contentDescription = "cancel",
                tint = Color.White
            )
        }
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF000000
)
private fun CategoryItemPreview() {
    LazyRow {
        item { BookmarkItem(onClick = {}) }
        item {
            CategoryItem(
                text = "전체",
                onClick = {},
                isSelected = false,
                isEditMode = false
            )
        }
        items(listOf("키워드", "태그")) {
            CategoryItem(
                text = it,
                onClick = {},
                isSelected = false,
                isEditMode = false
            )
        }
        item { MoreItem(onClick = {}) }
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF000000,
)
private fun CategoryItemPreview2() {
    LazyRow {
        item { BookmarkItem(onClick = {}) }
        item {
            CategoryItem(
                text = "전체",
                onClick = {},
                isSelected = false,
                isEditMode = false
            )
        }
        items(listOf("키워드", "태그")) {
            CategoryItem(
                text = it,
                onClick = {},
                isSelected = false,
                isEditMode = true
            )
        }
        item {
            ConfirmCancelItem(
                checkOnClick = {},
                cancelOnClick = {}
            )
        }
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF000000,
)
private fun CategoryItemPreview3() {
    LazyColumn {
        items(listOf("키워드키워드키워드키워드키워드키워드키워드키워드키워드", "태그")) {
            CategoryFillMaxWidthItem(
                text = it,
                onClick = {},
                deleteClick = {},
                isSelected = false,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun CategoryFlowRowPreview() {
    val options = listOf("전체", "북마크", "내용", "제목", "전체", "북마크", "내용", "제목", "전체", "북마크", "내용", "제목")
    FlowRow(modifier = Modifier.padding(8.dp)) {
        options.forEach {
            CategoryItem(
                text = it,
                onClick = {},
                isSelected = false
            )
        }
    }
}