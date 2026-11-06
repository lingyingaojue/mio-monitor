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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a41probe.monitor.ui.components.GlassTransparencyConfig
import com.a41probe.monitor.ui.theme.Ink
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.effect.lens
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource

/* ============================================================
 * 玻璃表面引擎（v3.3）
 *
 * 两条渲染路径，按能力自动选择：
 *
 * A) Miuix Backdrop 液态玻璃（FULL + API 33+，LocalMioChromeBackdrop != null）
 *    —— BiliPai 同款光学链：内容背板录制 → vibrancy 增艳 → 背板模糊
 *       → lens 边缘折射（可带色散）→ BloomStroke 边缘高光
 *       → 玻璃底色 + 可读性 scrim → 悬浮投影 + 内阴影
 *    用于：DOCK（底部导航）、CHROME（顶栏/整页返回条）、INDICATOR（选中胶囊）
 *
 * B) Haze 模糊 / 半透明降级（API 31/32、LITE、或 CARD/SHEET 角色）
 *    —— v3.2 材质：单 tint + 低不透明度 + 折射环 + 镜面高光。
 *    用于：卡片（采样背景层）、弹层（窗口模糊）、以及所有降级设备。
 * ============================================================ */

@Composable
fun MioGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    role: MioGlassRole,
    strength: Float = 1f,
    /** 非空时按顶栏滚动进度做渐强（0=通透，1=完全雾化）；顶栏使用。 */
    progress: Float? = null,
    /**
     * 液态分支的滚动进度 provider：在 draw 阶段读取（Miuix DrawBackdropNode 观察 snapshot），
     * 避免滚动时每像素重组并重建 effect 链。非液态降级路径仍走 [progress]。
     */
    progressProvider: (() -> Float)? = null,
    /** 覆盖角色默认的采样源（如引导页覆盖层要采样内容层而非背景层）。 */
    sampling: MioGlassSampling? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalMioGlass.current
    val capability = currentGlassCapability()
    val dark = Ink.bg.luminance() < 0.5f
    // v3.6：全局玻璃透明度（Compose state，切换后所有玻璃表面即时重组）
    val transparency = GlassTransparencyConfig.current()
    val liquidSource = LocalMioChromeBackdrop.current
    // 覆盖层卡片（引导页）：采样内容背板而不是背景层
    val isOverlayCard = role == MioGlassRole.CARD && sampling == MioGlassSampling.CHROME
    // 该角色是否处于"液态模式"（API33+ 完整档）：此时内容层没有挂 Haze 源，
    // 不能用 chromeState 做 Haze 降级（会采空源），退回纯 tint。
    val chromeLiquidMode = liquidSource != null && role != MioGlassRole.SHEET &&
        (role != MioGlassRole.CARD || isOverlayCard)
    // 液态材质两端值：CHROME 在 draw 阶段按滚动进度插值，其余角色两端相同
    val liquidTuningFull = remember(role, capability, dark, isOverlayCard, transparency) {
        applyMioLiquidTransparency(
            resolveMioLiquidTuning(role, capability, dark, progress = 1f, overlay = isOverlayCard),
            transparency,
        )
    }
    val liquidTuningRest = remember(role, capability, dark, isOverlayCard, transparency) {
        applyMioLiquidTransparency(
            resolveMioLiquidTuning(role, capability, dark, progress = 0f, overlay = isOverlayCard),
            transparency,
        )
    }
    // 首帧/换屏时 backdrop 尚未录制完成：先走 tint 降级，isReady 变 true 后自动重组为液态
    val liquidReady = liquidSource?.isReady == true && liquidTuningFull.available

    // ---- A) Miuix Backdrop 液态玻璃 ----
    if (liquidReady) {
        MioLiquidGlassSurface(
            modifier = modifier,
            shape = shape,
            tuning = liquidTuningFull,
            restTuning = liquidTuningRest,
            progressProvider = progressProvider,
            source = liquidSource!!,
            onClick = onClick,
            content = content,
        )
        return
    }

    // ---- B) Haze 模糊 / 半透明降级（v3.2 材质）----
    val effectiveProgress = progress ?: progressProvider?.invoke()
    val baseGeometry = remember(role, capability, dark, strength) {
        resolveGlassGeometry(role, capability, dark, strength)
    }
    val geometry = remember(baseGeometry, effectiveProgress, transparency, capability.enabled) {
        val ramped = if (effectiveProgress == null) {
            baseGeometry
        } else {
            baseGeometry.copy(
                blurRadius = resolveChromeBlurRadius(baseGeometry.blurRadius, effectiveProgress),
                fillAlpha = resolveChromeFillAlpha(baseGeometry.fillAlpha, effectiveProgress),
            )
        }
        applyGlassGeometryTransparency(ramped, transparency, enabled = capability.enabled)
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
        geometry.hazeSampling && capability.realtimeBlur && geometry.blurRadius > 0.dp &&
        // 液态模式下内容层没有 Haze 源（只录了 Backdrop），壳层角色不能再采 chromeState
        !chromeLiquidMode

    val style = remember(role, geometry, tintColor, dark) {
        val base = HazeBlurStyle {
            blurEnabled(geometry.blurRadius > 0.dp)
            blurRadius(geometry.blurRadius)
            noiseFactor(geometry.noiseFactor)
            // 兜底底色（模糊不可用时使用；正常模糊下被源内容覆盖）
            backgroundColor(tintColor)
            // v3.7 OriginOS「高级材质」：先做饱和度增艳（透彩色），再叠低 alpha tint。
            // 仅实时模糊路径启用；无模糊降级时颜色滤镜无意义。
            colorEffects(
                buildList {
                    if (capability.realtimeBlur && geometry.blurRadius > 0.dp) {
                        val vibrancy = resolveGlassVibrancy(role)
                        if (vibrancy > 1.001f) {
                            add(
                                HazeColorEffect.colorFilter(
                                    ColorFilter.colorMatrix(
                                        ColorMatrix().apply { setToSaturation(vibrancy) },
                                    ),
                                ),
                            )
                        }
                    }
                    add(
                        HazeColorEffect.tint(
                            tintColor.copy(alpha = geometry.fillAlpha.coerceIn(0f, 1f)),
                        ),
                    )
                },
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

/**
 * A) Miuix Backdrop 液态玻璃表面（API 33+）。
 * 背板由 MainActivity 的 MioChromeContent 录制，这里只做光学合成。
 *
 * [tuning] / [restTuning] 是滚动进度两端的材质；CHROME 在 draw 阶段按
 * [progressProvider] 插值（Miuix DrawBackdropNode 观察 snapshot 读），
 * 避免滚动时在组合期反复重建 effect 链。
 */
@Composable
private fun MioLiquidGlassSurface(
    modifier: Modifier,
    shape: Shape,
    tuning: MioLiquidTuning,
    restTuning: MioLiquidTuning,
    progressProvider: (() -> Float)?,
    source: ChromeBackdropSource,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = Ink.bg.luminance() < 0.5f
    val tintColor = remember(tuning, restTuning) {
        lerp(Ink.glassFill, Ink.glassTint, tuning.tintMix.coerceIn(0f, 1f))
    }
    val scrimColor = if (dark) Color.Black else Color.White
    // Bloom 高光只在液态分支里构造：API<33 的 Haze 降级路径不应触达 Miuix blur 类
    val bloomStyle = remember {
        BloomStroke(
            color = Color.White.copy(alpha = 0.16f),
            innerBlurRadius = 2.dp,
            primaryLight = LightSource(
                position = LightPosition(0.5f, -0.3f, -0.05f),
                color = Color.White,
                intensity = 1f,
            ),
            secondaryLight = LightSource(
                position = LightPosition(0.5f, 0.8f, -0.5f),
                color = Color.White,
                intensity = 0.4f,
            ),
            dualPeak = true,
        )
    }
    val shadowRadius = tuning.shadowRadius
    val shadowAlpha = tuning.shadowAlpha
    val innerShadowAlpha = tuning.innerShadowAlpha

    var surface = modifier
    if (shadowRadius > 0.dp) {
        surface = surface.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = shadowRadius,
                color = Color.Black,
                alpha = shadowAlpha.coerceIn(0f, 1f),
            ),
        )
    }
    surface = surface.drawBackdrop(
        backdrop = source.backdrop,
        shape = { shape },
        effects = {
            val p = progressProvider?.invoke()?.coerceIn(0f, 1f) ?: 1f
            // 1) 增艳：玻璃下的内容更接近"透过玻璃看"的饱和感
            colorControls(
                brightness = 0.01f,
                contrast = 1.05f,
                saturation = lerpF(restTuning.saturation, tuning.saturation, p),
            )
            // 2) 背板模糊（随滚动渐强）
            val blurPx = lerpDp(restTuning.blurRadius, tuning.blurRadius, p).toPx()
            blur(blurPx, blurPx)
            // 3) 边缘折射（含可选的色散）：液态玻璃的核心
            val refractionHeight = lerpDp(restTuning.refractionHeight, tuning.refractionHeight, p)
            val refractionAmount = lerpDp(restTuning.refractionAmount, tuning.refractionAmount, p)
            if (refractionHeight > 0.dp && refractionAmount > 0.dp) {
                lens(
                    refractionHeight = refractionHeight.toPx(),
                    refractionAmount = refractionAmount.toPx(),
                    chromaticAberration = lerpF(
                        restTuning.chromaticAberration,
                        tuning.chromaticAberration,
                        p,
                    ),
                )
            }
        },
        highlight = {
            val p = progressProvider?.invoke()?.coerceIn(0f, 1f) ?: 1f
            Highlight(
                width = 1.dp,
                alpha = lerpF(restTuning.highlightAlpha, tuning.highlightAlpha, p).coerceIn(0f, 1f),
                style = bloomStyle,
            )
        },
        onDrawSurface = {
            val p = progressProvider?.invoke()?.coerceIn(0f, 1f) ?: 1f
            drawRect(
                tintColor.copy(
                    alpha = lerpF(restTuning.surfaceAlpha, tuning.surfaceAlpha, p).coerceIn(0f, 1f),
                ),
            )
            val scrimAlpha = lerpF(restTuning.scrimAlpha, tuning.scrimAlpha, p)
            if (scrimAlpha > 0f) {
                drawRect(scrimColor.copy(alpha = scrimAlpha.coerceIn(0f, 1f)))
            }
        },
    )
    // 底部内阴影：玻璃厚度感（贴底的一圈暗边）
    if (innerShadowAlpha > 0f) {
        surface = surface.innerShadow(shape) {
            radius = 6.dp.toPx()
            alpha = innerShadowAlpha.coerceIn(0f, 1f)
            color = Color.Black
        }
    }
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

private fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    (start.value + (stop.value - start.value) * fraction).dp

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
private fun Shape.toOutlinePath(
    size: androidx.compose.ui.geometry.Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: androidx.compose.ui.unit.Density,
): Path {
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
 * 边缘折射环（Haze 降级路径用）：内缩 1.2dp 的描边，上亮 → 中透 → 下微亮。
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
