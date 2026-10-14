package com.jinscompany.saveurl.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.composable.AppBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SecondaryButton
import com.jinscompany.saveurl.ui.composable.SheetHeader
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme

/**
 * "백업 파일 만들기" (Stitch 11). 잠금 카테고리가 있을 때만 파일 만들기 전에 띄운다.
 * "잠금 카테고리도 포함"은 기본 끔. 켜고 [파일 만들기]를 누르면 화면이 인증한 뒤 파일 선택으로 넘어간다.
 * 닫힌 상태에서 개수가 드러나지 않도록 잠금 카테고리·링크 개수는 표시하지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvExportSheet(onDismiss: () -> Unit, onConfirm: (includeLocked: Boolean) -> Unit) {
    val colors = AppTheme.colors
    var includeLocked by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(
                title = stringResource(R.string.csv_export_title),
                description = stringResource(R.string.csv_export_desc),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox) { includeLocked = !includeLocked }
                    .padding(horizontal = AppDimens.Gutter - 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeLocked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent),
                    modifier = Modifier.padding(12.dp)
                )
                Text(stringResource(R.string.csv_include_locked), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }
            Text(
                text = stringResource(R.string.csv_include_locked_warn),
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
                text = stringResource(R.string.csv_export_confirm),
                onClick = { onConfirm(includeLocked) },
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
