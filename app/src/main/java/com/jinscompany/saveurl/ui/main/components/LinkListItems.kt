package com.jinscompany.saveurl.ui.main.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppShapes
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 리스트에 표시할 도메인: 사이트명이 있으면 사이트명, 없으면 URL 의 host */
internal fun UrlData.displayDomain(): String {
    val site = siteName?.trim().orEmpty()
    if (site.isNotEmpty()) return site
    return runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
}

/** 오늘 저장한 링크는 시각("오후 3:12"), 그 외에는 날짜 라벨("어제", "10월 3일") */
internal fun UrlData.displayTime(): String {
    if (url.isNullOrEmpty()) return ""
    val now = Calendar.getInstance()
    val saved = Calendar.getInstance().apply { timeInMillis = addDate }
    val isToday = now.get(Calendar.YEAR) == saved.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == saved.get(Calendar.DAY_OF_YEAR)
    return if (isToday) SimpleDateFormat("a h:mm", Locale.KOREA).format(saved.time) else getDate()
}

/** [keyword] 와 일치하는 부분(대소문자 무시)을 [color] 로 강조 */
internal fun highlightText(text: String, keyword: String?, color: Color): AnnotatedString {
    val key = keyword?.trim().orEmpty()
    if (key.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val idx = text.indexOf(key, start, ignoreCase = true)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(color = color)) { append(text.substring(idx, idx + key.length)) }
            start = idx + key.length
        }
    }
}

internal fun UrlData.metaText(): String =
    listOf(displayDomain(), displayTime()).filter { it.isNotEmpty() }.joinToString(" · ")

/**
 * 보기 방식에 맞는 링크 아이템.
 * 2분할 왼쪽 목록에서만 [menu](⋮ + 드롭다운)와 [selected](편집 중인 행: accentTint 배경 + 왼쪽 3dp 강조 막대)를 쓴다.
 * 폰에서는 둘 다 기본값이라 지금 모양 그대로다.
 */
@Composable
fun LinkListItem(
    data: UrlData,
    viewMode: ListViewMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    selected: Boolean = false,
    menu: (@Composable () -> Unit)? = null,
) {
    val itemModifier = if (selected) modifier.selectedRowBackground() else modifier
    when (viewMode) {
        ListViewMode.DEFAULT -> DefaultLinkItem(data, onClick, onLongClick, itemModifier, menu = menu)
        ListViewMode.LARGE_CARD -> LargeCardLinkItem(data, onClick, onLongClick, itemModifier, menu)
        ListViewMode.COMPACT -> CompactLinkItem(data, onClick, onLongClick, itemModifier, showDivider, menu)
    }
}

/** 선택(편집 중) 행 표시: 좌우 8dp 안쪽에 12dp 라운드 accentTint 배경 + 왼쪽 3dp 강조 막대. 레이아웃은 바꾸지 않는다. */
@Composable
private fun Modifier.selectedRowBackground(): Modifier {
    val colors = AppTheme.colors
    return this.drawBehind {
        val inset = 8.dp.toPx()
        val radius = 12.dp.toPx()
        drawRoundRect(
            color = colors.accentTint,
            topLeft = Offset(inset, 0f),
            size = Size(size.width - inset * 2, size.height),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = colors.accent,
            topLeft = Offset(inset, radius / 2),
            size = Size(3.dp.toPx(), size.height - radius),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.linkClickable(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.combinedClickable(
        onClick = singleClick { onClick() },
        onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        }
    )
}

/**
 * 기본: 64dp 썸네일 + 제목 2줄 + "도메인 · 시간" (+ 태그 1개).
 * 검색 결과([highlight] 키워드 강조), 휴지통([metaOverride] 로 메타 줄 교체)에서도 재사용한다.
 */
@Composable
fun DefaultLinkItem(
    data: UrlData,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    metaOverride: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .linkClickable(onClick, onLongClick)
            .padding(horizontal = AppDimens.Gutter, vertical = AppDimens.ItemSpacing / 2),
        verticalAlignment = Alignment.Top,
    ) {
        LinkThumbnail(imgUrl = data.imgUrl, size = AppDimens.ThumbDefault)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightText(data.title.orEmpty().ifBlank { data.url.orEmpty() }, highlight, colors.accent),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (metaOverride != null) metaOverride() else MetaRow(data = data, showTag = true)
        }
        if (trailing != null) trailing()
        else BookmarkStar(isBookmarked = data.isBookMark, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        if (menu != null) Box(modifier = Modifier.align(Alignment.CenterVertically).padding(start = 2.dp)) { menu() }
    }
}

/** 큰 카드: 16:9 썸네일 카드 */
@Composable
private fun LargeCardLinkItem(
    data: UrlData,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    menu: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .padding(horizontal = AppDimens.Gutter, vertical = 8.dp)
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .border(1.dp, colors.outline, AppShapes.Card)
            .background(colors.background)
            .linkClickable(onClick, onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(colors.surface)
        ) {
            if (!data.imgUrl.isNullOrEmpty()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = data.imgUrl,
                    placeholder = ColorPainter(colors.surface),
                    error = ColorPainter(colors.surface),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                )
            }
            val domain = data.displayDomain()
            if (domain.isNotEmpty()) {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = data.title.orEmpty().ifBlank { data.url.orEmpty() },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                BookmarkStar(isBookmarked = data.isBookMark, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                if (menu != null) Box(modifier = Modifier.padding(start = 2.dp)) { menu() }
            }
            if (!data.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.outline, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnreadDot(isRead = data.isRead)
                Text(
                    text = data.displayTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                data.tagList?.firstOrNull { it.isNotBlank() }?.let { TagChip(it) }
            }
        }
    }
}

/** 컴팩트: 40dp 썸네일 + 1줄 제목 */
@Composable
private fun CompactLinkItem(
    data: UrlData,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    showDivider: Boolean,
    menu: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .linkClickable(onClick, onLongClick)
                .padding(horizontal = AppDimens.Gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinkThumbnail(imgUrl = data.imgUrl, size = AppDimens.ThumbCompact, radius = 10.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title.orEmpty().ifBlank { data.url.orEmpty() },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                MetaRow(data = data, showTag = true)
            }
            BookmarkStar(isBookmarked = data.isBookMark, modifier = Modifier.padding(start = 8.dp))
            if (menu != null) Box(modifier = Modifier.padding(start = 2.dp)) { menu() }
        }
        if (showDivider) {
            HorizontalDivider(
                color = colors.outline,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = AppDimens.Gutter)
            )
        }
    }
}

@Composable
private fun MetaRow(data: UrlData, showTag: Boolean) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        UnreadDot(isRead = data.isRead)
        Text(
            text = data.metaText(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (showTag) {
            data.tagList?.firstOrNull { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.width(8.dp))
                TagChip(it)
            }
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    val colors = AppTheme.colors
    Text(
        text = "#$tag",
        style = MaterialTheme.typography.labelSmall,
        color = colors.accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.accentTint)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 읽지 않은 링크 표시 (강조색 점). 기존 하늘색(#4FC3F7) 점을 강조색 하나로 통일 */
@Composable
private fun UnreadDot(isRead: Boolean) {
    if (isRead) return
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(6.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.accent)
    )
}

/** 즐겨찾기한 링크에만 강조색 별을 표시 (표시 전용, 탭 동작 없음) */
@Composable
private fun BookmarkStar(isBookmarked: Boolean, modifier: Modifier = Modifier) {
    if (!isBookmarked) return
    Icon(
        imageVector = Icons.Rounded.Star,
        contentDescription = stringResource(R.string.main_bookmarked),
        tint = AppTheme.colors.accent,
        modifier = modifier.size(20.dp)
    )
}

@Composable
private fun LinkThumbnail(imgUrl: String?, size: Dp, radius: Dp = AppDimens.ThumbRadius) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(colors.surface)
            // 흰 바탕 썸네일이 배경과 섞이지 않도록 얇은 테두리
            .border(1.dp, colors.outline, shape),
        contentAlignment = Alignment.Center
    ) {
        if (!imgUrl.isNullOrEmpty()) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = imgUrl,
                placeholder = ColorPainter(colors.surface),
                error = ColorPainter(colors.surface),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
}

private val previewData = UrlData(
    url = "https://naver.com",
    title = "초보도 쉽게 따라하는 김치찌개 황금레시피",
    description = "돼지고기 듬뿍 넣고 묵은지로 칼칼하게 끓여내는 밥도둑 찌개",
    siteName = "naver.com",
    isBookMark = true,
    tagList = listOf("레시피"),
)

@Preview(showBackground = true)
@Composable
private fun LinkListItemPreview() {
    SaveUrlTheme(darkTheme = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListViewMode.entries.forEach {
                LinkListItem(data = previewData, viewMode = it, onClick = {}, onLongClick = {}, showDivider = true)
            }
        }
    }
}
