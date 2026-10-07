package com.jinscompany.saveurl.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// 브랜드 팔레트 (Google Stitch "SaveURL 리디자인 (심플)" 디자인 시스템)
// 강조색은 하나만 사용한다. 동적 색상(Material You)은 사용하지 않는다.
// ---------------------------------------------------------------------------------------------
internal object Palette {
    // Light
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFF5F6F8)
    val LightTextPrimary = Color(0xFF111827)
    val LightTextSecondary = Color(0xFF6B7280)
    val LightOutline = Color(0xFFE5E7EB)
    val LightAccent = Color(0xFF3D6BF5)
    val LightOnAccent = Color(0xFFFFFFFF)
    val LightAccentTint = Color(0xFFEEF2FF)
    val LightDanger = Color(0xFFD14343)

    // Dark
    val DarkBackground = Color(0xFF0F1115)
    val DarkSurface = Color(0xFF1A1D23)
    val DarkTextPrimary = Color(0xFFF3F4F6)
    val DarkTextSecondary = Color(0xFF9CA3AF)
    val DarkOutline = Color(0xFF2A2E35)
    val DarkAccent = Color(0xFF7B9BFF)
    val DarkOnAccent = Color(0xFF0F1115)
    // accent(#7B9BFF) 15% 를 배경(#0F1115) 위에 합성한 불투명 색
    val DarkAccentTint = Color(0xFF1F2638)
    // 어두운 배경 대비를 위해 같은 계열에서 밝기만 올림
    val DarkDanger = Color(0xFFF07171)
}

/**
 * 앱 전용 의미 기반 색상 토큰. MaterialTheme.colorScheme 에 없는 토큰(accentTint, danger 등)을 포함해
 * 화면 코드에서는 이 토큰을 우선 사용한다. ([AppTheme.colors])
 */
@Immutable
data class AppColors(
    val background: Color,
    /** 입력칸, 미리보기 카드, 선택되지 않은 칩, 광고 슬롯 등 배경보다 한 단계 올라온 면 */
    val surface: Color,
    val textPrimary: Color,
    /** 도메인, 날짜, 라벨 등 보조 텍스트 */
    val textSecondary: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    /** 선택 칩 배경, 빈 상태 원형 배경 등 */
    val accentTint: Color,
    /** 휴지통 이동/삭제 등 위험 동작 (텍스트·아이콘에만 사용) */
    val danger: Color,
    val isDark: Boolean,
)

internal val LightAppColors = AppColors(
    background = Palette.LightBackground,
    surface = Palette.LightSurface,
    textPrimary = Palette.LightTextPrimary,
    textSecondary = Palette.LightTextSecondary,
    outline = Palette.LightOutline,
    accent = Palette.LightAccent,
    onAccent = Palette.LightOnAccent,
    accentTint = Palette.LightAccentTint,
    danger = Palette.LightDanger,
    isDark = false,
)

internal val DarkAppColors = AppColors(
    background = Palette.DarkBackground,
    surface = Palette.DarkSurface,
    textPrimary = Palette.DarkTextPrimary,
    textSecondary = Palette.DarkTextSecondary,
    outline = Palette.DarkOutline,
    accent = Palette.DarkAccent,
    onAccent = Palette.DarkOnAccent,
    accentTint = Palette.DarkAccentTint,
    danger = Palette.DarkDanger,
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// ---------------------------------------------------------------------------------------------
// 레거시 토큰
// 기존 화면(저장/검색/휴지통/카테고리 편집 등)이 참조하던 상수를 테마를 따르는 getter 로 바꿨다.
// 라이트/다크 전환 시 아직 리디자인되지 않은 화면도 읽을 수 있는 색이 되도록 하기 위함이다.
// 신규 코드는 AppTheme.colors 를 직접 사용한다.
// ---------------------------------------------------------------------------------------------
val AppBackground: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
val AppSurface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
val AppSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
val AppPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
val AppOnPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onAccent
val AppTextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val AppTextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
val AppDivider: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.outline
val AppDanger: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.danger
val AppChipSelected: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
val AppChipUnselected: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
/** 예전 갈색 버튼색. 강조색 하나로 통일하기 위해 accent 로 매핑 */
val Brown: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
