package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jinscompany.saveurl.domain.model.UrlData

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkUrlItem(
    modifier: Modifier,
    data: UrlData,
    onClick: (String) -> Unit,
    longOnClick: (UrlData) -> Unit,
    tagRemoveClick: (String) -> Unit = {},
    tagEditMode: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = singleClick { onClick(data.url ?: "") },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    longOnClick(data)
                }
            )
            .wrapContentHeight()
    ) {
        Column {
            PreviewLinkUrlItem(data = data)
            if (!data.tagList.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LinkUrlTagList(tagList = data.tagList ?: listOf(), editMode = tagEditMode, removeClick = tagRemoveClick)
            }
        }
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF444444,
)
fun LinkUrlItemPreview() {
    LinkUrlItem(Modifier, UrlData(
        title = "가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사",
        description = "가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사가나다라마바사",
        siteName = "매일경제",
        isBookMark = true,
        tagList = mutableListOf("네이트", "네이버", "카카오톡", "운동", "농구", "축구", "스마트폰", "갤럭시", "아이폰", "운동", "농구", "축구", "스마트폰", "갤럭시", "아이폰")
    ), {}, {}, {}
    )
}