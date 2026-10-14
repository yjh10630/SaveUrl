package com.jinscompany.saveurl.ui.revisit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.revisit.ReminderTimes
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme

/**
 * 다시 볼 링크 카드 (42번 §5.3). 표면색 16dp 카드, 위에 시계 + "다시 볼 링크" + ✕, 가운데 썸네일·제목·"사이트 · n주 전에 저장",
 * 오른쪽 아래 [열기]. 카드 전체를 눌러도 열기, 길게 누르면 일반 링크 메뉴.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RevisitCard(
    link: UrlData,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val now = remember { System.currentTimeMillis() }
    val site = link.siteName?.takeIf { it.isNotBlank() }
        ?: runCatching { android.net.Uri.parse(link.url).host?.removePrefix("www.") }.getOrNull().orEmpty()
    val ago = ReminderTimes.savedAgo(link.addDate, now)
    Column(
        modifier = modifier
            .padding(horizontal = AppDimens.Gutter, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                stringResource(R.string.revisit_card_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.revisit_card_close_cd), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Row(modifier = Modifier.padding(end = 12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(colors.background),
                contentAlignment = Alignment.Center,
            ) {
                if (!link.imgUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = link.imgUrl,
                        contentDescription = null,
                        placeholder = ColorPainter(colors.background),
                        error = ColorPainter(colors.background),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.title?.takeIf { it.isNotBlank() } ?: link.url.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (ago == null) stringResource(R.string.revisit_saved_today, site) else stringResource(R.string.revisit_saved_ago, site, ago),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.revisit_card_open), color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 주간 정리 제안 카드 (42번 §4.1, 한 번만): 달력 아이콘, 제목, 본문, [괜찮아요] [받기] */
@Composable
fun WeeklyPromptCard(onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .padding(horizontal = AppDimens.Gutter, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 8.dp)) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.weekly_prompt_title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.weekly_prompt_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.weekly_prompt_no), color = colors.textSecondary) }
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.weekly_prompt_yes), color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
