package com.a41probe.monitor.ui.glass

import androidx.compose.runtime.Composable
import com.a41probe.monitor.ui.components.GlassTransparency
import com.a41probe.monitor.ui.components.GlassTransparencyConfig

/* ============================================================
 * Mio 3.6 全局玻璃透明度策略（纯逻辑，可 JVM 单测）
 *
 * 只缩放"玻璃填充不透明度"，模糊半径、边缘折射/色散、Bloom 高光、
 * 悬浮投影、内阴影、可读性 scrim 全部保持不变——透明度是材质浓度，
 * 不是把玻璃关掉，因此不牺牲文字对比。
 *
 * 档位：低(更实) / 中(默认，= 3.5 外观) / 高 / 极高。
 * ============================================================ */

/** 透明度档位的 alpha 倍率：越高越透（alpha 越低）。中档为 1.0，保证升级零视觉变化。 */
fun glassTransparencyFactor(level: GlassTransparency): Float = when (level) {
    GlassTransparency.LOW -> 1.35f
    GlassTransparency.MEDIUM -> 1.00f
    GlassTransparency.HIGH -> 0.70f
    GlassTransparency.ULTRA -> 0.45f
}

/** 把基础 alpha 按档位缩放并夹在安全区间，避免完全透明或超出 1。 */
fun scaleGlassAlpha(
    base: Float,
    level: GlassTransparency,
    floor: Float = 0.06f,
    ceiling: Float = 0.92f,
): Float = (base * glassTransparencyFactor(level)).coerceIn(floor, ceiling)

/** 液态（Miuix Backdrop）材质：只改 surfaceAlpha，scrimAlpha 保持可读性不变。 */
fun applyMioLiquidTransparency(
    tuning: MioLiquidTuning,
    level: GlassTransparency,
): MioLiquidTuning {
    if (!tuning.available) return tuning
    return tuning.copy(
        surfaceAlpha = scaleGlassAlpha(tuning.surfaceAlpha, level, floor = 0.05f, ceiling = 0.85f),
    )
}

/** Haze / 半透明降级材质：只改 fillAlpha；玻璃关闭（enabled=false）时保持实色不变。 */
fun applyGlassGeometryTransparency(
    geometry: MioGlassGeometry,
    level: GlassTransparency,
    enabled: Boolean = true,
): MioGlassGeometry {
    if (!enabled) return geometry
    return geometry.copy(
        fillAlpha = scaleGlassAlpha(geometry.fillAlpha, level, floor = 0.06f, ceiling = 0.90f),
    )
}

/**
 * 供直接使用 `Ink.glassFill.copy(alpha=…)` 的元素（玻璃圆钮 / 弹窗底 / 引导 chip）使用：
 * 读当前档位后统一缩放。这些元素本身就是玻璃填充，即使"液态玻璃"档关闭也保持可调。
 */
@Composable
fun scaledGlassFillAlpha(
    base: Float,
    floor: Float = 0.04f,
    ceiling: Float = 0.96f,
): Float = scaleGlassAlpha(base, GlassTransparencyConfig.current(), floor, ceiling)
