package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
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

@Composable
fun PreviewLinkUrlItem(
    modifier: Modifier = Modifier,
    data: UrlData
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                data.siteName ?: "",
                fontSize = 10.sp,
                fontWeight = FontWeight.Thin,
                color = Color.LightGray,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                data.title ?: "",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                maxLines = 1
            )
            Text(
                data.description ?: "",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.LightGray,
                maxLines = 2
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (data.isBookMark) {
                    Icon(
                        modifier = Modifier.size(14.dp),
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = "bookMark",
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = if (data.url.isNullOrEmpty()) "" else data.getDate(),
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.width(5.dp))
        AsyncImage(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp)),
            model = data.imgUrl,
            placeholder = ColorPainter(Color.Transparent),
            error = ColorPainter(Color.LightGray),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF444444,
)
fun PreviewLinkUrlItemPreview() {
    PreviewLinkUrlItem(
        data = UrlData(
            url = "https://",
            title = "가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사",
            description = "가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사",
            siteName = "매일경제",
            isBookMark = true,
            tagList = mutableListOf(
                "네이트",
                "네이버",
                "카카오톡",
                "운동",
                "농구",
                "축구",
                "스마트폰",
                "갤럭시",
                "아이폰",
                "운동",
                "농구",
                "축구",
                "스마트폰",
                "갤럭시",
                "아이폰"
            )
        )
    )
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF444444,
)
fun PreviewLinkUrlItemPreview2() {
    PreviewLinkUrlItem(data = UrlData())
}