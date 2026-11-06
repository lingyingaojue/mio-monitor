package com.a41probe.monitor.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/* ============================================================
 * Mio 3.0 设计令牌（BiliPai / Miuix 视觉语言）
 *
 * 与 v1.x 的兼容策略：
 *  - 字段名与类型完全不变（bg/card/panel/stroke/tx/tx2/tx3/off/accent/...）；
 *  - 内部改为读取「已解析颜色快照」的 Compose 状态，MioTheme 组合时 bind()；
 *  - 因此全工程数百处 `Ink.xxx` 调用点一个字都不用改，明暗/配色切换自动重组。
 *  - 语义色（温度红/橙/绿、警告/危险）随明暗模式各取一版，不再写死。
 * ============================================================ */

/** 主题解析后的一整份颜色快照。字段与旧 Ink 一一对应，另补充 Miuix 角色。 */
data class MioColors(
    val bg: Color,
    val card: Color,
    val panel: Color,
    val stroke: Color,
    val tx: Color,
    val tx2: Color,
    val tx3: Color,
    val off: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val curve: Color,
    val shadowTint: Color,
    val clusterPrime: Color,
    val clusterPerf: Color,
    val clusterEff: Color,
    val clusterBal: Color,
    val iconBgBlue: Color,
    val iconBgCyan: Color,
    val iconBgGreen: Color,
    val iconBgOrange: Color,
    val iconBgPurple: Color,
    val iconBgYellow: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    /** 卡片内嵌深一档的表面（旧 NestedTile 的 Ink.panel 语义） */
    val surfaceSunken: Color,
    /** 分割线（Miuix dividerLine） */
    val divider: Color,
    /** 强调色浅底上可读的文字色 */
    val onAccentSoft: Color,
    // ---- v3.1 玻璃材质令牌（MioGlass 引擎使用）----
    /** 玻璃基底色（不透明底，透明度由玻璃几何决定） */
    val glassFill: Color,
    /** 玻璃上的主题色 tint */
    val glassTint: Color,
    /** 玻璃顶部高光描边 */
    val glassRimLight: Color,
    /** 玻璃底部暗边 */
    val glassRimShade: Color,
    /** 玻璃外阴影（随主题明暗） */
    val glassShadow: Color,
) {
    companion object {
        private fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount)

        private fun Color.mixDark(amount: Float): Color = lerp(this, Color(0xFF1A1A1A), amount)

        /** WCAG 2.1 相对亮度对比度。 */
        private fun contrastRatio(a: Color, b: Color): Double {
            fun luminanceOf(c: Color): Double {
                fun ch(v: Float): Double {
                    val s = v.toDouble()
                    return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
                }
                return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
            }
            val la = luminanceOf(a)
            val lb = luminanceOf(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** 深色模式下强调色会被提亮：按对比度在白字/深字之间自动取更可读的一个。 */
        private fun Color.readableOnColor(): Color {
            val white = Color.White
            val dark = Color(0xFF101010)
            return if (contrastRatio(this, white) >= contrastRatio(this, dark)) white else dark
        }

        /** 由 Mio 配色预设 + 明暗模式派生全套颜色。深色表面固定中性色，只让强调色带色温。 */
        fun from(palette: Palette, dark: Boolean): MioColors {
            if (!dark) {
                return MioColors(
                    bg = palette.bg,
                    card = palette.card,
                    panel = palette.panel,
                    stroke = palette.stroke,
                    tx = palette.tx,
                    tx2 = palette.tx2,
                    tx3 = palette.tx3,
                    off = palette.off,
                    accent = palette.accent,
                    accent2 = palette.accent2,
                    onAccent = palette.onAccent,
                    accentSoft = palette.accentSoft,
                    curve = palette.curve,
                    shadowTint = palette.shadowTint,
                    clusterPrime = palette.clusterPrime,
                    clusterPerf = palette.clusterPerf,
                    clusterEff = palette.clusterEff,
                    clusterBal = palette.clusterBal,
                    iconBgBlue = palette.iconBgBlue,
                    iconBgCyan = palette.iconBgCyan,
                    iconBgGreen = palette.iconBgGreen,
                    iconBgOrange = palette.iconBgOrange,
                    iconBgPurple = palette.iconBgPurple,
                    iconBgYellow = palette.iconBgYellow,
                    ok = Color(0xFF34C759),
                    warn = Color(0xFFFF9500),
                    danger = Color(0xFFFF3B30),
                    surfaceSunken = palette.panel,
                    divider = palette.stroke,
                    onAccentSoft = palette.accent,
                    glassFill = Color.White,
                    glassTint = palette.accent,
                    glassRimLight = Color.White.copy(alpha = 0.88f),
                    glassRimShade = Color(0xFF1C1C1E).copy(alpha = 0.06f),
                    glassShadow = palette.shadowTint.copy(alpha = 0.24f),
                )
            }
            val darkAccent = palette.accent.lighten(0.34f)
            return MioColors(
                bg = Color(0xFF0D0D0D),
                card = Color(0xFF1A1A1A),
                panel = Color(0xFF262626),
                stroke = Color(0xFF2E2E2E),
                tx = Color(0xFFE8E8E8),
                tx2 = Color(0xFFB0B0B0),
                tx3 = Color(0xFF9A9A9A),
                off = Color(0xFF707070),
                accent = darkAccent,
                accent2 = palette.accent2.lighten(0.1f),
                onAccent = darkAccent.readableOnColor(),
                accentSoft = darkAccent.copy(alpha = 0.22f),
                curve = darkAccent,
                shadowTint = Color.Black,
                clusterPrime = palette.clusterPrime.lighten(0.22f),
                clusterPerf = palette.clusterPerf.lighten(0.22f),
                clusterEff = palette.clusterEff.lighten(0.22f),
                clusterBal = palette.clusterBal.lighten(0.22f),
                iconBgBlue = palette.iconBgBlue.mixDark(0.74f),
                iconBgCyan = palette.iconBgCyan.mixDark(0.74f),
                iconBgGreen = palette.iconBgGreen.mixDark(0.74f),
                iconBgOrange = palette.iconBgOrange.mixDark(0.74f),
                iconBgPurple = palette.iconBgPurple.mixDark(0.74f),
                iconBgYellow = palette.iconBgYellow.mixDark(0.74f),
                ok = Color(0xFF30D158),
                warn = Color(0xFFFF9F0A),
                danger = Color(0xFFFF453A),
                surfaceSunken = Color(0xFF111111),
                divider = Color(0xFF2A2A2A),
                onAccentSoft = darkAccent,
                glassFill = Color(0xFF161616),
                glassTint = darkAccent,
                glassRimLight = Color.White.copy(alpha = 0.34f),
                glassRimShade = Color.Black.copy(alpha = 0.30f),
                glassShadow = Color.Black.copy(alpha = 0.55f),
            )
        }
    }
}

object Ink {
    private val state = mutableStateOf(MioColors.from(Palettes.AZURE, dark = false))

    /** 由 MioTheme 在组合时写入；切换主题/明暗后所有读取点自动重组。 */
    internal fun bind(colors: MioColors) {
        state.value = colors
    }

    val bg: Color get() = state.value.bg
    val card: Color get() = state.value.card
    val panel: Color get() = state.value.panel
    val stroke: Color get() = state.value.stroke

    val tx: Color get() = state.value.tx
    val tx2: Color get() = state.value.tx2
    val tx3: Color get() = state.value.tx3
    val off: Color get() = state.value.off

    val accent: Color get() = state.value.accent
    val accent2: Color get() = state.value.accent2
    val onAccent: Color get() = state.value.onAccent
    val accentSoft: Color get() = state.value.accentSoft
    val curve: Color get() = state.value.curve
    val shadowTint: Color get() = state.value.shadowTint
    val m: Color get() = state.value.accent2

    val ok: Color get() = state.value.ok
    val warn: Color get() = state.value.warn
    val danger: Color get() = state.value.danger

    val clusterPrime: Color get() = state.value.clusterPrime
    val clusterPerf: Color get() = state.value.clusterPerf
    val clusterEff: Color get() = state.value.clusterEff
    val clusterBal: Color get() = state.value.clusterBal

    val iconBgBlue: Color get() = state.value.iconBgBlue
    val iconBgCyan: Color get() = state.value.iconBgCyan
    val iconBgGreen: Color get() = state.value.iconBgGreen
    val iconBgOrange: Color get() = state.value.iconBgOrange
    val iconBgPurple: Color get() = state.value.iconBgPurple
    val iconBgYellow: Color get() = state.value.iconBgYellow

    // Miuix 语义补充（新组件使用）
    val surface: Color get() = state.value.card
    val surfaceSunken: Color get() = state.value.surfaceSunken
    val divider: Color get() = state.value.divider
    val onAccentSoft: Color get() = state.value.onAccentSoft

    // v3.1 玻璃材质令牌
    val glassFill: Color get() = state.value.glassFill
    val glassTint: Color get() = state.value.glassTint
    val glassRimLight: Color get() = state.value.glassRimLight
    val glassRimShade: Color get() = state.value.glassRimShade
    val glassShadow: Color get() = state.value.glassShadow
}

// 提权档位标注：颜色改为计算属性，随主题即时变化
enum class Priv(val tag: String) {
    FREE("—"),
    SHIZUKU("S"),
    ROOT("R");

    val color: Color
        get() = when (this) {
            FREE -> Ink.off
            SHIZUKU -> Ink.accent
            ROOT -> Ink.warn
        }

    companion object {
        fun of(tag: String) = entries.firstOrNull { it.tag == tag } ?: FREE
    }
}

// 数据状态机
enum class DataState { OK, NEED_PRIV, UNAVAILABLE, STALE }
