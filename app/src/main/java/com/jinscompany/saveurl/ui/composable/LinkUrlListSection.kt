package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.theme.AppBackground
import com.jinscompany.saveurl.ui.theme.AppTextSecondary

@Composable
fun LinkUrlListSection(
    listState: LazyListState,
    items: LazyPagingItems<UrlData>,
    onClick: (UrlData) -> Unit,
    longOnClick: (UrlData) -> Unit
) {
    val snapshot = items.itemSnapshotList
    // 날짜별 구분을 위한 레이블 계산 (LazyColumn 안에서는 변수 mutation 불가하므로 미리 계산)
    val dateLabels = snapshot.mapIndexed { index, item ->
        val label = item?.getDate() ?: ""
        val prevLabel = if (index > 0) snapshot[index - 1]?.getDate() ?: "" else ""
        label to (label != prevLabel)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        items(
            count = snapshot.size,
            key = { index -> snapshot[index]?.id ?: index }
        ) { index ->
            val item = snapshot[index] ?: return@items
            val (dateLabel, isNewDate) = dateLabels[index]

            if (isNewDate) {
                if (index != 0) Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = dateLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp, top = if (index == 0) 0.dp else 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            LinkUrlItem(
                modifier = Modifier.animateItem(),
                data = item,
                onClick = { onClick(item) },
                longOnClick = { longOnClick(item) },
                tagRemoveClick = {},
            )
        }
    }
}
