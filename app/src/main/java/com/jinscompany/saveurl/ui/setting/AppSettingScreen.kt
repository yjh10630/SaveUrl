package com.jinscompany.saveurl.ui.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.jinscompany.saveurl.ui.navigation.navigateToStaticWeb
import com.jinscompany.saveurl.ui.navigation.navigateToTrash
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.getCurrentAppVersion
import com.jinscompany.saveurl.utils.tutorialUrl
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 2분할 설정에서 오른쪽 패널에 열 수 있는 하위 화면 (왼쪽 목록의 선택 행) */
enum class SettingPaneRow { CATEGORY_EDIT, TRASH, TUTORIAL }

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
) {
    val themeMode by sharedViewModel.themeMode.collectAsState()
    val listViewMode by sharedViewModel.listViewMode.collectAsState()
    val context = LocalContext.current
    val isFlexibleUpdatable by sharedViewModel.isFlexibleUpdatable.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { settingViewModel.exportCsv(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                exportLauncher.launch("savelink_backup_$dateStr.csv")
            },
            importCsvClick = {
                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
            },
            shareMyApp = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=${context.packageName}")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, ""))
            },
            updateClick = {
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                try {
                    context.startActivity(playStoreIntent)
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                    )
                }
            },
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
            trashClick = {
                if (isListPane) navController.navigateInSettingsPane(Navigation.Routes.TRASH)
                else navController.navigateToTrash()
            },
            tutorialClick = {
                val encodedUrl = URLEncoder.encode(tutorialUrl, "UTF-8")
                if (isListPane) navController.navigateInSettingsPane("${Navigation.Routes.STATIC_WEB}?url=$encodedUrl")
                else navController.navigateToStaticWeb(encodedUrl)
            },
            // 카테고리 편집 행은 2분할에서만 둔다 (폰 설정 화면은 그대로: 카테고리 편집은 필터·카테고리 선택 시트에서 진입)
            categoryEditClick = if (isListPane) {
                { navController.navigateInSettingsPane(Navigation.Routes.EDIT_CATEGORY) }
            } else null,
            selectedRow = if (!isListPane) null else when (selectedPaneRoute) {
                Navigation.Routes.EDIT_CATEGORY -> SettingPaneRow.CATEGORY_EDIT
                Navigation.Routes.TRASH -> SettingPaneRow.TRASH
                Navigation.Routes.STATIC_WEB -> SettingPaneRow.TUTORIAL
                else -> null
            },
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

        // 데이터
        item { SectionLabel("데이터") }
        if (categoryEditClick != null) {
            item {
                SettingRow(
                    icon = Icons.AutoMirrored.Outlined.Label, label = "카테고리 편집", onClick = categoryEditClick,
                    selected = selectedRow == SettingPaneRow.CATEGORY_EDIT,
                )
            }
        }
        item { SettingRow(icon = Icons.Outlined.Delete, label = "휴지통", onClick = trashClick, selected = selectedRow == SettingPaneRow.TRASH) }
        item { SettingRow(icon = Icons.Outlined.FileDownload, label = "데이터 백업 (CSV)", onClick = exportCsvClick) }
        item { SettingRow(icon = Icons.Outlined.FileUpload, label = "데이터 가져오기 (CSV)", onClick = importCsvClick) }

        // 앱 정보
        item { SectionLabel("앱 정보") }
        item {
            SettingRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook, label = "사용 방법 (튜토리얼)", onClick = tutorialClick,
                selected = selectedRow == SettingPaneRow.TUTORIAL,
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
private fun RowIconLabel(icon: ImageVector, label: String, modifier: Modifier = Modifier, selected: Boolean = false) {
    val colors = AppTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    SettingRowContainer(onClick = onClick, selected = selected) {
        RowIconLabel(icon = icon, label = label, modifier = Modifier.weight(1f), selected = selected)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            tint = if (selected) AppTheme.colors.accent else AppTheme.colors.textSecondary,
            modifier = Modifier.size(22.dp)
        )
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
        AppSettingScreen(isUpdatable = false, themeMode = ThemeMode.DARK)
    }
}
