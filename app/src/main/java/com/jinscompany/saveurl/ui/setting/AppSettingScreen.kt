package com.jinscompany.saveurl.ui.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.navigation.navigateToTrash
import com.jinscompany.saveurl.ui.navigation.navigateToStaticWeb
import com.jinscompany.saveurl.ui.navigation.navigateToSupport
import com.jinscompany.saveurl.ui.theme.AppBackground
import com.jinscompany.saveurl.ui.theme.AppChipSelected
import com.jinscompany.saveurl.ui.theme.AppChipUnselected
import com.jinscompany.saveurl.ui.theme.AppDivider
import com.jinscompany.saveurl.ui.theme.AppPrimary
import com.jinscompany.saveurl.ui.theme.AppSurface
import com.jinscompany.saveurl.ui.theme.AppTextPrimary
import com.jinscompany.saveurl.ui.theme.AppTextSecondary
import com.jinscompany.saveurl.utils.getCurrentAppVersion
import com.jinscompany.saveurl.utils.tutorialUrl
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppSettingScreen(
    navController: NavHostController,
    sharedViewModel: SharedViewModel = hiltViewModel(LocalActivity.current as MainActivity),
    settingViewModel: AppSettingViewModel = hiltViewModel(),
) {
    val darkModePref by sharedViewModel.darkModeEnabled.collectAsState()
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

    Scaffold { paddingValue ->
        AppSettingScreen(
            paddingValues = paddingValue,
            popBackStack = { navController.popBackStack() },
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
            trashClick = { navController.navigateToTrash() },
            tutorialClick = {
                val encodedUrl = URLEncoder.encode(tutorialUrl, "UTF-8")
                navController.navigateToStaticWeb(encodedUrl)
            },
            currentAppVersion = getCurrentAppVersion(context),
            isUpdatable = isFlexibleUpdatable,
            darkModePref = darkModePref,
            onDarkModeChange = { sharedViewModel.setDarkMode(it) },
            onSupportClick = { navController.navigateToSupport() },
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
    darkModePref: Boolean? = null,
    onDarkModeChange: (Boolean?) -> Unit = {},
    isUpdatable: Boolean = false,
    onSupportClick: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(AppBackground),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // 헤더
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = popBackStack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppTextSecondary,
                    )
                }
                Text(
                    text = "설정",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTextPrimary
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // 개발자 응원하기 섹션 (통신판매업 신고 후 활성화)
        // item { SectionLabel("개발자 응원하기") }
        // item {
        //     SettingCard {
        //         SettingRow(
        //             icon = Icons.Default.Favorite,
        //             label = "개발자 응원하기",
        //             description = if (isAdsRemoved) "후원 · 광고 제거됨 ✓" else "후원 · 광고 제거",
        //             onClick = onSupportClick
        //         )
        //     }
        // }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // 앱 정보 섹션
        item { SectionLabel("앱 정보") }
        item {
            SettingCard {
                SettingRow(
                    icon = Icons.Default.MenuBook,
                    label = "튜토리얼",
                    onClick = tutorialClick
                )
                SettingDivider()
                SettingRow(
                    icon = Icons.Default.GroupAdd,
                    label = "친구 초대",
                    onClick = shareMyApp
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // 데이터 섹션
        item { SectionLabel("데이터") }
        item {
            SettingCard {
                SettingRow(
                    icon = Icons.Default.Delete,
                    label = "휴지통",
                    onClick = trashClick
                )
                SettingDivider()
                SettingRow(
                    icon = Icons.Default.Backup,
                    label = "데이터 백업 (CSV)",
                    onClick = exportCsvClick
                )
                SettingDivider()
                SettingRow(
                    icon = Icons.Default.Download,
                    label = "데이터 가져오기 (CSV)",
                    onClick = importCsvClick
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // 화면 설정 섹션
        item { SectionLabel("화면 설정") }
        item {
            SettingCard {
                DarkModeRow(darkModePref = darkModePref, onChange = onDarkModeChange)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // 기타 섹션
        item { SectionLabel("기타") }
        item {
            SettingCard {
                SettingRow(
                    icon = Icons.Default.BugReport,
                    label = "앱 오류 신고",
                    onClick = emailClick
                )
                SettingDivider()
                VersionRow(
                    currentVersion = currentAppVersion,
                    isUpdatable = isUpdatable,
                    onClick = updateClick
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppTextSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppSurface,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        color = AppDivider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = singleClick { onClick() })
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = AppTextPrimary
                )
                if (description != null) {
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = AppTextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            tint = AppTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DarkModeRow(darkModePref: Boolean?, onChange: (Boolean?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "화면 모드",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = AppTextPrimary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("시스템" to null, "다크" to true, "라이트" to false).forEach { (label, value) ->
                val selected = darkModePref == value
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) AppChipSelected else AppChipUnselected,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onChange(value) }
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else AppTextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(currentVersion: String, isUpdatable: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = singleClick { onClick() })
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "버전 정보",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = AppTextPrimary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currentVersion,
                fontSize = 14.sp,
                color = AppTextSecondary
            )
            if (isUpdatable) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.Autorenew,
                    contentDescription = "업데이트 가능",
                    tint = Color(0xFFFF453A),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
fun AppSettingScreenPreview() {
    AppSettingScreen(isUpdatable = true)
}
