package com.a41probe.monitor.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.theme.MioColors
import com.a41probe.monitor.ui.theme.Palettes
import com.a41probe.monitor.ui.theme.ThemeMode
import com.a41probe.monitor.ui.theme.resolveLiquidGlassEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主题解析的纯 JVM 单测：明暗派生、对比度、模式解析、玻璃降级。 */
class ThemeResolutionTest {

    @Test
    fun lightColorsFollowPalette() {
        val p = Palettes.AZURE
        val c = MioColors.from(p, dark = false)
        assertEquals(p.accent, c.accent)
        assertEquals(p.bg, c.bg)
        assertEquals(p.card, c.card)
        assertEquals(p.tx, c.tx)
        assertEquals(p.onAccent, c.onAccent)
    }

    @Test
    fun darkColorsUseNeutralSurfacesAndLightText() {
        val c = MioColors.from(Palettes.AZURE, dark = true)
        assertEquals(Color(0xFF0D0D0D), c.bg)
        assertEquals(Color(0xFF1A1A1A), c.card)
        assertEquals(Color(0xFF262626), c.panel)
        assertTrue("深色正文应是浅色", c.tx.luminance() > 0.5f)
        assertTrue("深色次要文字应可读", contrast(c.tx2, c.bg) >= 4.5)
    }

    @Test
    fun accentOnAccentMeetsMinContrastInBothModes() {
        for (p in Palettes.ALL) {
            val light = MioColors.from(p, dark = false)
            val dark = MioColors.from(p, dark = true)
            assertTrue(
                "浅色 ${p.id} accent/onAccent 对比度不足: ${contrast(light.accent, light.onAccent)}",
                contrast(light.accent, light.onAccent) >= 3.0,
            )
            assertTrue(
                "深色 ${p.id} accent/onAccent 对比度不足: ${contrast(dark.accent, dark.onAccent)}",
                contrast(dark.accent, dark.onAccent) >= 3.0,
            )
        }
    }

    @Test
    fun darkSurfaceSecondaryTextIsReadable() {
        for (p in Palettes.ALL) {
            val c = MioColors.from(p, dark = true)
            assertTrue("${p.id} 深色卡片次要文字", contrast(c.tx2, c.card) >= 4.5)
            assertTrue("${p.id} 深色卡片正文", contrast(c.tx, c.card) >= 7.0)
        }
    }

    @Test
    fun themeModeParsing() {
        assertEquals(ThemeMode.DARK, ThemeMode.of("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.of("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of("system"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of("nonsense"))
    }

    @Test
    fun paletteFallbackIsAzure() {
        assertEquals("azure", Palettes.of(null).id)
        assertEquals("azure", Palettes.of("does-not-exist").id)
    }

    @Test
    fun liquidGlassOnlyOnApi33PlusAndFullLevel() {
        assertFalse(resolveLiquidGlassEnabled(GlassLevel.FULL, 32))
        assertTrue(resolveLiquidGlassEnabled(GlassLevel.FULL, 33))
        assertTrue(resolveLiquidGlassEnabled(GlassLevel.FULL, 34))
        assertFalse(resolveLiquidGlassEnabled(GlassLevel.LITE, 34))
        assertFalse(resolveLiquidGlassEnabled(GlassLevel.OFF, 34))
    }

    /** WCAG 2.1 相对亮度对比度。 */
    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }

        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
