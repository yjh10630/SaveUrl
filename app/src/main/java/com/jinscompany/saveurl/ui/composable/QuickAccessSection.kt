package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.theme.AppBackground
import com.jinscompany.saveurl.ui.theme.AppBookmark
import com.jinscompany.saveurl.ui.theme.AppChipSelected
import com.jinscompany.saveurl.ui.theme.AppSurface
import com.jinscompany.saveurl.ui.theme.AppSurfaceVariant
import com.jinscompany.saveurl.ui.theme.AppTextPrimary
import com.jinscompany.saveurl.ui.theme.AppTextSecondary
import com.jinscompany.saveurl.ui.theme.AppUnread

@Composable
fun QuickAccessSection(
    recentItems: List<UrlData>,
    bookmarkItems: List<UrlData>,
    onItemClick: (String) -> Unit,
    onItemLongClick: (UrlData) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("최근 저장", "즐겨찾기")
    val currentItems = if (selectedTab == 0) recentItems else bookmarkItems

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) AppTextPrimary else AppTextSecondary,
                    modifier = Modifier.clickable { selectedTab = index }
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(AppChipSelected, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (currentItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedTab == 0) "저장된 링크가 없어요" else "즐겨찾기한 링크가 없어요",
                    fontSize = 13.sp,
                    color = AppTextSecondary
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentItems, key = { it.id }) { item ->
                    QuickAccessCard(
                        data = item,
                        onClick = { onItemClick(item.url ?: "") },
                        onLongClick = { onItemLongClick(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAccessCard(
    data: UrlData,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(160.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!data.imgUrl.isNullOrEmpty()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = data.imgUrl,
                    placeholder = ColorPainter(AppSurfaceVariant),
                    error = ColorPainter(AppSurface),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
                // 그라디언트 오버레이
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC0F0F0F)),
                                startY = 60f
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = data.siteName ?: "",
                    fontSize = 10.sp,
                    color = AppTextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = data.title ?: "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTextPrimary,
                    maxLines = 2,
                    lineHeight = 17.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!data.isRead) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(AppUnread, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (data.isBookMark) {
                        Icon(
                            modifier = Modifier.size(11.dp),
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = AppBookmark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = data.getDate(),
                        fontSize = 10.sp,
                        color = AppTextSecondary
                    )
                }
            }
        }
    }
}
