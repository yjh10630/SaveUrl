package com.jinscompany.saveurl.ui.save_screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.PreviewLinkUrlItem
import com.jinscompany.saveurl.ui.save_screen.LinkUrlPreviewUiState

@Composable
fun LazyItemScope.PreviewSection(state: LinkUrlPreviewUiState, event: () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
        val data = state as? LinkUrlPreviewUiState.LinkUrlData
        val isItemOn = state == LinkUrlPreviewUiState.Loading || state == LinkUrlPreviewUiState.Idle
        PreviewLinkUrlItem(
            modifier = Modifier.alpha(if (isItemOn) 0f else 1f),
            data = data?.urlData ?: UrlData()
        )
        if (state is LinkUrlPreviewUiState.Loading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Transparent)
                    .clickable(enabled = false) {},
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                IconButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = { event.invoke() }
                ) {
                    Icon(
                        modifier = Modifier.padding(8.dp),
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "close",
                        tint = Color.LightGray,
                    )
                }
            }
        }
    }
}
