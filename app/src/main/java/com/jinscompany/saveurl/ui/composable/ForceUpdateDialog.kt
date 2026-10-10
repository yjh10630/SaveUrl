package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.ForceUpdateBlockReason

/**
 * 강제 업데이트 안내 (닫을 수 없음: 뒤로 가기·바깥 터치 무시).
 * 강제 업데이트를 취소/실패했거나 Play 가 업데이트를 주지 않을 때, 설명 없이 앱을 끄는 대신 띄운다.
 * 되돌리기는 Remote Config 의 min_version 을 내리는 것으로 하며, 앱 안에서 건너뛰는 방법은 두지 않는다.
 *
 * @param isRechecking 다시 시도/다시 확인 진행 중 (버튼 비활성화)
 */
@Composable
fun ForceUpdateDialog(
    reason: ForceUpdateBlockReason,
    isRechecking: Boolean,
    onPlayStoreClick: () -> Unit,
    onRetryClick: () -> Unit,
    onExitClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val message = when (reason) {
        ForceUpdateBlockReason.CANCELED -> R.string.force_update_message_canceled
        ForceUpdateBlockReason.FAILED -> R.string.force_update_message_failed
        ForceUpdateBlockReason.NOT_OFFERED -> R.string.force_update_message_not_offered
    }
    // Play 가 업데이트를 주지 않는 경우는 Play 를 다시 부르는 "다시 시도" 가 아니라 상태를 "다시 확인" 한다
    val retryLabel = if (reason == ForceUpdateBlockReason.NOT_OFFERED) R.string.force_update_recheck else R.string.force_update_retry
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(sheetContainerColor())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.force_update_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.force_update_play_store), onClick = onPlayStoreClick)
            SecondaryButton(
                text = stringResource(retryLabel),
                onClick = { if (!isRechecking) onRetryClick() },
            )
            TextAction(
                text = stringResource(R.string.force_update_exit),
                onClick = onExitClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
