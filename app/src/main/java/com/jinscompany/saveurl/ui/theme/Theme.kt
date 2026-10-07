package com.jinscompany.saveurl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.jinscompany.saveurl.domain.model.ThemeMode

private fun AppColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    // 시트·메뉴 등 Material 컴포넌트가 쓰는 surfaceContainer 계열: 라이트는 흰 바탕, 다크는 한 단계 올라온 면
    val container = if (isDark) surface else background
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentTint,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = textSecondary,
        onSecondary = background,
        secondaryContainer = accentTint,
        onSecondaryContainer = accent,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        surfaceTint = background,
        surfaceBright = container,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = container,
        surfaceContainer = container,
        surfaceContainerHigh = container,
        surfaceContainerHighest = surface,
        outline = outline,
        outlineVariant = outline,
        error = danger,
        onError = background,
    )
}

private val LightColorScheme = LightAppColors.toColorScheme()
private val DarkColorScheme = DarkAppColors.toColorScheme()

@Composable
fun ThemeMode.isDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * 앱 테마. 고정 브랜드 강조색(#3D6BF5 / 다크 #7B9BFF)을 사용하며 동적 색상은 쓰지 않는다.
 */
@Composable
fun SaveUrlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = MaterialShapes,
            content = content
        )
    }
}

/** 화면 코드에서 디자인 토큰에 접근하는 진입점 */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}
