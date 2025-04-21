package com.jinscompany.saveurl.ui.composable.category

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.domain.model.CategoryModel

@Composable
fun EditCategoryList(
    list: List<CategoryModel>,
    selectedItemName: String,
    onClick: (String) -> Unit,
    deleteClick: (String) -> Unit
) {
    LazyColumn {
        items(list) { item ->
            CategoryFillMaxWidthItem(
                text = item.name,
                contentCount = item.contentCnt,
                onClick = { onClick.invoke(item.name) },
                deleteClick = { deleteClick.invoke(item.name) },
                isSelected = selectedItemName == item.name
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}