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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
 * 玻璃表面（v3.2）：真·液态玻璃 = 低不透明度模糊 + 镜面折射边 + 高光
 *
 *  1) hazeBlur：对源层做真实背景模糊（卡片=环境光/立绘；壳层=滚动内容）；
 *  2) 单 tint：填充色 = lerp(白/深底, 主题强调色) 且 alpha 很低（dock 0.26 / 卡片 0.34），
 *     直接透出模糊内容 —— v3.1 的 0.60 填充是"看起来不是玻璃"的根因，已全部下调；
 *  3) rim：1dp 顶部高光 / 底部暗边；
 *  4) lens：1.2dp 内缩折射环（上亮→中透→下微亮），模拟玻璃厚度与边缘透镜；
 *  5) sheen / sweep：左上→右下镜面高光 +（dock）缓慢扫过的液态高光。
 *
 * 单一 tint 还能命中 Haze 的 tint+noise 合并快通道，画质更均匀、性能更好。
 * API < 31 时 haze 不产生模糊，材质其余部分保持一致（填充稍高，仍半透明）。
 * ============================================================ */

@Composable
fun MioGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    role: MioGlassRole,
    strength: Float = 1f,
    /** 非空时按顶栏滚动进度做渐强（0=通透，1=完全雾化）；顶栏使用。 */
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
    // 有色玻璃：把主题强调色按 tintMix 混进白/深底；INDICATOR 直接用强调色
    val tintColor = if (role == MioGlassRole.INDICATOR) {
        Ink.accent
    } else {
        lerp(Ink.glassFill, Ink.glassTint, geometry.tintMix.coerceIn(0f, 1f))
    }

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

    val style = remember(role, geometry, tintColor, dark) {
        val base = HazeBlurStyle {
            blurEnabled(geometry.blurRadius > 0.dp)
            blurRadius(geometry.blurRadius)
            noiseFactor(geometry.noiseFactor)
            // 兜底底色（模糊不可用时使用；正常模糊下被源内容覆盖）
            backgroundColor(tintColor)
            // 只放一个 tint，命中 Haze 的 tint+noise 合并快通道
            colorEffects(
                listOf(HazeColorEffect.tint(tintColor.copy(alpha = geometry.fillAlpha.coerceIn(0f, 1f)))),
            )
        }
        if (role == MioGlassRole.CHROME) {
            // 顶栏：顶部实、底部渐隐，避免硬边
            base.then {
                progressive(
                    HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0.45f,
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
        surface.background(tintColor.copy(alpha = fillAlpha))
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
    // 边缘折射环（液态玻璃的"厚度/透镜感"）
    surface = surface.mioGlassLensEdge(shape, geometry.lensAlpha)
    // 镜面高光
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

/** 把任意 Shape 转成 Path（用于沿轮廓画折射环）。 */
private fun Shape.toOutlinePath(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Path {
    val outline = createOutline(size, layoutDirection, density)
    return Path().apply {
        when (outline) {
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
            is Outline.Rectangle -> addRect(outline.rect)
        }
    }
}

/**
 * 边缘折射环：内缩 1.2dp 的描边，上亮 → 中透 → 下微亮。
 * 因为外层已有 clip(shape)，描边越界部分会被裁掉，效果是一条贴边的玻璃厚度线。
 */
fun Modifier.mioGlassLensEdge(shape: Shape, alpha: Float): Modifier {
    if (alpha <= 0.001f) return this
    return this.drawWithCache {
        val strokePx = 1.2.dp.toPx()
        val path = shape.toOutlinePath(size, layoutDirection, this)
        val brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (alpha * 0.95f).coerceIn(0f, 1f)),
                Color.White.copy(alpha = (alpha * 0.06f).coerceIn(0f, 1f)),
                Color.White.copy(alpha = (alpha * 0.45f).coerceIn(0f, 1f)),
            ),
        )
        onDrawWithContent {
            drawContent()
            drawPath(path, brush = brush, style = Stroke(width = strokePx))
        }
    }
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

/**
 * 液态高光扫过（dock 专用）：一条柔和的斜向白色光带随进度 [progress]（0..1）扫过表面。
 * Reduce Motion 时调用方传 alpha=0 关闭。
 */
fun Modifier.mioGlassSweep(progress: Float, alpha: Float): Modifier {
    if (alpha <= 0.001f) return this
    return this.drawWithCache {
        val p = progress.coerceIn(0f, 1f)
        val band = size.width * 0.42f
        val cx = size.width * (p * 1.6f - 0.3f)
        val brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = alpha),
            1f to Color.Transparent,
            start = Offset(cx - band, 0f),
            end = Offset(cx + band, size.height),
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
