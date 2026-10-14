package com.jinscompany.saveurl.ui.revisit

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.data.revisit.NotifBlock
import com.jinscompany.saveurl.data.revisit.RevisitNotifier
import com.jinscompany.saveurl.domain.revisit.ReminderOption
import com.jinscompany.saveurl.domain.revisit.ReminderPreset
import com.jinscompany.saveurl.domain.revisit.ReminderTimes
import com.jinscompany.saveurl.ui.composable.AppBottomSheet
import com.jinscompany.saveurl.ui.composable.PrimaryButton
import com.jinscompany.saveurl.ui.composable.SecondaryButton
import com.jinscompany.saveurl.ui.composable.SheetHeader
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** 시간 고르기 시트를 열 대상 */
data class ReminderPickRequest(
    val linkId: Int,
    /** 시트 부제 (링크 제목 1줄) */
    val linkTitle: String,
    /** 지금 예약 (있으면 "알림 끄기" 줄) */
    val currentAt: Long?,
)

/** 권한 흐름 결과 */
sealed interface NotifPermissionResult {
    data object Granted : NotifPermissionResult
    /** [나중에]·시스템 대화상자 거절 (다시 물을 수 있음) */
    data object Declined : NotifPermissionResult
    /** 다시 물을 수 없음·앱 알림 끔·채널 끔 → [설정 열기] */
    data class Blocked(val reason: NotifBlock) : NotifPermissionResult
}

@HiltViewModel
class NotifPermissionViewModel @Inject constructor(
    private val preferences: PreferencesManager,
    private val notifier: RevisitNotifier,
) : ViewModel() {
    val asked: StateFlow<Boolean> = preferences.notifPermissionAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markAsked() {
        viewModelScope.launch { preferences.setNotifPermissionAsked() }
    }

    fun blockedReason(channelId: String): NotifBlock? = notifier.blockedReason(channelId)
}

/**
 * 알림 권한 흐름 (42번 §2.3). 앱 시작 때 묻지 않고, 처음 알림을 예약하려 할 때만 묻는다.
 * 1. 이미 보낼 수 있음 → Granted
 * 2. API 33+ 이고 아직 물을 수 있음 → 사전 안내 시트 → [허용하기] → 시스템 대화상자
 * 3. 그 밖(다시 물을 수 없음, 앱 알림·채널 꺼짐) → Blocked
 *
 * 시스템 대화상자가 떠 있는 동안 화면이 다시 만들어지면 결과 콜백이 사라질 수 있다 (권한은 남으므로 다시 누르면 바로 예약된다).
 */
class NotifPermissionRequester internal constructor(
    private val start: (channelId: String, bodyRes: Int, onResult: (NotifPermissionResult) -> Unit) -> Unit,
) {
    fun request(channelId: String, bodyRes: Int, onResult: (NotifPermissionResult) -> Unit) = start(channelId, bodyRes, onResult)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberNotifPermissionRequester(viewModel: NotifPermissionViewModel = hiltViewModel()): NotifPermissionRequester {
    val context = LocalContext.current
    val asked by viewModel.asked.collectAsState()
    var prePromptBody by rememberSaveable { mutableStateOf<Int?>(null) }
    var prePromptChannel by rememberSaveable { mutableStateOf(RevisitNotifier.CHANNEL_REMINDER) }
    val pending = remember { arrayOfNulls<(NotifPermissionResult) -> Unit>(1) }

    fun finish(result: NotifPermissionResult) {
        val callback = pending[0]
        pending[0] = null
        callback?.invoke(result)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val reason = viewModel.blockedReason(prePromptChannel)
        finish(
            when {
                reason == null -> NotifPermissionResult.Granted
                !granted -> NotifPermissionResult.Declined
                else -> NotifPermissionResult.Blocked(reason)
            }
        )
    }

    prePromptBody?.let { bodyRes ->
        AppBottomSheet(onDismissRequest = {
            prePromptBody = null
            finish(NotifPermissionResult.Declined)
        }) {
            NotifPrePromptContent(
                body = stringResource(bodyRes),
                onAllow = {
                    prePromptBody = null
                    viewModel.markAsked()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else finish(NotifPermissionResult.Declined)
                },
                onLater = {
                    prePromptBody = null
                    finish(NotifPermissionResult.Declined)
                },
            )
        }
    }

    return remember(viewModel, launcher) {
        NotifPermissionRequester { channelId, bodyRes, onResult ->
            val reason = viewModel.blockedReason(channelId)
            when {
                reason == null -> onResult(NotifPermissionResult.Granted)
                reason == NotifBlock.PERMISSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    (!viewModel.asked.value || canAskAgain(context)) -> {
                    pending[0] = onResult
                    prePromptChannel = channelId
                    prePromptBody = bodyRes
                }
                else -> onResult(NotifPermissionResult.Blocked(reason))
            }
        }
    }
}

/** 한 번 거절했지만 시스템이 다시 물을 수 있게 하는 경우 (두 번째 거절부터는 false) */
private fun canAskAgain(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 앱(또는 채널) 알림 설정 열기 */
fun Context.openNotificationSettings(reason: NotifBlock?, channelId: String = RevisitNotifier.CHANNEL_REMINDER) {
    val intent = if (reason == NotifBlock.CHANNEL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
}

@Composable
private fun NotifPrePromptContent(body: String, onAllow: () -> Unit, onLater: () -> Unit) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Outlined.NotificationsActive, contentDescription = null, tint = colors.accent,
            modifier = Modifier.padding(horizontal = AppDimens.Gutter).size(28.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        SheetHeader(title = stringResource(R.string.notif_perm_title), description = body)
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = stringResource(R.string.notif_perm_allow), onClick = onAllow, modifier = Modifier.padding(horizontal = AppDimens.Gutter))
        Spacer(modifier = Modifier.height(8.dp))
        SecondaryButton(text = stringResource(R.string.notif_perm_later), onClick = onLater, modifier = Modifier.padding(horizontal = AppDimens.Gutter))
        Spacer(modifier = Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

/**
 * 시간 고르기 시트 + 권한 흐름 (42번 §2.2·§2.3).
 * 시간을 고르는 순간 권한을 확인하고, 허용되면 [onPicked], 아니면 [onNotAllowed] (예약하지 않음).
 * [request] = null 이면 아무것도 그리지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPicker(
    request: ReminderPickRequest?,
    onDismiss: () -> Unit,
    onPicked: (ReminderPreset, Long) -> Unit,
    onTurnOff: () -> Unit,
    onNotAllowed: (NotifPermissionResult) -> Unit,
) {
    val context = LocalContext.current
    val permission = rememberNotifPermissionRequester()
    val pick: (ReminderPreset, Long) -> Unit = { preset, at ->
        onDismiss()
        permission.request(RevisitNotifier.CHANNEL_REMINDER, R.string.notif_perm_body_link) { result ->
            if (result == NotifPermissionResult.Granted) onPicked(preset, at) else onNotAllowed(result)
        }
    }
    if (request == null) return
    AppBottomSheet(onDismissRequest = onDismiss) {
        ReminderSheetContent(
            request = request,
            onPick = pick,
            onCustom = {
                showCustomPicker(context) { at -> pick(ReminderPreset.CUSTOM, at) }
            },
            onTurnOff = {
                onDismiss()
                onTurnOff()
            },
        )
    }
}

@Composable
private fun ReminderSheetContent(
    request: ReminderPickRequest,
    onPick: (ReminderPreset, Long) -> Unit,
    onCustom: () -> Unit,
    onTurnOff: () -> Unit,
) {
    val colors = AppTheme.colors
    val now = remember { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val options = remember(now) { ReminderTimes.options(now, zone) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(title = stringResource(R.string.remind_sheet_title), description = request.linkTitle.takeIf { it.isNotBlank() })
        Spacer(modifier = Modifier.height(8.dp))
        options.forEach { option -> OptionRow(option, now, zone) { onPick(option.preset, option.at) } }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onCustom)
                .padding(horizontal = AppDimens.Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.remind_opt_custom), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = colors.textSecondary)
        }
        if (request.currentAt != null) {
            TextButton(onClick = onTurnOff, modifier = Modifier.padding(horizontal = AppDimens.Gutter - 12.dp)) {
                Text(stringResource(R.string.remind_off), color = colors.textSecondary)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.Gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.remind_local_only), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        Spacer(modifier = Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

@Composable
private fun OptionRow(option: ReminderOption, now: Long, zone: ZoneId, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val label = when (option.preset) {
        ReminderPreset.TONIGHT -> R.string.remind_opt_tonight
        ReminderPreset.TOMORROW -> R.string.remind_opt_tomorrow
        ReminderPreset.WEEKEND -> R.string.remind_opt_weekend
        ReminderPreset.NEXT_WEEKEND -> R.string.remind_opt_next_weekend
        else -> R.string.remind_opt_next_week
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimens.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            ReminderTimes.sheetLabel(option.at, now, zone),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 날짜 → 시간 (지금부터 5분 뒤 ~ 1년 안). 과거를 고르면 안내하고 시간을 다시 고르게 한다 */
private fun showCustomPicker(context: Context, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val start = ZonedDateTime.now(zone).plusHours(1)
    val dateDialog = DatePickerDialog(context, { _, year, month, day ->
        val date = LocalDate.of(year, month + 1, day)
        fun askTime() {
            TimePickerDialog(context, { _, hour, minute ->
                val at = ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()
                if (ReminderTimes.isValidCustom(at, System.currentTimeMillis())) onPicked(at)
                else {
                    Toast.makeText(context, R.string.remind_custom_past, Toast.LENGTH_SHORT).show()
                    askTime()
                }
            }, start.hour, 0, false).show()
        }
        askTime()
    }, start.year, start.monthValue - 1, start.dayOfMonth)
    dateDialog.datePicker.minDate = now
    dateDialog.datePicker.maxDate = now + ReminderTimes.CUSTOM_MAX_AHEAD_DAYS * 24 * 60 * 60_000L
    dateDialog.show()
}
