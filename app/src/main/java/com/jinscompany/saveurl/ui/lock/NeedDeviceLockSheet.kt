package com.jinscompany.saveurl.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.composable.AppBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SecondaryButton
import com.jinscompany.saveurl.ui.composable.SheetHeader
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme

/**
 * "휴대폰 화면 잠금이 필요해요" (Stitch 03). 화면 잠금이 없는 기기에서 카테고리를 잠그려 할 때.
 * 설정에서 돌아와도 자동으로 잠그지 않는다 (사용자가 다시 자물쇠를 누름).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedDeviceLockSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(
                title = stringResource(R.string.lock_need_credential_title),
                description = stringResource(R.string.lock_need_credential_body),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.lock_need_credential_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .padding(horizontal = AppDimens.Gutter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(14.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.lock_open_security_settings),
                onClick = {
                    onDismiss()
                    context.openDeviceLockSettings()
                },
                modifier = Modifier.padding(horizontal = AppDimens.Gutter),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = AppDimens.Gutter),
            )
            Spacer(modifier = Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}
