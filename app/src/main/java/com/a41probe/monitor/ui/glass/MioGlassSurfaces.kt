package com.a41probe.monitor.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a41probe.monitor.ui.theme.Ink
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/* ============================================================
 * 玻璃表面：材质 = 背景实时模糊 + 半透明着色 + 高光描边 + 镜面反光
 *
 * 真正的"液态玻璃"由四层构成：
 *   1) hazeBlur：对背景层（卡片=环境光/立绘；壳层=滚动内容）做真实模糊；
 *   2) 半透明填充 + 主题色 tint：模糊之上再压一层材质色，保证文字对比度；
 *   3) rim：1dp 顶部高光 / 底部暗边的竖向渐变描边（玻璃厚度感）；
 *   4) sheen：左上到右下的一层柔和高光（空气感/液态感）。
 * API < 31 时 haze 不产生模糊（引擎自动退化为半透明），材质其余部分保持一致。
 * ============================================================ */

@Composable
fun MioGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    role: MioGlassRole,
    strength: Float = 1f,
    /** 非空时按顶栏滚动进度做渐强（0=通透，1=完全雾化）；顶栏/dock 渐变使用。 */
    progress: Float? = null,
    /** 覆盖角色默认的采样源（如引导页覆盖层要采样内容层而非背景层）。 */
    sampling: MioGlassSampling? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalMioGlass.current
    val capability = currentGlassCapability()
    val dark = Ink.bg.luminance() < 0.5f
    val baseGeometry = remember(role, capability, dark, strength) {
        resolveGlassGeometry(role, capability, dark, strength)
    }
    val geometry = remember(baseGeometry, progress) {
        if (progress == null) {
            baseGeometry
        } else {
            baseGeometry.copy(
                blurRadius = resolveChromeBlurRadius(baseGeometry.blurRadius, progress),
                fillAlpha = resolveChromeFillAlpha(baseGeometry.fillAlpha, progress),
            )
        }
    }
    val fill = Ink.glassFill
    val tint = Ink.glassTint

    val hazeState = when (sampling ?: when (role) {
        MioGlassRole.CARD -> MioGlassSampling.PAGE
        MioGlassRole.CHROME, MioGlassRole.DOCK, MioGlassRole.INDICATOR -> MioGlassSampling.CHROME
        MioGlassRole.SHEET -> null
    }) {
        MioGlassSampling.PAGE -> host?.pageState
        MioGlassSampling.CHROME -> host?.chromeState
        null -> null // 弹层独立窗口：靠 ModalWindowBlurBehindEffect
    }

    val useHaze = host != null && hazeState != null &&
        geometry.hazeSampling && capability.realtimeBlur && geometry.blurRadius > 0.dp

    val style = remember(role, geometry, fill, tint, dark) {
        val base = HazeBlurStyle {
            blurEnabled(geometry.blurRadius > 0.dp)
            blurRadius(geometry.blurRadius)
            noiseFactor(geometry.noiseFactor)
            // 无模糊能力时的兜底底色
            backgroundColor(fill)
            val effects = buildList {
                add(HazeColorEffect.tint(fill.copy(alpha = geometry.fillAlpha.coerceIn(0f, 1f))))
                if (geometry.tintAlpha > 0.001f) {
                    add(HazeColorEffect.tint(tint.copy(alpha = geometry.tintAlpha)))
                }
            }
            colorEffects(effects)
        }
        if (role == MioGlassRole.CHROME || role == MioGlassRole.DOCK) {
            // 顶栏/dock：顶部实、底部渐隐，避免硬边
            base.then {
                progressive(
                    HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0.35f,
                    )
                )
            }
        } else {
            base
        }
    }

    var surface = modifier
    if (geometry.shadowElevation > 0.dp) {
        surface = surface.shadow(
            elevation = geometry.shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = Ink.glassShadow.copy(alpha = geometry.shadowAlpha),
            spotColor = Ink.glassShadow.copy(alpha = geometry.shadowAlpha),
        )
    }
    surface = surface.clip(shape)
    surface = if (useHaze) {
        surface.hazeBlur(
            input = HazeInput.Sources(hazeState!!),
            style = style,
            performanceMode = when (role) {
                MioGlassRole.CARD -> HazePerformanceMode.Performance
                MioGlassRole.INDICATOR -> HazePerformanceMode.Balanced
                else -> HazePerformanceMode.Quality
            },
        )
    } else {
        val fillAlpha = geometry.fillAlpha.coerceIn(0f, 1f)
        var fallback = surface.background(fill.copy(alpha = fillAlpha))
        if (role == MioGlassRole.INDICATOR) {
            fallback = fallback.background(Ink.accentSoft)
        } else if (geometry.tintAlpha > 0.001f) {
            fallback = fallback.background(tint.copy(alpha = geometry.tintAlpha))
        }
        fallback
    }
    // 玻璃厚度感：顶部高光 / 底部暗边
    surface = surface.border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Ink.glassRimLight.copy(alpha = geometry.rimLightAlpha),
                Ink.glassRimShade,
            ),
        ),
        shape = shape,
    )
    // 空气感/液态感：左上→右下的柔和高光
    surface = surface.mioGlassSheen(geometry.sheenAlpha)
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        surface = surface.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    }

    Box(surface, content = content)
}

/** 圆形玻璃（返回按钮/图标按钮）。 */
@Composable
fun MioGlassCircle(
    modifier: Modifier = Modifier,
    role: MioGlassRole = MioGlassRole.CHROME,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    MioGlassSurface(
        modifier = modifier.size(size),
        shape = CircleShape,
        role = role,
        onClick = onClick,
        content = content,
    )
}

/** 弹层表面（bottom sheet / dialog）：模糊由 ModalWindowBlurBehindEffect 提供。 */
@Composable
fun MioGlassSheetSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable BoxScope.() -> Unit,
) {
    MioGlassSurface(
        modifier = modifier,
        shape = shape,
        role = MioGlassRole.SHEET,
        content = content,
    )
}

/** 左上→右下的一层柔和白色高光（镜面/液态质感，压在内容之上但很淡）。 */
fun Modifier.mioGlassSheen(alpha: Float): Modifier {
    if (alpha <= 0.001f) return this
    return this.drawWithCache {
        val brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = alpha),
            0.42f to Color.White.copy(alpha = alpha * 0.18f),
            0.75f to Color.Transparent,
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.9f, size.height * 1.1f),
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    }
}

/** 玻璃-lite 高光（按钮/chip 用，不额外开模糊层）：顶部白边 + 轻微内高光。 */
fun Modifier.mioGlassGloss(
    shape: Shape,
    lightAlpha: Float = 0.45f,
    sheenAlpha: Float = 0.10f,
): Modifier = this
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = lightAlpha), Color.White.copy(alpha = 0.02f)),
        ),
        shape = shape,
    )
    .mioGlassSheen(sheenAlpha)
