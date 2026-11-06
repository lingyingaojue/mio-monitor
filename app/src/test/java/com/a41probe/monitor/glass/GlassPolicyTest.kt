package com.a41probe.monitor.glass

import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.glass.GlassRenderMode
import com.a41probe.monitor.ui.glass.MioChromeInsets
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.cardBlurEnabled
import com.a41probe.monitor.ui.glass.resolveChromeBlurRadius
import com.a41probe.monitor.ui.glass.resolveChromeFillAlpha
import com.a41probe.monitor.ui.glass.resolveChromeProgress
import com.a41probe.monitor.ui.glass.resolveGlassCapability
import com.a41probe.monitor.ui.glass.resolveGlassGeometry
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 液态玻璃能力/材质策略的纯 JVM 单测。 */
class GlassPolicyTest {

    @Test
    fun capabilityMatrixBySdkAndLevel() {
        // OFF 永远关闭
        for (sdk in listOf(26, 30, 31, 32, 33, 35)) {
            val cap = resolveGlassCapability(GlassLevel.OFF, sdk)
            assertEquals(GlassRenderMode.OFF, cap.mode)
            assertFalse(cap.realtimeBlur)
            assertFalse(cap.liquidControls)
            assertFalse(cap.enabled)
        }
        // API < 31：半透明降级
        for (sdk in listOf(26, 29, 30)) {
            val cap = resolveGlassCapability(GlassLevel.FULL, sdk)
            assertEquals(GlassRenderMode.TINT, cap.mode)
            assertFalse(cap.realtimeBlur)
            assertFalse(cap.liquidControls)
            assertTrue(cap.enabled)
            assertTrue(cap.fullSurfaceBlur)
        }
        // API 31/32：真实模糊，但无 Miuix 液态控件
        for (sdk in listOf(31, 32)) {
            val cap = resolveGlassCapability(GlassLevel.FULL, sdk)
            assertEquals(GlassRenderMode.BLUR, cap.mode)
            assertTrue(cap.realtimeBlur)
            assertFalse(cap.liquidControls)
        }
        // API >= 33：液态 + 液态控件
        for (sdk in listOf(33, 34, 35)) {
            val cap = resolveGlassCapability(GlassLevel.FULL, sdk)
            assertEquals(GlassRenderMode.LIQUID, cap.mode)
            assertTrue(cap.realtimeBlur)
            assertTrue(cap.liquidControls)
        }
        // LITE：仍是玻璃，但不是全场景模糊
        val lite = resolveGlassCapability(GlassLevel.LITE, 33)
        assertTrue(lite.enabled)
        assertTrue(lite.realtimeBlur)
        assertFalse(lite.fullSurfaceBlur)
        assertFalse(lite.liquidControls)
    }

    @Test
    fun cardBlurOnlyInFullModeWithRealtimeBlur() {
        assertTrue(cardBlurEnabled(GlassLevel.FULL, resolveGlassCapability(GlassLevel.FULL, 31)))
        assertFalse(cardBlurEnabled(GlassLevel.LITE, resolveGlassCapability(GlassLevel.LITE, 31)))
        assertFalse(cardBlurEnabled(GlassLevel.FULL, resolveGlassCapability(GlassLevel.FULL, 26)))
        assertFalse(cardBlurEnabled(GlassLevel.OFF, resolveGlassCapability(GlassLevel.OFF, 34)))
    }

    @Test
    fun geometryFollowsRoleAndMode() {
        val full33 = resolveGlassCapability(GlassLevel.FULL, 33)
        val tint26 = resolveGlassCapability(GlassLevel.FULL, 26)
        val off = resolveGlassCapability(GlassLevel.OFF, 33)

        val card = resolveGlassGeometry(MioGlassRole.CARD, full33, dark = false)
        assertTrue("卡片应有模糊", card.blurRadius.value > 0f)
        assertTrue("卡片应采样背景", card.hazeSampling)

        val chrome = resolveGlassGeometry(MioGlassRole.CHROME, full33, dark = true)
        assertTrue("顶栏模糊应大于卡片", chrome.blurRadius.value > card.blurRadius.value)

        val tintCard = resolveGlassGeometry(MioGlassRole.CARD, tint26, dark = false)
        assertEquals(0f, tintCard.blurRadius.value, 0.001f)
        assertTrue("无模糊时填充更不透明", tintCard.fillAlpha > card.fillAlpha)

        val offCard = resolveGlassGeometry(MioGlassRole.CARD, off, dark = false)
        assertEquals(1f, offCard.fillAlpha, 0.001f)
        assertEquals(0f, offCard.blurRadius.value, 0.001f)
        assertEquals(0f, offCard.tintAlpha, 0.001f)
    }

    @Test
    fun chromeProgressRampsMonotonically() {
        assertEquals(0f, resolveChromeProgress(0f), 0.0001f)
        assertEquals(1f, resolveChromeProgress(1f), 0.0001f)
        var prev = -1f
        for (i in 0..20) {
            val p = resolveChromeProgress(i / 20f)
            assertTrue("progress 应单调递增", p >= prev)
            prev = p
        }
        // 越界输入被 clamp
        assertEquals(0f, resolveChromeProgress(-1f), 0.0001f)
        assertEquals(1f, resolveChromeProgress(2f), 0.0001f)
    }

    @Test
    fun chromeBlurAndFillRampWithProgress() {
        val baseBlur = 24.dp
        val atTop = resolveChromeBlurRadius(baseBlur, 0f).value
        val atBottom = resolveChromeBlurRadius(baseBlur, 1f).value
        assertTrue("顶部也应有轻微模糊", atTop > 0f)
        assertTrue("滚动后模糊更强", atBottom > atTop)
        assertEquals(baseBlur.value, atBottom, 0.001f)

        val fillTop = resolveChromeFillAlpha(0.6f, 0f)
        val fillBottom = resolveChromeFillAlpha(0.6f, 1f)
        assertTrue(fillTop > 0f)
        assertTrue(fillBottom > fillTop)
        assertTrue(fillBottom <= 1f)
    }

    @Test
    fun chromeInsetsDefaultIsZero() {
        assertEquals(0f, MioChromeInsets.Zero.top.value, 0.001f)
        assertEquals(0f, MioChromeInsets.Zero.bottom.value, 0.001f)
    }
}
