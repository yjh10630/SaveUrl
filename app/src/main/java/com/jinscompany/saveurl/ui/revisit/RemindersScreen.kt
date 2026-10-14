package com.jinscompany.saveurl.ui.revisit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.data.revisit.RevisitRepository
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.LinkReminder
import com.jinscompany.saveurl.domain.model.UpcomingReminder
import com.jinscompany.saveurl.domain.revisit.ReminderPreset
import com.jinscompany.saveurl.domain.revisit.ReminderTimes
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: RevisitRepository,
    private val inbox: RevisitOpenInbox,
    lockSession: CategoryLockSession,
) : ViewModel() {
    /** null = 읽는 중 */
    val upcoming: StateFlow<List<UpcomingReminder>?> = repository.observeUpcoming().map { it as List<UpcomingReminder>? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 열린 세션이면 잠금 링크 제목도 보인다 */
    val isUnlocked: StateFlow<Boolean> = lockSession.isUnlocked

    /** 행 누르기 = 링크 열기 (잠금 링크는 알림과 같은 경로로 인증 뒤) */
    fun open(linkId: Int) = inbox.put(RevisitOpenRequest.Link(linkId))

    suspend fun set(linkId: Int, preset: ReminderPreset, at: Long): LinkReminder? = viewModelScope.async {
        repository.getReminder(linkId).also { repository.setReminder(linkId, at, preset) }
    }.await()

    suspend fun cancel(linkId: Int): LinkReminder? = viewModelScope.async {
        repository.getReminder(linkId).also { repository.cancelReminder(linkId) }
    }.await()

    fun restore(linkId: Int, previous: LinkReminder?) {
        viewModelScope.launch { repository.restoreReminder(linkId, previous) }
    }
}

/**
 * 링크 알림 목록 (42번 §2.6, Stitch 09): 날짜별 묶음, 행 = 썸네일·제목·파란 ⏰ 시각·⋮(시간 바꾸기 / 알림 끄기).
 * 잠금 카테고리 링크는 닫힌 세션에서 자물쇠 + "잠금 카테고리에 저장한 링크" (⋮ 는 인증 없이 쓸 수 있음: 제목을 보이지 않으므로).
 */
@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val upcoming by viewModel.upcoming.collectAsState()
    val unlocked by viewModel.isUnlocked.collectAsState()
    var request by remember { mutableStateOf<ReminderPickRequest?>(null) }
    val now = remember(upcoming) { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }

    ReminderPicker(
        request = request,
        onDismiss = { request = null },
        onPicked = { linkId, preset, at ->
            scope.launch {
                val previous = viewModel.set(linkId, preset, at)
                val undo = snackbar.showSnackbar(
                    resources.getString(R.string.remind_set_snack, ReminderTimes.sheetLabel(at, System.currentTimeMillis(), zone)),
                    actionLabel = resources.getString(R.string.remind_undo), duration = SnackbarDuration.Short,
                )
                if (undo == SnackbarResult.ActionPerformed) viewModel.restore(linkId, previous)
            }
        },
        onTurnOff = { linkId -> turnOff(linkId, viewModel, scope, snackbar, resources) },
        onNotAllowed = { result ->
            scope.launch {
                val blocked = result as? NotifPermissionResult.Blocked
                val action = snackbar.showSnackbar(
                    resources.getString(R.string.notif_off_snack),
                    actionLabel = if (blocked != null) resources.getString(R.string.notif_open_settings) else null,
                )
                if (action == SnackbarResult.ActionPerformed && blocked != null) context.openNotificationSettings(blocked.reason)
            }
        },
    )

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                AppTopBar(title = stringResource(R.string.reminders_title), onNavigationClick = onBack)
            }
        },
        bottomBar = { AdBannerBar() },
    ) { padding ->
        val list = upcoming
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (list != null && list.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Alarm, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.reminders_empty_title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.reminders_empty_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
            } else if (list != null) {
                item {
                    Text(
                        stringResource(R.string.reminders_summary, list.size),
                        style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 4.dp),
                    )
                }
                list.groupBy { ReminderTimes.groupLabel(it.remindAt, now, zone) }.forEach { (label, rows) ->
                    item(key = "h_$label") {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = AppDimens.Gutter, end = AppDimens.Gutter, top = 16.dp, bottom = 6.dp),
                        )
                    }
                    items(rows, key = { it.linkId }) { row ->
                        ReminderRow(
                            row = row,
                            hideTitle = row.locked && !unlocked,
                            now = now,
                            zone = zone,
                            onClick = { viewModel.open(row.linkId) },
                            onChange = {
                                request = ReminderPickRequest(
                                    linkId = row.linkId,
                                    linkTitle = if (row.locked && !unlocked) resources.getString(R.string.reminders_locked_title)
                                        else row.title.orEmpty().ifBlank { row.url.orEmpty() },
                                    currentAt = row.remindAt,
                                )
                            },
                            onTurnOff = { turnOff(row.linkId, viewModel, scope, snackbar, resources) },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.remind_local_only), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                }
            }
        }
    }
}

private fun turnOff(
    linkId: Int,
    viewModel: RemindersViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState,
    resources: android.content.res.Resources,
) {
    scope.launch {
        val previous = viewModel.cancel(linkId)
        val undo = snackbar.showSnackbar(
            resources.getString(R.string.remind_off_snack),
            actionLabel = resources.getString(R.string.remind_undo), duration = SnackbarDuration.Short,
        )
        if (undo == SnackbarResult.ActionPerformed) viewModel.restore(linkId, previous)
    }
}

@Composable
private fun ReminderRow(
    row: UpcomingReminder,
    hideTitle: Boolean,
    now: Long,
    zone: ZoneId,
    onClick: () -> Unit,
    onChange: () -> Unit,
    onTurnOff: () -> Unit,
) {
    val colors = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = AppDimens.Gutter, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            when {
                hideTitle -> Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                !row.imageUrl.isNullOrEmpty() -> AsyncImage(
                    model = row.imageUrl, contentDescription = null,
                    placeholder = ColorPainter(colors.surface), error = ColorPainter(colors.surface),
                    contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp),
                )
                else -> Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (hideTitle) stringResource(R.string.reminders_locked_title) else row.title.orEmpty().ifBlank { row.url.orEmpty() },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Alarm, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(ReminderTimes.shortLabel(row.remindAt, now, zone), style = MaterialTheme.typography.labelMedium, color = colors.accent)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.main_link_more), tint = colors.textSecondary)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.reminders_change_time)) }, onClick = { menu = false; onChange() })
                DropdownMenuItem(text = { Text(stringResource(R.string.remind_off)) }, onClick = { menu = false; onTurnOff() })
            }
        }
    }
}
