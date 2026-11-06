package com.a41probe.monitor.ui.glass

import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ============================================================
 * Mio 3.3 液态玻璃策略（API 33+，Miuix Backdrop 引擎）
 *
 * 与 v3.2 的 Haze 玻璃不同，这一档走 BiliPai 同款光学链：
 *   内容录制背板 -> 饱和度(vibrancy) -> 背板模糊 -> 边缘折射(lens/色散)
 *   -> BloomStroke 边缘高光 -> 玻璃底色 + 可读性 scrim -> 悬浮投影/内阴影
 *
 * 该档只在 FULL 档且系统 API >= 33 时可用（Miuix miuix-blur 的 drawBackdrop
 * 依赖 Android 13 的 RenderEffect / RuntimeShader）；其它情况继续用 Haze 或半透明降级。
 * 纯数据/纯函数，便于 JVM 单测。
 * ============================================================ */

/** 当前设备能否使用 Miuix Backdrop 液态玻璃。 */
fun liquidChromeAvailable(
    capability: GlassCapability,
    sdkInt: Int = capability.sdkInt,
): Boolean = capability.fullSurfaceBlur &&
    sdkInt >= Build.VERSION_CODES.TIRAMISU &&
    capability.realtimeBlur

/** 液态玻璃的光学/材质参数（33+ 专用）。 */
data class MioLiquidTuning(
    /** 是否可用；false 时其余字段无意义（走 Haze/实色降级）。 */
    val available: Boolean,
    /** 玻璃底色不透明度（白/深底 + accent tint 混色后的 alpha）。 */
    val surfaceAlpha: Float,
    /** 可读性 scrim：浅色=白、深色=黑，叠在模糊内容上保证文字对比。 */
    val scrimAlpha: Float,
    /** 强调色混入比例（让玻璃带一点主题色，和纯白卡片区分开）。 */
    val tintMix: Float,
    /** 背板模糊半径。 */
    val blurRadius: Dp,
    /** 背板增艳（vibrancy / colorControls saturation）。 */
    val saturation: Float,
    /** 边缘折射高度（越大透镜带越宽）。 */
    val refractionHeight: Dp,
    /** 边缘折射强度（越大边缘内容弯曲越明显）。 */
    val refractionAmount: Dp,
    /** 边缘色散（0=无色散，BiliPai 平衡档壳体为 0、指示器有少量）。 */
    val chromaticAberration: Float,
    /** 边缘 bloom 高光强度。 */
    val highlightAlpha: Float,
    /** 悬浮投影。 */
    val shadowRadius: Dp,
    val shadowAlpha: Float,
    /** 底部内阴影（玻璃厚度感）。 */
    val innerShadowAlpha: Float,
) {
    companion object {
        val Unavailable = MioLiquidTuning(
            available = false,
            surfaceAlpha = 1f, scrimAlpha = 0f, tintMix = 0f,
            blurRadius = 0.dp, saturation = 1f,
            refractionHeight = 0.dp, refractionAmount = 0.dp,
            chromaticAberration = 0f, highlightAlpha = 0f,
            shadowRadius = 0.dp, shadowAlpha = 0f, innerShadowAlpha = 0f,
        )
    }
}

/**
 * 解析某个玻璃角色在当前明暗/滚动进度下的液态材质。
 *
 * @param progress CHROME 专用：0=滚到顶（通透），1=滚动后（雾化）。
 * @param overlay 覆盖层上、但需要采样"内容层背板"的卡片（如引导页分页卡）。
 */
fun resolveMioLiquidTuning(
    role: MioGlassRole,
    capability: GlassCapability,
    dark: Boolean,
    progress: Float = 0f,
    overlay: Boolean = false,
): MioLiquidTuning {
    if (!liquidChromeAvailable(capability)) return MioLiquidTuning.Unavailable
    val p = progress.coerceIn(0f, 1f)
    if (overlay) {
        // 覆盖层玻璃卡：采样内容背板（引导页下方的仪表盘 UI 被模糊透出）
        return MioLiquidTuning(
            available = true,
            surfaceAlpha = if (dark) 0.40f else 0.30f,
            scrimAlpha = if (dark) 0.10f else 0.06f,
            tintMix = if (dark) 0.08f else 0.06f,
            blurRadius = 12.dp,
            saturation = 1.5f,
            refractionHeight = 16.dp,
            refractionAmount = 18.dp,
            chromaticAberration = 0f,
            highlightAlpha = 0.80f,
            shadowRadius = 16.dp,
            shadowAlpha = if (dark) 0.30f else 0.14f,
            innerShadowAlpha = if (dark) 0.14f else 0.08f,
        )
    }
    return when (role) {
        MioGlassRole.DOCK -> MioLiquidTuning(
            available = true,
            surfaceAlpha = if (dark) 0.40f else 0.30f,
            scrimAlpha = if (dark) 0.10f else 0.06f,
            tintMix = if (dark) 0.08f else 0.06f,
            blurRadius = 10.dp,
            saturation = 1.5f,
            refractionHeight = 20.dp,
            refractionAmount = 22.dp,
            chromaticAberration = 0f, // BiliPai 平衡档：壳体无色散，色散留给指示器
            highlightAlpha = 0.85f,
            shadowRadius = 14.dp,
            shadowAlpha = if (dark) 0.28f else 0.12f,
            innerShadowAlpha = if (dark) 0.16f else 0.10f,
        )

        MioGlassRole.CHROME -> MioLiquidTuning(
            available = true,
            surfaceAlpha = lerp(if (dark) 0.08f else 0.06f, if (dark) 0.38f else 0.34f, p),
            scrimAlpha = lerp(if (dark) 0.03f else 0.02f, if (dark) 0.08f else 0.05f, p),
            tintMix = 0.05f,
            blurRadius = lerp(4f, 14f, p).dp,
            saturation = 1.4f,
            refractionHeight = lerp(6f, 14f, p).dp,
            refractionAmount = lerp(8f, 16f, p).dp,
            chromaticAberration = 0f,
            highlightAlpha = lerp(0.35f, 0.65f, p),
            shadowRadius = 8.dp,
            shadowAlpha = if (dark) 0.20f else 0.08f,
            innerShadowAlpha = if (dark) 0.10f else 0.06f,
        )

        MioGlassRole.INDICATOR -> MioLiquidTuning(
            available = true,
            surfaceAlpha = if (dark) 0.24f else 0.18f,
            scrimAlpha = 0f,
            tintMix = 0.9f, // 指示器以 accent 为主
            blurRadius = 4.dp,
            saturation = 1.5f,
            refractionHeight = 8.dp,
            refractionAmount = 12.dp,
            chromaticAberration = 0.4f,
            highlightAlpha = 0.80f,
            shadowRadius = 0.dp,
            shadowAlpha = 0f,
            innerShadowAlpha = if (dark) 0.14f else 0.10f,
        )

        // 卡片/弹层不使用 Miuix Backdrop 液态玻璃（卡片走 Haze，弹层走窗口模糊）
        MioGlassRole.CARD, MioGlassRole.SHEET -> MioLiquidTuning.Unavailable
    }
}

/** 设置页「玻璃引擎」自检文案（供用户/支持确认当前实际走的渲染路径）。 */
fun liquidEngineLabel(capability: GlassCapability): String = when {
    !capability.enabled -> "实色（玻璃已关闭）"
    liquidChromeAvailable(capability) -> "Miuix Backdrop 液态玻璃（实时折射 + 高光）"
    capability.realtimeBlur -> "Haze 实时模糊（系统不支持折射）"
    else -> "半透明降级（系统不支持实时模糊）"
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
