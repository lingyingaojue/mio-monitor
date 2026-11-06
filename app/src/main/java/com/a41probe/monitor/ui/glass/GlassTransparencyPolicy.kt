package com.a41probe.monitor.ui.glass

import androidx.compose.runtime.Composable
import com.a41probe.monitor.ui.components.GlassTransparency
import com.a41probe.monitor.ui.components.GlassTransparencyConfig

/* ============================================================
 * Mio 3.7 全局玻璃透明度策略（OriginOS「高级材质」对齐）
 *
 * 材质语言：强实时模糊 + 透彩色（背景颜色透上来）+ 很低的填充 tint
 * + 细腻边缘高光；透明度档位只缩放"填充不透明度"。
 *
 * 四档以「低」为 OriginOS 参考档（1.0），中/高/极高逐级更透；
 * 模糊半径、边缘折射/色散、Bloom 高光、悬浮投影、内阴影、
 * 可读性 scrim 全部保持不变——透明度是材质浓度，不牺牲文字对比。
 * ============================================================ */

/** 透明度档位的 alpha 倍率：低=OriginOS 参考档，中/高/极高逐级更透。 */
fun glassTransparencyFactor(level: GlassTransparency): Float = when (level) {
    GlassTransparency.LOW -> 1.00f
    GlassTransparency.MEDIUM -> 0.80f
    GlassTransparency.HIGH -> 0.58f
    GlassTransparency.ULTRA -> 0.38f
}

/** 把基础 alpha 按档位缩放并夹在安全区间，避免完全透明或超出 1。 */
fun scaleGlassAlpha(
    base: Float,
    level: GlassTransparency,
    floor: Float = 0.035f,
    ceiling: Float = 0.92f,
): Float = (base * glassTransparencyFactor(level)).coerceIn(floor, ceiling)

/**
 * 各玻璃角色的"透彩色"增艳强度（Haze ColorMatrix 饱和度）。
 * 这是 OriginOS 高级材质与普通磨砂的核心差异：模糊后的背景颜色更艳、更透。
 * SHEET 保持克制，保证弹层文字可读。
 */
fun resolveGlassVibrancy(role: MioGlassRole): Float = when (role) {
    MioGlassRole.CARD -> 1.45f
    MioGlassRole.CHROME -> 1.35f
    MioGlassRole.DOCK -> 1.40f
    MioGlassRole.INDICATOR -> 1.40f
    MioGlassRole.SHEET -> 1.15f
}

/** 液态（Miuix Backdrop）材质：只改 surfaceAlpha，scrimAlpha 保持可读性不变。 */
fun applyMioLiquidTransparency(
    tuning: MioLiquidTuning,
    level: GlassTransparency,
): MioLiquidTuning {
    if (!tuning.available) return tuning
    return tuning.copy(
        surfaceAlpha = scaleGlassAlpha(tuning.surfaceAlpha, level, floor = 0.035f, ceiling = 0.85f),
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
        fillAlpha = scaleGlassAlpha(geometry.fillAlpha, level, floor = 0.035f, ceiling = 0.90f),
    )
}

/**
 * 供直接使用 `Ink.glassFill.copy(alpha=…)` 的元素（玻璃圆钮 / 弹窗底 / 引导 chip）使用：
 * 读当前档位后统一缩放。这些元素本身就是玻璃填充，即使"液态玻璃"档关闭也保持可调。
 */
@Composable
fun scaledGlassFillAlpha(
    base: Float,
    floor: Float = 0.035f,
    ceiling: Float = 0.96f,
): Float = scaleGlassAlpha(base, GlassTransparencyConfig.current(), floor, ceiling)
/**
 * v3.8 PrismalAGSL 卡片玻璃的 tint 浓度：只做很淡的可读性 overlay，
 * 不把玻璃染成白雾；四档透明度继续按倍率缩放。
 */
fun prismalCardTintAlpha(dark: Boolean, level: GlassTransparency): Float =
    scaleGlassAlpha(if (dark) 0.12f else 0.08f, level, floor = 0.03f, ceiling = 0.20f)

/** Prismal 卡片玻璃启用门槛：完整液态档 + 背景层背板可用 + 普通卡片（非引导覆盖层）。 */
fun usesPrismalCardGlass(
    role: MioGlassRole,
    overlay: Boolean,
    capability: GlassCapability,
    prismalAvailable: Boolean,
): Boolean = role == MioGlassRole.CARD &&
    !overlay &&
    prismalAvailable &&
    capability.enabled &&
    capability.fullSurfaceBlur &&
    capability.mode == GlassRenderMode.LIQUID

/** Haze/降级路径的装饰性边缘色散强度（0 = 不画）。 */
fun resolveGlassEdgeDispersion(role: MioGlassRole, realtimeBlur: Boolean): Float {
    if (!realtimeBlur) return 0f
    return when (role) {
        MioGlassRole.CARD -> 0.40f
        MioGlassRole.DOCK -> 0.45f
        MioGlassRole.CHROME -> 0.25f
        MioGlassRole.INDICATOR -> 0.20f
        MioGlassRole.SHEET -> 0f
    }
}