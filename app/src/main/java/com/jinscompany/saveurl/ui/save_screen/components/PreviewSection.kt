package com.jinscompany.saveurl.ui.save_screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.TextAction
import com.jinscompany.saveurl.ui.save_screen.LinkUrlPreviewUiState
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme

private val PreviewCardShape = RoundedCornerShape(20.dp)

/**
 * 자동 미리보기 카드 (Stitch 7/8).
 * - Idle: 안내 문구 / Loading: 진행 표시 + 닫기(크롤링 중단) / LinkUrlData: 이미지·사이트명·제목·설명 + "편집"
 */
@Composable
fun PreviewSection(
    state: LinkUrlPreviewUiState,
    event: () -> Unit,
    editorClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .padding(horizontal = AppDimens.Gutter)
            .fillMaxWidth()
            .clip(PreviewCardShape)
            .background(colors.surface)
    ) {
        when (state) {
            LinkUrlPreviewUiState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "링크를 입력하면 미리보기가 표시돼요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            LinkUrlPreviewUiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("링크 정보를 가져오는 중…", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                    IconButton(modifier = Modifier.align(Alignment.TopEnd), onClick = { event.invoke() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "중단", tint = colors.textSecondary)
                    }
                }
            }
            is LinkUrlPreviewUiState.LinkUrlData -> PreviewContent(state.urlData, editorClick)
        }
    }
}

@Composable
private fun PreviewContent(data: UrlData, editorClick: () -> Unit) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp, top = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                TextAction(text = "편집", onClick = editorClick, color = colors.textSecondary)
            }
        }
        if (!data.imgUrl.isNullOrEmpty()) {
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(shape)
                    .background(colors.background)
                    .border(1.dp, colors.outline, shape)
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = data.imgUrl,
                    placeholder = ColorPainter(colors.background),
                    error = ColorPainter(colors.background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (!data.siteName.isNullOrBlank()) {
            Text(
                text = data.siteName.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = data.title.orEmpty().ifBlank { data.url.orEmpty() },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.1f),
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!data.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = data.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSectionPreview() {
    SaveUrlTheme(darkTheme = false) {
        Column {
            PreviewSection(LinkUrlPreviewUiState.Idle, {}, {})
            Spacer(Modifier.height(12.dp))
            PreviewSection(
                LinkUrlPreviewUiState.LinkUrlData(UrlData(url = "https://a.com", siteName = "네이버 블로그", title = "초보도 쉽게 따라하는 김치찌개", description = "돼지고기와 묵은지만 있으면 30분 완성")),
                {}, {}
            )
        }
    }
}
