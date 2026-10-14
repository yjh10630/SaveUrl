package com.jinscompany.saveurl.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.composable.AppBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SheetHeader
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme

/**
 * 처음 카테고리를 잠갔을 때 한 번만 보이는 안내 (37번 §19).
 * "잠갔는데 누가 볼 수 있지?"에 바로 답한다: 이 폰에만 있음 / 지문·화면 잠금으로 열림 / 1분 뒤 다시 잠김.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockIntroSheet(onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        LockIntroContent(onDismiss = onDismiss)
    }
}

@Composable
private fun LockIntroContent(onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(title = stringResource(R.string.lock_intro_title))
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier.padding(horizontal = AppDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IntroLine(Icons.Outlined.PhoneAndroid, stringResource(R.string.lock_intro_line_local))
            IntroLine(Icons.Outlined.Fingerprint, stringResource(R.string.lock_intro_line_auth))
            IntroLine(Icons.Outlined.Timer, stringResource(R.string.lock_intro_line_relock))
        }
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.btn_confirm),
            onClick = onDismiss,
            modifier = Modifier.padding(horizontal = AppDimens.Gutter),
        )
        Spacer(modifier = Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

@Composable
private fun IntroLine(icon: ImageVector, text: String) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    }
}

@Preview(showBackground = true)
@Composable
private fun LockIntroContentPreview() {
    SaveUrlTheme { LockIntroContent(onDismiss = {}) }
}
