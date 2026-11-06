package com.a41probe.monitor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme as materialDarkColorScheme
import androidx.compose.material3.lightColorScheme as materialLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.a41probe.monitor.ui.components.GlassConfig
import com.a41probe.monitor.ui.components.GlassLevel
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.BiliMiuixTypography
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.LocalCornerRadiusScale
import com.android.purebilibili.core.theme.LocalDynamicColorActive
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import com.android.purebilibili.core.theme.resolveCornerRadiusScale
import com.android.purebilibili.core.theme.resolveMaterialMotionScheme
import com.android.purebilibili.core.theme.resolveMaterialShapes
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppIconStyle
import com.android.purebilibili.core.ui.LocalAppListItemStyle
import com.android.purebilibili.core.ui.LocalLiquidControlThemeColor
import com.android.purebilibili.core.ui.ProvideAppContentColors
import com.android.purebilibili.core.ui.ProvideAppThemeConfig
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.resolveAppIconStyle
import com.android.purebilibili.core.ui.resolveAppListItemStyle
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

// 数字用等宽字体保持仪器感；中文正文用系统字体
val Mono = androidx.compose.ui.text.font.FontFamily.Monospace

/* ============================================================
 * Mio 3.0 主题（BiliPai / Miuix 视觉语言）
 *
 * 同步提供四层主题环境，供搬运的 BiliPai design-system 组件使用：
 *   1) MiuixTheme   —— Miuix 原生组件（Card/Button/Switch/NavigationBar/...）
 *   2) MaterialTheme—— Material3 组件（AlertDialog/ModalBottomSheet/TextField/...）
 *   3) design-system CompositionLocal —— AppUiStyle=MIUIX、liquid glass、图标/列表样式
 *   4) Ink 颜色快照 —— Mio 旧调用点的兼容层（支持运行时明暗切换）
 *
 * 液态玻璃（Miuix blur）要求 API 33+；低版本强制关闭玻璃走 Miuix 实色表面。
 * ============================================================ */

/** 液体玻璃是否可用（Miuix blur 模块 minSdk 33，低版本降级为实色表面） */
fun liquidGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Composable
fun MioTheme(
    palette: Palette = ThemeConfig.palette(),
    darkTheme: Boolean = ThemeConfig.isDark(),
    content: @Composable () -> Unit,
) {
    val resolved = remember(palette, darkTheme) { MioColors.from(palette, darkTheme) }
    val miuixColors = remember(palette, darkTheme) { palette.toMiuixColors(darkTheme) }
    val materialScheme = remember(palette, darkTheme) { palette.toMaterialScheme(darkTheme) }
    val typography = remember { BiliMiuixTypography as Typography }
    val shapes = remember { resolveMaterialShapes(AppUiStyle.MIUIX) }
    val motionScheme = remember { resolveMaterialMotionScheme(AppUiStyle.MIUIX) }

    val glassLevel = GlassConfig.effective()
    val liquidGlassEnabled = glassLevel == GlassLevel.FULL && liquidGlassSupported()
    val glassAvailable = glassLevel != GlassLevel.OFF

    val themeConfig = remember(liquidGlassEnabled) {
        AppThemeConfig(
            blurIntensity = BlurIntensity.APPLE_DOCK,
            headerBlurEnabled = liquidGlassEnabled,
            bottomBarBlurEnabled = liquidGlassEnabled,
            progressiveTopBlurEnabled = liquidGlassEnabled,
            progressiveTopFadeEnabled = true,
            hapticFeedbackEnabled = true,
            liquidGlassEnabled = liquidGlassEnabled,
            liquidControlsUseThemeColor = true,
        )
    }

    CompositionLocalProvider(
        LocalAppUiStyle provides AppUiStyle.MIUIX,
        LocalDynamicColorActive provides false,
        LocalSettingsLiquidGlassEnabled provides liquidGlassEnabled,
        LocalLiquidControlThemeColor provides miuixColors.primary,
        LocalAppIconStyle provides resolveAppIconStyle(AppIconStyle.AUTO, AppUiStyle.MIUIX),
        LocalAppListItemStyle provides resolveAppListItemStyle(AppListItemStyle.AUTO, AppUiStyle.MIUIX),
        LocalCornerRadiusScale provides resolveCornerRadiusScale(AppUiStyle.MIUIX),
    ) {
        MiuixTheme(colors = miuixColors) {
            MaterialTheme(
                colorScheme = materialScheme,
                typography = typography,
                shapes = shapes,
                motionScheme = motionScheme,
            ) {
                ProvideAppThemeConfig(config = themeConfig) {
                    ProvideAppContentColors(
                        nativeInteractionColors = false,
                        miuixContentColors = true,
                    ) {
                        SideEffect { Ink.bind(resolved) }
                        AppSystemBars(darkTheme = darkTheme)
                        content()
                    }
                }
            }
        }
    }
}

/** 系统栏图标明暗跟随主题（沉浸式透明状态栏）。 */
@Composable
private fun AppSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

/* ---------------- 配色 → Miuix Colors ---------------- */

private fun Palette.toMiuixColors(dark: Boolean): Colors {
    val c = MioColors.from(this, dark)
    val accentContainer = lerp(c.accent, c.bg, 0.84f)
    val errorContainer = lerp(c.danger, c.card, 0.88f)
    return if (dark) {
        miuixDarkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryVariant = c.accent,
            onPrimaryVariant = c.onAccent,
            error = c.danger,
            onError = Color.White,
            errorContainer = errorContainer,
            onErrorContainer = c.danger,
            disabledPrimary = c.accent.copy(alpha = 0.38f),
            disabledOnPrimary = c.onAccent.copy(alpha = 0.6f),
            disabledPrimaryButton = c.accent.copy(alpha = 0.3f),
            disabledOnPrimaryButton = c.onAccent.copy(alpha = 0.5f),
            disabledPrimarySlider = c.accent.copy(alpha = 0.34f),
            primaryContainer = accentContainer,
            onPrimaryContainer = c.accent,
            secondary = c.panel,
            onSecondary = c.tx,
            secondaryVariant = c.panel,
            onSecondaryVariant = c.tx2,
            disabledSecondary = c.panel,
            disabledOnSecondary = c.off,
            disabledSecondaryVariant = c.panel,
            disabledOnSecondaryVariant = c.off,
            secondaryContainer = c.panel,
            onSecondaryContainer = c.tx2,
            secondaryContainerVariant = c.panel,
            onSecondaryContainerVariant = c.tx3,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = c.accent,
            tertiaryContainerVariant = accentContainer,
            background = c.bg,
            onBackground = c.tx,
            onBackgroundVariant = c.tx2,
            surface = c.card,
            onSurface = c.tx,
            surfaceVariant = c.card,
            onSurfaceSecondary = c.tx2,
            onSurfaceVariantSummary = c.tx2,
            onSurfaceVariantActions = c.tx3,
            disabledOnSurface = c.off,
            surfaceContainer = c.card,
            onSurfaceContainer = c.tx,
            onSurfaceContainerVariant = c.tx3,
            surfaceContainerHigh = c.panel,
            onSurfaceContainerHigh = c.tx2,
            surfaceContainerHighest = c.panel,
            onSurfaceContainerHighest = c.tx,
            outline = c.stroke,
            dividerLine = c.divider,
            windowDimming = Color.Black.copy(alpha = 0.5f),
            sliderKeyPoint = c.accent.copy(alpha = 0.3f),
            sliderKeyPointForeground = c.accent,
            sliderBackground = c.panel,
        )
    } else {
        miuixLightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryVariant = c.accent,
            onPrimaryVariant = c.accent.copy(alpha = 0.7f),
            error = c.danger,
            onError = Color.White,
            errorContainer = errorContainer,
            onErrorContainer = c.danger,
            disabledPrimary = c.accent.copy(alpha = 0.35f),
            disabledOnPrimary = c.onAccent.copy(alpha = 0.7f),
            disabledPrimaryButton = c.accent.copy(alpha = 0.3f),
            disabledOnPrimaryButton = c.onAccent.copy(alpha = 0.6f),
            disabledPrimarySlider = c.accent.copy(alpha = 0.34f),
            primaryContainer = accentContainer,
            onPrimaryContainer = c.accent,
            secondary = c.panel,
            onSecondary = c.tx2,
            secondaryVariant = c.panel,
            onSecondaryVariant = c.tx2,
            disabledSecondary = c.panel,
            disabledOnSecondary = c.off,
            disabledSecondaryVariant = c.panel,
            disabledOnSecondaryVariant = c.off,
            secondaryContainer = c.panel,
            onSecondaryContainer = c.tx2,
            secondaryContainerVariant = c.panel,
            onSecondaryContainerVariant = c.tx3,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = c.accent,
            tertiaryContainerVariant = accentContainer,
            background = c.bg,
            onBackground = c.tx,
            onBackgroundVariant = c.tx2,
            surface = c.card,
            onSurface = c.tx,
            surfaceVariant = c.card,
            onSurfaceSecondary = c.tx2,
            onSurfaceVariantSummary = c.tx2,
            onSurfaceVariantActions = c.tx3,
            disabledOnSurface = c.off,
            surfaceContainer = c.card,
            onSurfaceContainer = c.tx,
            onSurfaceContainerVariant = c.tx3,
            surfaceContainerHigh = c.panel,
            onSurfaceContainerHigh = c.tx2,
            surfaceContainerHighest = c.panel,
            onSurfaceContainerHighest = c.tx,
            outline = c.stroke,
            dividerLine = c.divider,
            windowDimming = Color.Black.copy(alpha = 0.35f),
            sliderKeyPoint = c.accent.copy(alpha = 0.3f),
            sliderKeyPointForeground = c.accent,
            sliderBackground = c.panel,
        )
    }
}

/* ---------------- 配色 → Material3 ColorScheme ---------------- */

private fun Palette.toMaterialScheme(dark: Boolean): ColorScheme {
    val c = MioColors.from(this, dark)
    val accentContainer = lerp(c.accent, c.bg, 0.84f)
    val onAccentContainer = c.accent
    return if (dark) {
        materialDarkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            secondary = c.panel,
            onSecondary = c.tx,
            secondaryContainer = c.panel,
            onSecondaryContainer = c.tx2,
            tertiary = c.accent2,
            onTertiary = c.onAccent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = onAccentContainer,
            background = c.bg,
            onBackground = c.tx,
            surface = c.card,
            onSurface = c.tx,
            surfaceVariant = c.panel,
            onSurfaceVariant = c.tx2,
            surfaceContainer = c.card,
            surfaceContainerHigh = c.panel,
            surfaceContainerHighest = c.panel,
            surfaceContainerLow = c.card,
            surfaceContainerLowest = c.bg,
            outline = c.stroke,
            outlineVariant = c.divider,
            error = c.danger,
            onError = Color.White,
            errorContainer = lerp(c.danger, c.card, 0.88f),
            onErrorContainer = c.danger,
            scrim = Color.Black,
        )
    } else {
        materialLightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            secondary = c.panel,
            onSecondary = c.tx2,
            secondaryContainer = c.panel,
            onSecondaryContainer = c.tx2,
            tertiary = c.accent2,
            onTertiary = c.tx,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = onAccentContainer,
            background = c.bg,
            onBackground = c.tx,
            surface = c.card,
            onSurface = c.tx,
            surfaceVariant = c.panel,
            onSurfaceVariant = c.tx2,
            surfaceContainer = c.card,
            surfaceContainerHigh = c.panel,
            surfaceContainerHighest = c.panel,
            surfaceContainerLow = c.card,
            surfaceContainerLowest = c.bg,
            outline = c.stroke,
            outlineVariant = c.divider,
            error = c.danger,
            onError = Color.White,
            errorContainer = lerp(c.danger, c.card, 0.88f),
            onErrorContainer = c.danger,
            scrim = Color.Black,
        )
    }
}
