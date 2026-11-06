package com.a41probe.monitor.glass

import androidx.compose.ui.graphics.Color
import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.glass.liquidEngineLabel
import com.a41probe.monitor.ui.glass.resolveGlassCapability
import com.android.purebilibili.feature.home.components.resolveBottomBarDarkTheme
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import com.android.purebilibili.feature.home.components.resolveSegmentedControlIndicatorHeightDp
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.4：验证底部导航栏使用的是 **BiliPai 原版参数/工具函数**（vendor 代码），
 * 而不是 Mio 自研材质。
 */
class BiliPaiVendoredTuningTest {

    @Test
    fun biliPaiBalancedTuningIsUsedForBottomBar() {
        val tuning = resolveLiquidGlassTuning(progress = 0.5f)
        // 这些是 BiliPai Balanced 档的原始参数（LiquidGlassTuning.kt resolveLiquidGlassTuning）
        assertEquals(4f, tuning.backdropBlurRadius, 0.001f)      // 背板模糊 4dp
        assertEquals(0.40f, tuning.surfaceAlpha, 0.001f)         // 表面 40%
        assertEquals(1.5f, tuning.saturation, 0.001f)            // vibrancy 1.5
        assertEquals(24f, tuning.refractionHeight, 0.001f)       // 壳体折射 24dp
        assertEquals(24f, tuning.refractionAmount, 0.001f)       // 壳体折射强度 24dp
        assertEquals(0f, tuning.shellChromaticAberrationAmount, 0.001f) // 壳体无色散
        assertEquals(0.5f, tuning.indicatorChromaticAberrationAmount, 0.001f) // 指示器 0.5 色散
    }

    @Test
    fun biliPaiGeometryHelpersAreUsed() {
        // 胶囊形状来自 BiliPai AppShapes.SelectionIndicator
        assertNotNull(resolveSharedBottomBarCapsuleShape())
        // segmented 指示器高度限制（BiliPai BottomBarLiquidSegmentedControl.kt）
        assertEquals(20f, resolveSegmentedControlIndicatorHeightDp(slotWidthDp = 32f, indicatorHeightDp = 56f), 0.001f)
        assertEquals(0f, resolveSegmentedControlIndicatorHeightDp(slotWidthDp = 0f, indicatorHeightDp = 56f), 0.001f)
        // BiliPai 明暗判定
        assertTrue(resolveBottomBarDarkTheme(Color(0xFF0D0D0D)))
        assertFalse(resolveBottomBarDarkTheme(Color.White))
    }

    @Test
    fun engineLabelReflectsBiliPaiBottomBar() {
        val full35 = resolveGlassCapability(GlassLevel.FULL, 35)
        assertTrue(liquidEngineLabel(full35).contains("BiliPai FloatingBottomBar"))
        assertTrue(liquidEngineLabel(full35, bottomBarBiliPai = false).contains("Mio 简版"))
        // API33+ 简约档不再误报"系统不支持折射"
        val lite35 = resolveGlassCapability(GlassLevel.LITE, 35)
        assertTrue(liquidEngineLabel(lite35).contains("简约档"))
        // 31/32 与低版本文案保持如实
        assertTrue(liquidEngineLabel(resolveGlassCapability(GlassLevel.FULL, 31)).contains("系统不支持折射"))
        assertTrue(liquidEngineLabel(resolveGlassCapability(GlassLevel.FULL, 26)).contains("半透明"))
        assertTrue(liquidEngineLabel(resolveGlassCapability(GlassLevel.OFF, 35)).contains("实色"))
    }
}
