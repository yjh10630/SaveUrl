package com.jinscompany.saveurl.ui.filter.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.composable.category.CategoryItem
import com.jinscompany.saveurl.ui.theme.Brown

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabContent(
    data: MutableList<String>,
    state: ScrollState = rememberScrollState(),
    selectedContentList: MutableList<String>,
    isCategoryTabInsertButtonVisible: Boolean = false,
    onCategoryAddClick: () -> Unit = {},
    click: (String) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(state)) {
        FlowRow(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            if (isCategoryTabInsertButtonVisible) {
                OutlinedButton(
                    onClick = onCategoryAddClick,
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(width = 1.dp, color = Brown),
                    colors = ButtonDefaults.buttonColors(containerColor = Brown),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Rounded.LibraryAdd,
                        contentDescription = "categortInsert",
                        tint = Color.White
                    )
                }
            }
            data.forEach { contentName ->
                val isSelected = selectedContentList.firstOrNull { contentName == it } != null
                CategoryItem(
                    text = contentName,
                    onClick = { click.invoke(contentName) },
                    isSelected = isSelected,
                )
            }
        }
    }
}
