package com.a41probe.monitor.ui.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a41probe.monitor.ui.components.GlassConfig
import com.a41probe.monitor.ui.components.GlassLevel

/* ============================================================
 * Mio 3.2 液态玻璃策略（纯逻辑，可单测）
 *
 * 引擎：Haze 2.0.1（design-system 已依赖）
 *  - API >= 31：真 RenderEffect 模糊（>=33 Haze 自动用 RuntimeShader 路径）
 *  - API <  31：系统无实时模糊，降级为「半透明 + 高光」玻璃材质
 *  - 用户档位 FULL/LITE/OFF 决定玻璃覆盖范围与强度
 *
 * v3.2 关键修正：v3.1 的填充 alpha（dock 0.60/0.62、卡片 0.55）在视觉上等于实色磨砂。
 * 现在全部下调到「能明显透出模糊内容」的区间（dock 0.26/0.34、卡片 0.34/0.42），
 * 并新增镜面折射边（lens）与高光（specular）参数，让玻璃有厚度与液态反光。
 * ============================================================ */

/** 玻璃渲染档位（由系统能力 + 用户设置共同决定）。 */
enum class GlassRenderMode {
    /** API 33+：实时模糊 + Miuix 液态控件 */
    LIQUID,
    /** API 31-32：实时模糊（无 Miuix 液态控件） */
    BLUR,
    /** API 26-30：系统不支持实时模糊，半透明材质 */
    TINT,
    /** 用户关闭：实色 */
    OFF,
}

/** 当前设备 + 用户设置解析出的一套玻璃能力。 */
data class GlassCapability(
    val mode: GlassRenderMode,
    val realtimeBlur: Boolean,
    val liquidControls: Boolean,
    val sdkInt: Int,
    /** FULL 档：所有表面玻璃；LITE 档：仅壳层模糊、卡片半透明不模糊 */
    val fullSurfaceBlur: Boolean,
) {
    val enabled: Boolean get() = mode != GlassRenderMode.OFF
    val translucent: Boolean get() = mode != GlassRenderMode.OFF
}

fun resolveGlassCapability(level: GlassLevel, sdkInt: Int): GlassCapability {
    val mode = when {
        level == GlassLevel.OFF -> GlassRenderMode.OFF
        sdkInt >= Build.VERSION_CODES.TIRAMISU -> GlassRenderMode.LIQUID
        sdkInt >= Build.VERSION_CODES.S -> GlassRenderMode.BLUR
        else -> GlassRenderMode.TINT
    }
    return GlassCapability(
        mode = mode,
        realtimeBlur = mode == GlassRenderMode.LIQUID || mode == GlassRenderMode.BLUR,
        liquidControls = level == GlassLevel.FULL && sdkInt >= Build.VERSION_CODES.TIRAMISU,
        sdkInt = sdkInt,
        fullSurfaceBlur = level == GlassLevel.FULL,
    )
}

/** 读取当前设置 + 设备能力（Compose 状态：设置页切换后立即重组）。 */
@Composable
fun rememberGlassCapability(): GlassCapability =
    resolveGlassCapability(GlassConfig.current(), Build.VERSION.SDK_INT)

/** 玻璃表面角色：决定模糊强度/填充浓度/圆角与阴影。 */
enum class MioGlassRole {
    /** 内容卡片（仪表盘/CPU/设置分组/图表卡…） */
    CARD,
    /** 顶栏 / 页面返回条（内容从其下滚过） */
    CHROME,
    /** 底部 dock（悬浮玻璃条） */
    DOCK,
    /** 独立玻璃胶囊（dock 选中态、按钮、chip） */
    INDICATOR,
    /** 弹层（bottom sheet / dialog 表面；模糊由弹窗背后 RenderEffect 提供） */
    SHEET,
}

/** 玻璃采样的源层：卡片默认采样背景（环境光/立绘），覆盖层上的玻璃需要采样内容层。 */
enum class MioGlassSampling { PAGE, CHROME }

/** 一种角色在当前能力/明暗下的材质参数。 */
data class MioGlassGeometry(
    val blurRadius: Dp,
    /** 玻璃填充不透明度：越低越通透（v3.2 已大幅下调） */
    val fillAlpha: Float,
    /** 主题强调色混入填充的比例（0=纯白/纯黑玻璃，越大越有主题色） */
    val tintMix: Float,
    val noiseFactor: Float,
    val shadowElevation: Dp,
    val shadowAlpha: Float,
    /** 顶部高光描边强度 */
    val rimLightAlpha: Float,
    /** 左上→右下镜面高光强度 */
    val sheenAlpha: Float,
    /** 内侧折射环强度（玻璃厚度/边缘透镜感） */
    val lensAlpha: Float,
    /** 流动高光强度（仅 dock 使用；Reduce Motion 时归零） */
    val specularAlpha: Float,
    /** 是否用 Haze 采样同窗口背景（弹层在独立窗口里采样不到，只能靠 window blur） */
    val hazeSampling: Boolean,
)

fun resolveGlassGeometry(
    role: MioGlassRole,
    capability: GlassCapability,
    dark: Boolean,
    strength: Float = 1f,
): MioGlassGeometry {
    val k = strength.coerceIn(0.4f, 1.6f)
    if (!capability.enabled) {
        return MioGlassGeometry(
            blurRadius = 0.dp, fillAlpha = 1f, tintMix = 0f, noiseFactor = 0f,
            shadowElevation = 0.dp, shadowAlpha = 0f,
            rimLightAlpha = 0.22f, sheenAlpha = 0.04f,
            lensAlpha = 0.12f, specularAlpha = 0f, hazeSampling = false,
        )
    }
    // 没有实时模糊时提高不透明度，保证文字对比度（但仍保留玻璃质感）
    val noBlurBoost = if (capability.realtimeBlur) 0f else 0.14f
    val liteBoost = if (capability.fullSurfaceBlur) 0f else 0.10f

    return when (role) {
        MioGlassRole.CARD -> MioGlassGeometry(
            blurRadius = if (capability.realtimeBlur && capability.fullSurfaceBlur) 24.dp else 0.dp,
            fillAlpha = ((if (dark) 0.42f else 0.34f) * (0.92f + 0.08f * k) +
                noBlurBoost + liteBoost).coerceAtMost(0.92f),
            tintMix = 0.03f * k,
            noiseFactor = 0.028f,
            shadowElevation = 8.dp,
            shadowAlpha = if (dark) 0.40f else 0.14f,
            rimLightAlpha = (if (dark) 0.26f else 0.45f) * k,
            sheenAlpha = (if (dark) 0.06f else 0.12f) * k,
            lensAlpha = (if (dark) 0.22f else 0.18f) * k,
            specularAlpha = 0f,
            hazeSampling = true,
        )

        MioGlassRole.CHROME -> MioGlassGeometry(
            blurRadius = if (capability.realtimeBlur) 32.dp else 0.dp,
            fillAlpha = (if (dark) 0.40f else 0.36f) + noBlurBoost,
            tintMix = 0.05f,
            noiseFactor = 0.022f,
            shadowElevation = 10.dp,
            shadowAlpha = if (dark) 0.45f else 0.16f,
            rimLightAlpha = if (dark) 0.28f else 0.55f,
            sheenAlpha = if (dark) 0.06f else 0.14f,
            lensAlpha = if (dark) 0.26f else 0.22f,
            specularAlpha = 0f,
            hazeSampling = true,
        )

        MioGlassRole.DOCK -> MioGlassGeometry(
            blurRadius = if (capability.realtimeBlur) 32.dp else 0.dp,
            fillAlpha = ((if (dark) 0.34f else 0.26f) + noBlurBoost).coerceAtMost(0.72f),
            tintMix = if (dark) 0.08f else 0.06f,
            noiseFactor = 0.018f,
            shadowElevation = 20.dp,
            shadowAlpha = if (dark) 0.50f else 0.18f,
            rimLightAlpha = if (dark) 0.34f else 0.55f,
            sheenAlpha = if (dark) 0.08f else 0.18f,
            lensAlpha = if (dark) 0.34f else 0.30f,
            specularAlpha = 0.07f,
            hazeSampling = true,
        )

        MioGlassRole.INDICATOR -> MioGlassGeometry(
            blurRadius = if (capability.realtimeBlur) 14.dp else 0.dp,
            fillAlpha = (if (dark) 0.24f else 0.20f) * k,
            tintMix = 1f, // 胶囊用强调色本身
            noiseFactor = 0.016f,
            shadowElevation = 0.dp,
            shadowAlpha = 0f,
            rimLightAlpha = (if (dark) 0.26f else 0.55f) * k,
            sheenAlpha = (if (dark) 0.10f else 0.22f) * k,
            lensAlpha = (if (dark) 0.24f else 0.20f) * k,
            specularAlpha = 0f,
            hazeSampling = true,
        )

        MioGlassRole.SHEET -> MioGlassGeometry(
            blurRadius = 0.dp, // 弹层在独立窗口：模糊由 ModalWindowBlurBehindEffect 提供
            fillAlpha = (if (dark) 0.68f else 0.62f) + noBlurBoost * 0.5f,
            tintMix = 0.035f,
            noiseFactor = 0.02f,
            shadowElevation = 0.dp,
            shadowAlpha = 0f,
            rimLightAlpha = if (dark) 0.22f else 0.62f,
            sheenAlpha = if (dark) 0.05f else 0.12f,
            lensAlpha = if (dark) 0.24f else 0.20f,
            specularAlpha = 0f,
            hazeSampling = false,
        )
    }
}

/** 用户关闭/简约档位下，卡片是否仍保留模糊。 */
fun cardBlurEnabled(level: GlassLevel, capability: GlassCapability): Boolean =
    level == GlassLevel.FULL && capability.realtimeBlur

/* ---------------- 顶栏滚动渐强 ---------------- */

/** 滚动进度 → 顶栏雾化进度（smoothstep，0=通透，1=完全玻璃）。 */
fun resolveChromeProgress(scrollFraction: Float): Float {
    val x = scrollFraction.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** 顶栏模糊半径随滚动渐强：滚到顶时几乎通透，滚起来才雾化。 */
fun resolveChromeBlurRadius(baseRadius: Dp, progress: Float): Dp {
    val p = progress.coerceIn(0f, 1f)
    return baseRadius * (0.28f + 0.72f * p)
}

/** 顶栏填充浓度随滚动渐强（顶部 α≈0.06，完全雾化时到基准值）。 */
fun resolveChromeFillAlpha(baseAlpha: Float, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return (baseAlpha * (0.16f + 0.84f * p)).coerceIn(0f, 1f)
}

/* ---------------- 壳层占位尺寸（内容避让） ---------------- */

/** 顶栏视觉高度（不含状态栏）。 */
val TopBarHeight: Dp = 58.dp
/** 底部 dock 视觉高度。 */
val DockHeight: Dp = 64.dp
val DockTopGap: Dp = 6.dp
val DockBottomGap: Dp = 8.dp
/** dock 左右悬浮留白（BiliPai 式悬浮玻璃条）。 */
val DockHorizontalInset: Dp = 12.dp

/** 内容需要让出的壳层高度（tab 页：顶栏 + dock；整页：页面返回条 + 底部安全区）。 */
data class MioChromeInsets(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
) {
    companion object {
        val Zero = MioChromeInsets()
    }
}
