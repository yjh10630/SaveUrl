package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.theme.AppBackground
import com.jinscompany.saveurl.ui.theme.AppChipSelected
import com.jinscompany.saveurl.ui.theme.AppChipUnselected
import com.jinscompany.saveurl.ui.theme.AppTextPrimary
import com.jinscompany.saveurl.ui.theme.AppTextSecondary

@Composable
fun FilterSelectedList(
    data: List<String>,
    categoryList: List<String> = emptyList(),
    selectedCategory: String = FilterDefaults.CATEGORY_ALL,
    onCategoryClick: (String) -> Unit = {},
    onClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBackground)
    ) {
        // 카테고리 탭 행
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allCategories = listOf(FilterDefaults.CATEGORY_ALL) + categoryList.filter { it != FilterDefaults.CATEGORY_ALL }
                items(allCategories) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        text = category,
                        isSelected = isSelected,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
            // ▽ 전체 필터 바텀시트 버튼
            OutlinedButton(
                onClick = singleClick { onClick.invoke() },
                modifier = Modifier
                    .wrapContentSize()
                    .padding(end = 12.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                border = null
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "filter",
                    tint = AppTextSecondary
                )
            }
        }

        // 선택된 세부 필터 칩 표시 행 (정렬/사이트/태그 선택 시)
        val detailFilters = data.filter { it != selectedCategory }
        if (detailFilters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(detailFilters) { txt ->
                    ActiveFilterChip(text = txt)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = singleClick { onClick() },
        modifier = Modifier.wrapContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) AppChipSelected else AppChipUnselected
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        border = null
    ) {
        if (text == FilterDefaults.CATEGORY_BOOKMARK) {
            Icon(
                modifier = Modifier.size(14.dp),
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = if (isSelected) AppTextPrimary else AppTextSecondary
            )
        } else {
            Text(
                text = text,
                color = if (isSelected) AppTextPrimary else AppTextSecondary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ActiveFilterChip(text: String) {
    OutlinedButton(
        onClick = {},
        modifier = Modifier.wrapContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppChipUnselected),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        border = null
    ) {
        Text(
            text = text,
            color = AppChipSelected,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F)
fun FilterSelectedListPreview() {
    FilterSelectedList(
        data = listOf("최신순"),
        categoryList = listOf("개발", "디자인", "뉴스"),
        selectedCategory = "개발",
    )
}
