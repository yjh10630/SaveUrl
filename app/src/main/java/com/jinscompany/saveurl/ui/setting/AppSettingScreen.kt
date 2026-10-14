package com.jinscompany.saveurl.ui.setting

import android.content.Intent
import com.jinscompany.saveurl.ui.share.excludeOwnShareTarget
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import com.jinscompany.saveurl.ui.navigation.Navigation
import com.jinscompany.saveurl.ui.navigation.navigateInSettingsPane
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.domain.model.ThemeMode
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.main.components.labelRes
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.navigation.navigateToTutorial
import com.jinscompany.saveurl.ui.navigation.navigateToEditCategory
import com.jinscompany.saveurl.ui.lock.LockAuthResult
import com.jinscompany.saveurl.ui.composable.rememberStableActivityResultLauncher
import com.jinscompany.saveurl.ui.lock.rememberLockAuthRequest
import com.jinscompany.saveurl.ui.tutorial.NewPill
import com.jinscompany.saveurl.ui.tutorial.TutorialNews
import com.jinscompany.saveurl.ui.tutorial.TutorialViewModel
import com.jinscompany.saveurl.ui.tutorial.currentVisibleTutorialTopics
import com.jinscompany.saveurl.ui.navigation.navigateToTrash
import com.jinscompany.saveurl.ui.navigation.navigateToAutoCategory
import com.jinscompany.saveurl.ui.navigation.navigateToImport
import com.jinscompany.saveurl.ui.importer.ImportEntry
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.getCurrentAppVersion
import com.jinscompany.saveurl.utils.openPlayStorePage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import com.jinscompany.saveurl.data.revisit.RevisitNotifier
import com.jinscompany.saveurl.ui.composable.AppSwitch
import com.jinscompany.saveurl.ui.navigation.navigateToReminders
import com.jinscompany.saveurl.ui.revisit.NotifPermissionResult
import com.jinscompany.saveurl.ui.revisit.openNotificationSettings
import com.jinscompany.saveurl.ui.revisit.rememberNotifPermissionRequester

/** 2분할 설정에서 오른쪽 패널에 열 수 있는 하위 화면 (왼쪽 목록의 선택 행) */
enum class SettingPaneRow { REMINDERS, LOCKED_CATEGORIES, CATEGORY_EDIT, AUTO_CATEGORY, TRASH, IMPORT, TUTORIAL }

/**
 * 설정.
 * - 폰: 설정 전체 화면 (기존 그대로).
 * - 2분할([isListPane] = true): 왼쪽 설정 목록. 카테고리 편집 / 휴지통 / 사용 방법은 오른쪽 패널에 열고,
 *   [selectedPaneRoute] 로 지금 열린 항목을 선택 표시한다. ← 는 설정 전체를 닫는다.
 */
@Composable
fun AppSettingScreen(
    navController: NavHostController,
    isListPane: Boolean = false,
    selectedPaneRoute: String? = null,
    sharedViewModel: SharedViewModel = hiltViewModel(LocalActivity.current as MainActivity),
    settingViewModel: AppSettingViewModel = hiltViewModel(),
    tutorialViewModel: TutorialViewModel = hiltViewModel(),
) {
    val themeMode by sharedViewModel.themeMode.collectAsState()
    val tutorialBadgePrefs by tutorialViewModel.badgePrefs.collectAsState()
    val tutorialNewCount = TutorialNews.newTopics(currentVisibleTutorialTopics(), tutorialBadgePrefs).size
    val listViewMode by sharedViewModel.listViewMode.collectAsState()
    val context = LocalContext.current
    val isFlexibleUpdatable by sharedViewModel.isFlexibleUpdatable.collectAsState()

    val lockedCategoryCount by settingViewModel.lockedCategoryCount.collectAsState()
    val autoCategoryBadge by settingViewModel.autoCategoryBadge.collectAsState()
    val importEnabled by settingViewModel.importEnabled.collectAsState()
    val importNew by settingViewModel.importNew.collectAsState()
    val resources = LocalResources.current
    // 설정 › 알림 (42번 §6)
    val reminderCount by settingViewModel.reminderCount.collectAsState()
    val revisitCardRemote by settingViewModel.revisitCardRemote.collectAsState()
    val revisitCardEnabled by settingViewModel.revisitCardEnabled.collectAsState()
    val weeklyRemote by settingViewModel.weeklyRemote.collectAsState()
    val weeklyEnabled by settingViewModel.weeklyEnabled.collectAsState()
    val notifBlocked by settingViewModel.notifBlocked.collectAsState()
    val weeklyPermission = rememberNotifPermissionRequester { _, result ->
        if (result == NotifPermissionResult.Granted) settingViewModel.setWeeklyEnabled(true)
        else {
            Toast.makeText(context, R.string.notif_off_snack, Toast.LENGTH_SHORT).show()
            (result as? NotifPermissionResult.Blocked)?.let { context.openNotificationSettings(it.reason, RevisitNotifier.CHANNEL_WEEKLY) }
        }
    }
    val settingLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(settingLifecycle) {
        // 시스템 설정에서 알림을 켜고 돌아오면 경고 줄을 다시 판단한다
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) settingViewModel.refreshNotifBlocked()
        }
        settingLifecycle.lifecycle.addObserver(observer)
        onDispose { settingLifecycle.lifecycle.removeObserver(observer) }
    }
    // 시트 표시 여부와 "포함" 선택값은 ViewModel(SavedStateHandle)에 둔다 (38번 리뷰 2번)
    val showCsvExportSheet by settingViewModel.showCsvExportSheet.collectAsState()

    // 파일 선택 화면에 다녀오는 동안 한 화면 ↔ 2분할이 바뀌어도 결과를 받도록 고정 키로 등록한다
    val exportLauncher = rememberStableActivityResultLauncher(
        key = "setting_csv_export",
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { settingViewModel.exportCsv(it, includeLocked = settingViewModel.exportIncludeLocked) } }
    val launchExport: (Boolean) -> Unit = { includeLocked ->
        settingViewModel.exportIncludeLocked = includeLocked
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        exportLauncher.launch("savelink_backup_$dateStr.csv")
    }
    // 인증 결과는 지금 컴포지션에서 받으므로, 인증 중에 Activity 가 다시 만들어져도 현재 런처로 파일 선택을 연다 (38번 리뷰 1번).
    // 백업만 하고 세션은 열지 않는다 (37번 기획서 1.3)
    val csvIncludeLockedAuth = rememberLockAuthRequest("setting_csv_include_locked") { result, _ ->
        when (result) {
            LockAuthResult.Success, LockAuthResult.PassedWithoutCredential -> {
                settingViewModel.setShowCsvExportSheet(false)
                launchExport(true)
            }
            LockAuthResult.Error -> Toast.makeText(context, R.string.lock_unavailable_retry, Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }
    if (showCsvExportSheet) {
        CsvExportSheet(
            onDismiss = { settingViewModel.setShowCsvExportSheet(false) },
            onConfirm = { includeLocked ->
                if (!includeLocked) {
                    settingViewModel.setShowCsvExportSheet(false)
                    launchExport(false)
                } else {
                    // 체크를 켜는 순간이 아니라 [파일 만들기]에서 인증. 취소하면 시트에 머문다
                    csvIncludeLockedAuth.authenticate(
                        title = resources.getString(R.string.prompt_csv_include_locked),
                        subtitle = resources.getString(R.string.prompt_csv_include_locked_subtitle),
                        allowWithoutCredential = true,
                    )
                }
            },
        )
    }

    // 2분할: "잠금 카테고리"와 "카테고리 편집" 행은 같은 화면을 열므로, 누른 행에 선택 표시를 한다 (37번 기획서 2장)
    var categoryPaneFromLockedRow by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberStableActivityResultLauncher(
        key = "setting_csv_import",
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { settingViewModel.importCsv(it) } }

    LaunchedEffect(Unit) {
        settingViewModel.effect.collect { effect ->
            when (effect) {
                is AppSettingEffect.ShowToast ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        bottomBar = { AdBannerBar() },
    ) { paddingValue ->
        AppSettingScreen(
            paddingValues = paddingValue,
            popBackStack = {
                if (isListPane) navController.popBackStack(Navigation.Routes.APP_SETTING, inclusive = true)
                else navController.popBackStack()
            },
            exportCsvClick = {
                // 잠금 카테고리가 있으면 포함 여부를 먼저 묻는다 (기본 미포함)
                if (lockedCategoryCount > 0) settingViewModel.setShowCsvExportSheet(true) else launchExport(false)
            },
            reminderCount = reminderCount,
            remindersClick = {
                if (isListPane) navController.navigateInSettingsPane(Navigation.Routes.REMINDERS)
                else navController.navigateToReminders()
            },
            revisitCardVisible = revisitCardRemote,
            revisitCardEnabled = revisitCardEnabled,
            onRevisitCardChange = settingViewModel::setRevisitCardEnabled,
            weeklyVisible = weeklyRemote,
            weeklyEnabled = weeklyEnabled,
            onWeeklyChange = { on ->
                if (!on) settingViewModel.setWeeklyEnabled(false)
                else weeklyPermission.request(RevisitNotifier.CHANNEL_WEEKLY, R.string.notif_perm_body_weekly)
            },
            // 예약이나 주간 정리가 켜져 있는데 알림이 꺼져 있을 때만
            notifBlockedVisible = notifBlocked != null && (reminderCount > 0 || weeklyEnabled),
            onOpenNotifSettings = { context.openNotificationSettings(notifBlocked) },
            lockedCategoryCount = lockedCategoryCount,
            lockedCategoriesClick = {
                categoryPaneFromLockedRow = true
                if (isListPane) navController.navigateInSettingsPane(Navigation.Routes.EDIT_CATEGORY)
                else navController.navigateToEditCategory()
            },
            importCsvClick = {
                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
            },
            importEnabled = importEnabled,
            importNew = importNew,
            importClick = {
                if (isListPane) navController.navigateToImport(ImportEntry.SETTINGS, inSettingsPane = true)
                else navController.navigateToImport(ImportEntry.SETTINGS)
            },
            shareMyApp = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=${context.packageName}")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "").excludeOwnShareTarget(context))
            },
            updateClick = { context.openPlayStorePage() },
            emailClick = {
                val emailSelectorIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("jinscompany25@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "SaveLink App 오류 신고")
                    putExtra(Intent.EXTRA_TEXT, "사진 및 영상을 올려주신 다면, 신속한 오류 개선에 도움이 됩니다.")
                    selector = emailSelectorIntent
                }
                if (intent.resolveActivity(context.packageManager) != null)
                    context.startActivity(intent)
            },
            autoCategoryBadge = when (val badge = autoCategoryBadge) {
                is AutoCategoryBadge.Check -> stringResource(R.string.autocat_setting_badge_check, badge.count)
                is AutoCategoryBadge.Suggest -> stringResource(R.string.autocat_setting_badge_suggest, badge.count)
                null -> null
            },
            autoCategoryClick = {
                if (isListPane) navController.navigateToAutoCategory(inSettingsPane = true)
                else navController.navigateToAutoCategory()
            },
            trashClick = {
                if (isListPane) navController.navigateInSettingsPane(Navigation.Routes.TRASH)
                else navController.navigateToTrash()
            },
            tutorialClick = {
                // 2분할: 오른쪽 패널에 튜토리얼 홈(주제 목록). 폰: 전체 화면
                if (isListPane) navController.navigateInSettingsPane(Navigation.Routes.TUTORIAL)
                else navController.navigateToTutorial()
            },
            // 카테고리 편집 행은 2분할에서만 둔다 (폰 설정 화면은 그대로: 카테고리 편집은 필터·카테고리 선택 시트에서 진입)
            categoryEditClick = if (isListPane) {
                {
                    categoryPaneFromLockedRow = false
                    navController.navigateInSettingsPane(Navigation.Routes.EDIT_CATEGORY)
                }
            } else null,
            selectedRow = if (!isListPane) null else when (selectedPaneRoute) {
                Navigation.Routes.EDIT_CATEGORY ->
                    if (categoryPaneFromLockedRow) SettingPaneRow.LOCKED_CATEGORIES else SettingPaneRow.CATEGORY_EDIT
                Navigation.Routes.TRASH -> SettingPaneRow.TRASH
                Navigation.Routes.REMINDERS -> SettingPaneRow.REMINDERS
                Navigation.Routes.AUTO_CATEGORY -> SettingPaneRow.AUTO_CATEGORY
                Navigation.Routes.IMPORT, Navigation.Routes.IMPORT_HISTORY -> SettingPaneRow.IMPORT
                Navigation.Routes.TUTORIAL, Navigation.Routes.TUTORIAL_TOPIC -> SettingPaneRow.TUTORIAL
                else -> null
            },
            tutorialNewCount = tutorialNewCount,
            currentAppVersion = getCurrentAppVersion(context),
            isUpdatable = isFlexibleUpdatable,
            themeMode = themeMode ?: ThemeMode.SYSTEM,
            onThemeModeChange = { sharedViewModel.setThemeMode(it) },
            listViewMode = listViewMode,
            onListViewModeChange = { sharedViewModel.setListViewMode(it) },
        )
    }
}

@Composable
fun AppSettingScreen(
    paddingValues: PaddingValues = PaddingValues(),
    currentAppVersion: String = "1.0.0",
    popBackStack: () -> Unit = {},
    shareMyApp: () -> Unit = {},
    updateClick: () -> Unit = {},
    emailClick: () -> Unit = {},
    tutorialClick: () -> Unit = {},
    /** 사용 방법 행의 "새 기능 n" (0 이면 표시 안 함) */
    tutorialNewCount: Int = 0,
    trashClick: () -> Unit = {},
    exportCsvClick: () -> Unit = {},
    importCsvClick: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    listViewMode: ListViewMode = ListViewMode.DEFAULT,
    onListViewModeChange: (ListViewMode) -> Unit = {},
    isUpdatable: Boolean = false,
    categoryEditClick: (() -> Unit)? = null,
    selectedRow: SettingPaneRow? = null,
    /** 잠금 카테고리 개수 ("데이터" 섹션의 "잠금 카테고리" 행 값) */
    lockedCategoryCount: Int = 0,
    lockedCategoriesClick: () -> Unit = {},
    /** 링크 가져오기 (원격 스위치가 꺼지면 예전 CSV 가져오기 줄) */
    importEnabled: Boolean = true,
    importNew: Boolean = false,
    importClick: () -> Unit = {},
    /** 자동 분류 ("확인 n" / "제안 n" 배지) */
    autoCategoryBadge: String? = null,
    autoCategoryClick: () -> Unit = {},
    /** 설정 › 알림 (42번 §6) */
    reminderCount: Int = 0,
    remindersClick: () -> Unit = {},
    revisitCardVisible: Boolean = true,
    revisitCardEnabled: Boolean = true,
    onRevisitCardChange: (Boolean) -> Unit = {},
    weeklyVisible: Boolean = true,
    weeklyEnabled: Boolean = false,
    onWeeklyChange: (Boolean) -> Unit = {},
    notifBlockedVisible: Boolean = false,
    onOpenNotifSettings: () -> Unit = {},
) {
    val colors = AppTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 앱바: ← 설정
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 8.dp, end = AppDimens.Gutter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = popBackStack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "설정",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
            }
        }

        // 개발자 응원하기: 노출하지 않음. TODO: 통신판매업 신고 후 billing 복원 시 다시 노출 (AppNavigation 의 SUPPORT 라우트 등록도 함께 해제)

        // 화면
        item { SectionLabel("화면", isFirst = true) }
        item {
            SegmentRow(
                icon = Icons.Outlined.Palette,
                label = "화면 모드",
                options = listOf(
                    ThemeMode.SYSTEM to R.string.theme_mode_system,
                    ThemeMode.LIGHT to R.string.theme_mode_light,
                    ThemeMode.DARK to R.string.theme_mode_dark,
                ),
                selected = themeMode,
                onSelect = onThemeModeChange,
            )
        }
        item {
            SegmentRow(
                icon = Icons.Outlined.ViewAgenda,
                label = stringResource(R.string.main_view_mode),
                options = ListViewMode.entries.map { it to it.labelRes },
                selected = listViewMode,
                onSelect = onListViewModeChange,
            )
        }

        // 알림 ("화면"과 "데이터" 사이, 42번 §6)
        item { SectionLabel(stringResource(R.string.setting_section_notif)) }
        item {
            SettingRow(
                icon = Icons.Outlined.Alarm,
                label = stringResource(R.string.setting_reminders),
                onClick = remindersClick,
                selected = selectedRow == SettingPaneRow.REMINDERS,
                value = if (reminderCount > 0) stringResource(R.string.setting_reminders_count, reminderCount)
                    else stringResource(R.string.setting_reminders_none),
            )
        }
        if (revisitCardVisible) {
            item {
                SettingSwitchRow(
                    icon = Icons.Outlined.Schedule,
                    label = stringResource(R.string.setting_revisit_card),
                    subtitle = stringResource(R.string.setting_revisit_card_sub),
                    checked = revisitCardEnabled,
                    onCheckedChange = onRevisitCardChange,
                )
            }
        }
        if (weeklyVisible) {
            item {
                SettingSwitchRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = stringResource(R.string.setting_weekly),
                    subtitle = stringResource(R.string.setting_weekly_sub),
                    checked = weeklyEnabled,
                    onCheckedChange = onWeeklyChange,
                )
            }
        }
        if (notifBlockedVisible) {
            item {
                SettingRow(
                    icon = Icons.Outlined.NotificationsOff,
                    label = stringResource(R.string.setting_notif_blocked),
                    subtitle = stringResource(R.string.notif_open_settings),
                    onClick = onOpenNotifSettings,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.remind_local_only), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
        }

        // 데이터
        item { SectionLabel("데이터") }
        // 폰에는 카테고리 편집 행이 없으므로 이 행이 폰에서도 카테고리 편집(잠금 토글)으로 가는 길이 된다
        item {
            SettingRow(
                icon = Icons.Outlined.Lock,
                label = stringResource(R.string.setting_locked_categories),
                onClick = lockedCategoriesClick,
                selected = selectedRow == SettingPaneRow.LOCKED_CATEGORIES,
                value = if (lockedCategoryCount > 0) stringResource(R.string.setting_locked_categories_count, lockedCategoryCount)
                    else stringResource(R.string.setting_locked_categories_none),
            )
        }
        if (categoryEditClick != null) {
            item {
                SettingRow(
                    icon = Icons.AutoMirrored.Outlined.Label, label = "카테고리 편집", onClick = categoryEditClick,
                    selected = selectedRow == SettingPaneRow.CATEGORY_EDIT,
                )
            }
        }
        // 카테고리 편집(2분할) 바로 아래, 휴지통 위 (36번 §5.1)
        item {
            SettingRow(
                icon = Icons.Outlined.Bolt,
                label = stringResource(R.string.autocat_setting_row),
                onClick = autoCategoryClick,
                selected = selectedRow == SettingPaneRow.AUTO_CATEGORY,
                badge = autoCategoryBadge,
            )
        }
        item { SettingRow(icon = Icons.Outlined.Delete, label = "휴지통", onClick = trashClick, selected = selectedRow == SettingPaneRow.TRASH) }
        // 클라우드 백업을 껐으므로(링크는 이 폰에만) 폰을 바꿀 땐 이 파일로 옮겨야 한다는 걸 행에서 바로 알린다
        item {
            SettingRow(
                icon = Icons.Outlined.FileDownload, label = "데이터 백업 (CSV)", onClick = exportCsvClick,
                subtitle = stringResource(R.string.setting_backup_subtitle),
            )
        }
        if (importEnabled) {
            item {
                SettingRow(
                    icon = Icons.Outlined.MoveToInbox,
                    label = stringResource(R.string.import_setting_title),
                    subtitle = stringResource(R.string.import_setting_subtitle),
                    onClick = importClick,
                    selected = selectedRow == SettingPaneRow.IMPORT,
                    badge = if (importNew) stringResource(R.string.tutorial_new_badge) else null,
                )
            }
        } else {
            item { SettingRow(icon = Icons.Outlined.FileUpload, label = "데이터 가져오기 (CSV)", onClick = importCsvClick) }
        }

        // 앱 정보
        item { SectionLabel("앱 정보") }
        item {
            SettingRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook, label = "사용 방법 (튜토리얼)", onClick = tutorialClick,
                selected = selectedRow == SettingPaneRow.TUTORIAL,
                badge = if (tutorialNewCount > 0) stringResource(R.string.tutorial_new_count, tutorialNewCount) else null,
            )
        }
        item { SettingRow(icon = Icons.Outlined.Share, label = "친구에게 추천하기", onClick = shareMyApp) }
        item { SettingRow(icon = Icons.Outlined.BugReport, label = "앱 오류 신고", onClick = emailClick) }
        item {
            VersionRow(
                currentVersion = currentAppVersion,
                isUpdatable = isUpdatable,
                onClick = updateClick
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, isFirst: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(
            start = AppDimens.Gutter,
            end = AppDimens.Gutter,
            top = if (isFirst) 12.dp else AppDimens.SectionSpacing,
            bottom = 8.dp
        )
    )
}

@Composable
private fun SettingRowContainer(
    onClick: (() -> Unit)?,
    selected: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.selectedSettingRow() else Modifier)
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = singleClick { onClick() }) else Modifier)
            .padding(horizontal = AppDimens.Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** 2분할에서 오른쪽에 열린 항목: 좌우 8dp 안쪽 accentTint 라운드 배경 + 왼쪽 3dp 강조 막대 (Stitch 8·9번) */
@Composable
private fun Modifier.selectedSettingRow(): Modifier {
    val colors = AppTheme.colors
    return drawBehind {
        val inset = 8.dp.toPx()
        val radius = 12.dp.toPx()
        drawRoundRect(colors.accentTint, Offset(inset, 0f), Size(size.width - inset * 2, size.height), CornerRadius(radius, radius))
        drawRoundRect(colors.accent, Offset(inset, radius / 2), Size(3.dp.toPx(), size.height - radius), CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()))
    }
}

@Composable
private fun RowIconLabel(icon: ImageVector, label: String, modifier: Modifier = Modifier, selected: Boolean = false, subtitle: String? = null) {
    val colors = AppTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = if (selected) FontWeight.SemiBold else null,
                color = if (selected) colors.accent else colors.textPrimary,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    /** 오른쪽 화살표 앞 작은 알약 (예: "새 기능 2") */
    badge: String? = null,
    /** 오른쪽 화살표 앞 회색 값 (예: "2개") */
    value: String? = null,
    /** 제목 아래 회색 한 줄 (예: "카톡 대화 · 북마크 · 백업 파일") */
    subtitle: String? = null,
) {
    SettingRowContainer(onClick = onClick, selected = selected) {
        RowIconLabel(icon = icon, label = label, modifier = Modifier.weight(1f), selected = selected, subtitle = subtitle)
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp)
            )
        }
        if (badge != null) {
            NewPill(text = badge, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            tint = if (selected) AppTheme.colors.accent else AppTheme.colors.textSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 라벨(+ 2줄까지 부제) + 오른쪽 스위치. 행 전체를 눌러도 바뀐다 */
@Composable
private fun SettingSwitchRow(icon: ImageVector, label: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    SettingRowContainer(onClick = { onCheckedChange(!checked) }) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = colors.textPrimary, maxLines = 1)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textSecondary, maxLines = 2)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 라벨 + 오른쪽 알약형 세그먼트 버튼 (화면 모드 / 보기 방식) */
@Composable
private fun <T> SegmentRow(
    icon: ImageVector,
    label: String,
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = AppTheme.colors
    SettingRowContainer(onClick = null) {
        RowIconLabel(icon = icon, label = label, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.outline, CircleShape)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { (value, labelRes) ->
                SegmentItem(
                    labelRes = labelRes,
                    isSelected = value == selected,
                    onClick = { onSelect(value) }
                )
            }
        }
    }
}

@Composable
private fun SegmentItem(@StringRes labelRes: Int, isSelected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Text(
        text = stringResource(labelRes),
        fontSize = 13.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (isSelected) colors.onAccent else colors.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) colors.accent else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun VersionRow(currentVersion: String, isUpdatable: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    SettingRowContainer(onClick = onClick) {
        RowIconLabel(icon = Icons.Outlined.Info, label = "버전 정보", modifier = Modifier.weight(1f))
        Text(
            text = currentVersion,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        if (isUpdatable) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "업데이트",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accentTint)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 2분할 설정의 오른쪽 기본 화면 (아무 하위 화면도 열지 않았을 때, 확정 사항 5):
 * 화면 모드·보기 방식을 크게 보여준다. 값은 왼쪽 목록의 세그먼트와 같은 상태(SharedViewModel)다.
 */
@Composable
fun DisplaySettingsPanel(
    sharedViewModel: SharedViewModel = hiltViewModel(LocalActivity.current as MainActivity),
) {
    val themeMode by sharedViewModel.themeMode.collectAsState()
    val listViewMode by sharedViewModel.listViewMode.collectAsState()
    DisplaySettingsPanelContent(
        themeMode = themeMode ?: ThemeMode.SYSTEM,
        onThemeModeChange = { sharedViewModel.setThemeMode(it) },
        listViewMode = listViewMode,
        onListViewModeChange = { sharedViewModel.setListViewMode(it) },
    )
}

@Composable
fun DisplaySettingsPanelContent(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    listViewMode: ListViewMode,
    onListViewModeChange: (ListViewMode) -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 24.dp)
    ) {
        Text("화면", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(20.dp))
        LargeSegmentCard(
            icon = Icons.Outlined.Palette,
            label = "화면 모드",
            options = listOf(
                ThemeMode.SYSTEM to R.string.theme_mode_system,
                ThemeMode.LIGHT to R.string.theme_mode_light,
                ThemeMode.DARK to R.string.theme_mode_dark,
            ),
            selected = themeMode,
            onSelect = onThemeModeChange,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LargeSegmentCard(
            icon = Icons.Outlined.ViewAgenda,
            label = stringResource(R.string.main_view_mode),
            options = ListViewMode.entries.map { it to it.labelRes },
            selected = listViewMode,
            onSelect = onListViewModeChange,
        )
    }
}

@Composable
private fun <T> LargeSegmentCard(
    icon: ImageVector,
    label: String,
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background)
            .then(if (colors.isDark) Modifier.border(1.dp, colors.outline, shape) else Modifier)
            .padding(20.dp)
    ) {
        RowIconLabel(icon = icon, label = label)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, labelRes) ->
                val isSelected = value == selected
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) colors.onAccent else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .clickable { onSelect(value) }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun AppSettingScreenPreview() {
    SaveUrlTheme(darkTheme = false) {
        AppSettingScreen(isUpdatable = true, themeMode = ThemeMode.LIGHT)
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
fun AppSettingScreenDarkPreview() {
    SaveUrlTheme(darkTheme = true) {
        AppSettingScreen(isUpdatable = false, themeMode = ThemeMode.DARK, tutorialNewCount = 2)
    }
}
