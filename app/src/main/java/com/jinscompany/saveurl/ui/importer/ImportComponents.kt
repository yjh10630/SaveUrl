package com.jinscompany.saveurl.ui.importer

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 가져오기 화면들이 함께 쓰는 작은 부품 (34번 §3 공통 규칙)

internal fun fmtCount(n: Int): String = NumberFormat.getInstance(Locale.KOREA).format(n)

/** 목록 행 날짜 `yyyy.M.d` */
internal fun fmtDay(millis: Long): String = SimpleDateFormat("yyyy.M.d", Locale.KOREA).format(Date(millis))

/** 기간 `yyyy.M` */
internal fun fmtMonth(millis: Long): String = SimpleDateFormat("yyyy.M", Locale.KOREA).format(Date(millis))

/** 완료 화면 부제 `yyyy년 M월 d일` */
internal fun fmtLongDay(millis: Long): String = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(Date(millis))

/** 기록 행 `M월 d일` */
internal fun fmtShortDay(millis: Long): String = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(millis))

internal fun fmtSize(bytes: Long?): String? = bytes?.let {
    when {
        it >= 1024 * 1024 -> String.format(Locale.KOREA, "%.1f MB", it / 1024.0 / 1024.0)
        it >= 1024 -> "${it / 1024} KB"
        else -> "$it B"
    }
}

/** 지금 연결이 데이터 요금 네트워크(모바일 데이터)인지. 연결이 없으면 true (채우기를 기다림) */
internal fun isMeteredNow(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    cm.activeNetwork == null || cm.isActiveNetworkMetered
}.getOrDefault(true)

/** 숫자 흔들림 방지 (TextStyle.fontFeatureSettings) */
internal const val TABULAR_NUMBERS = "tnum"

/** 가로 폭이 넓을 때(2분할 오른쪽 패널 600dp 이상) 내용 최대 폭 560dp, 가운데 */
@Composable
internal fun ImportContentWidth(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), content = content)
    }
}

/** 화면 하단 고정 영역: 버튼(들) + 광고 위 16dp */
@Composable
internal fun ImportBottomArea(content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth().background(colors.background)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(start = AppDimens.Gutter, end = AppDimens.Gutter, top = 8.dp, bottom = 16.dp),
                content = content,
            )
        }
        AdBannerBar()
    }
}

/** 자물쇠 + 회색 한 줄 (개인정보 안내) */
@Composable
internal fun PrivacyLine(text: String, modifier: Modifier = Modifier, boxed: Boolean = false) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (boxed) Modifier.clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(12.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textSecondary)
    }
}

/** 파일 칩: 아이콘 + 이름 · 크기 */
@Composable
internal fun FileChip(file: ImportFileInfo, icon: ImageVector, extra: String? = null) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        val meta = listOfNotNull(file.name, fmtSize(file.size), extra).joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 틴트 원 안의 아이콘 (가져오는 중·완료·오류) */
@Composable
internal fun CircleIcon(icon: ImageVector, size: Dp = 72.dp, tinted: Boolean = true) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(if (tinted) colors.accentTint else colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (tinted) colors.accent else colors.textSecondary, modifier = Modifier.size(size * 0.45f))
    }
}

/** 판정 배지 (알약) */
@Composable
internal fun FormatBadge(icon: ImageVector, text: String) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.clip(CircleShape).background(colors.accentTint).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = colors.accent, fontWeight = FontWeight.Medium)
    }
}

/** 섹션 라벨 + 오른쪽 텍스트 버튼 */
@Composable
internal fun SectionHeader(label: String, action: String? = null, onAction: () -> Unit = {}) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = singleClick { onAction() }).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** 회색 둥근 묶음 (#F5F6F8 rounded 16dp) */
@Composable
internal fun SurfaceGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.surface),
        content = content,
    )
}

/** 테두리 버튼 (되돌리기·취소) */
@Composable
internal fun OutlineActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 52.dp) {
    val colors = AppTheme.colors
    OutlinedButton(
        onClick = singleClick { onClick() },
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/** 강조 테두리 카드 (모두 이미 있음 등) */
@Composable
internal fun AccentNote(text: String) {
    val colors = AppTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.accent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.accentTint)
            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    )
}

@Composable
internal fun VSpace(dp: Int) = Spacer(modifier = Modifier.height(dp.dp))

/**
 * Scaffold topBar 칸은 상태바 여백을 받지 않으므로 직접 띄운다
 * (다른 화면은 앱바를 본문 Column 안에 두어 Scaffold 본문 여백을 받는다)
 */
@Composable
internal fun StatusBarSpaced(content: @Composable () -> Unit) {
    Box(modifier = Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)) { content() }
}
