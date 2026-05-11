package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.theme.AppBookmark
import com.jinscompany.saveurl.ui.theme.AppSurface
import com.jinscompany.saveurl.ui.theme.AppTextPrimary
import com.jinscompany.saveurl.ui.theme.AppTextSecondary
import com.jinscompany.saveurl.ui.theme.AppUnread

@Composable
fun PreviewLinkUrlItem(
    modifier: Modifier = Modifier,
    data: UrlData
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.siteName ?: "",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = AppTextSecondary,
                    maxLines = 1,
                    letterSpacing = 0.2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = data.title ?: "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTextPrimary,
                    maxLines = 2,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = data.description ?: "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = AppTextSecondary,
                    maxLines = 2,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!data.isRead) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(AppUnread, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    if (data.isBookMark) {
                        Icon(
                            modifier = Modifier.size(13.dp),
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "bookMark",
                            tint = AppBookmark
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = if (data.url.isNullOrEmpty()) "" else data.getDate(),
                        fontSize = 11.sp,
                        color = AppTextSecondary,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            if (!data.imgUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    model = data.imgUrl,
                    placeholder = ColorPainter(Color.Transparent),
                    error = ColorPainter(Color.Transparent),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F)
fun PreviewLinkUrlItemPreview() {
    PreviewLinkUrlItem(
        data = UrlData(
            url = "https://",
            title = "가나다라마바사가나다라마바사",
            description = "가나다라마바사가나다라마바사가나다라마바사가나다라마바사",
            siteName = "매일경제",
            isBookMark = true,
            tagList = mutableListOf("네이트", "네이버", "카카오톡")
        )
    )
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F)
fun PreviewLinkUrlItemPreview2() {
    PreviewLinkUrlItem(data = UrlData())
}
