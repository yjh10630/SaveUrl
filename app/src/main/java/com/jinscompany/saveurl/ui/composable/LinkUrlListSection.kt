package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.domain.model.UrlData

@Composable
fun LinkUrlListSection(
    listState: LazyListState,
    items: List<UrlData>,
    onClick: (UrlData) -> Unit,
    longOnClick: (UrlData) -> Unit
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
        modifier = Modifier
            .background(Color.DarkGray)
    ) {
        itemsIndexed(
            items = items,
            key = { index, data ->
                data.id
            }) { index, item ->
            LinkUrlItem(
                Modifier.animateItem(),
                item,
                itemHeight = if (item.tagList.isNullOrEmpty()) 120.dp else 160.dp,
                onClick = { url -> onClick.invoke(item) },
                longOnClick = { data -> longOnClick.invoke(data) },
                tagRemoveClick = {},
            )
            if (index < items.lastIndex) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}