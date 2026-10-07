package com.jinscompany.saveurl.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppShapes
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme

/**
 * 메인 목록 맨 위 첫 실행 안내 카드 (Stitch E1).
 * 카드나 "둘러보기"를 누르면 튜토리얼을 열고, ✕ 를 누르면 다시 보이지 않는다.
 */
@Composable
fun TutorialEntryCard(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Gutter, vertical = 8.dp)
            .clip(AppShapes.Card)
            .background(colors.accentTint)
            .border(1.dp, colors.accent.copy(alpha = 0.18f), AppShapes.Card)
            .clickable(onClick = singleClick { onOpen() })
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 44.dp, bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.background),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tutorial_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tutorial_card_body),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = singleClick { onOpen() },
                    modifier = Modifier.heightIn(min = 40.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                ) {
                    Text(stringResource(R.string.tutorial_card_action), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        IconButton(
            onClick = singleClick { onDismiss() },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.tutorial_card_dismiss_cd),
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TutorialEntryCardPreview() {
    SaveUrlTheme(darkTheme = false) { TutorialEntryCard(onOpen = {}, onDismiss = {}) }
}
