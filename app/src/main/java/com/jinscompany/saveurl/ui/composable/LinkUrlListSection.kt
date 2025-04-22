package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.jinscompany.saveurl.domain.model.UrlData

@Composable
fun LinkUrlListSection(
    listState: LazyListState,
    items: LazyPagingItems<UrlData>,
    onClick: (UrlData) -> Unit,
    longOnClick: (UrlData) -> Unit
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
        modifier = Modifier
            .background(Color.DarkGray)
    ) {
        items(
            count = items.itemCount,
            key = { index -> items[index]?.id ?: 0 }
        ) { index ->
            val item = items.get(index) ?: return@items
            LinkUrlItem(
                Modifier.animateItem(),
                item,
                itemHeight = if (item.tagList.isNullOrEmpty()) 120.dp else 160.dp,
                onClick = { onClick(item) },
                longOnClick = { longOnClick(item) },
                tagRemoveClick = {},
            )
            if (index < items.itemCount - 1) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}