package com.a41probe.monitor.glass

import androidx.compose.ui.unit.dp
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
        assertTrue("卡片应有折射边", card.lensAlpha > 0f)

        val chrome = resolveGlassGeometry(MioGlassRole.CHROME, full33, dark = true)
        assertTrue("顶栏模糊应大于卡片", chrome.blurRadius.value > card.blurRadius.value)

        val tintCard = resolveGlassGeometry(MioGlassRole.CARD, tint26, dark = false)
        assertEquals(0f, tintCard.blurRadius.value, 0.001f)
        assertTrue("无模糊时填充更不透明", tintCard.fillAlpha > card.fillAlpha)

        val offCard = resolveGlassGeometry(MioGlassRole.CARD, off, dark = false)
        assertEquals(1f, offCard.fillAlpha, 0.001f)
        assertEquals(0f, offCard.blurRadius.value, 0.001f)
        assertEquals(0f, offCard.tintMix, 0.001f)
        assertEquals(0f, offCard.specularAlpha, 0.001f)
    }

    @Test
    fun dockMaterialIsTranslucentNotSolid() {
        val full31 = resolveGlassCapability(GlassLevel.FULL, 31)
        val full26 = resolveGlassCapability(GlassLevel.FULL, 26)

        val dockLight = resolveGlassGeometry(MioGlassRole.DOCK, full31, dark = false)
        val dockDark = resolveGlassGeometry(MioGlassRole.DOCK, full31, dark = true)
        // 关键回归：dock 必须"能透"（v3.1 是 0.60/0.62 = 视觉实色）
        assertTrue("dock 浅色填充必须明显低于 0.40，实际 ${dockLight.fillAlpha}", dockLight.fillAlpha in 0.18f..0.40f)
        assertTrue("dock 深色填充必须明显低于 0.48，实际 ${dockDark.fillAlpha}", dockDark.fillAlpha in 0.26f..0.48f)
        assertTrue("dock 模糊半径 >= 28dp", dockLight.blurRadius.value >= 28f)
        assertEquals(32.dp.value, dockLight.blurRadius.value, 0.001f)
        assertTrue("dock 要有主题色 tint", dockLight.tintMix > 0.02f)
        assertTrue("dock 要有折射环", dockLight.lensAlpha >= 0.25f)
        assertTrue("dock 要有流动高光", dockLight.specularAlpha > 0.02f)
        assertTrue(dockLight.hazeSampling)

        // 卡片同样必须透
        val cardLight = resolveGlassGeometry(MioGlassRole.CARD, full31, dark = false)
        val cardDark = resolveGlassGeometry(MioGlassRole.CARD, full31, dark = true)
        assertTrue("卡片浅色填充 <= 0.45，实际 ${cardLight.fillAlpha}", cardLight.fillAlpha <= 0.45f)
        assertTrue("卡片深色填充 <= 0.52，实际 ${cardDark.fillAlpha}", cardDark.fillAlpha <= 0.52f)

        // 系统无实时模糊时提高不透明度，但仍是半透明玻璃（不回到 0.6+ 实色）
        val dockTint26 = resolveGlassGeometry(MioGlassRole.DOCK, full26, dark = false)
        assertTrue(dockTint26.fillAlpha in 0.30f..0.58f)
        assertEquals(0f, dockTint26.blurRadius.value, 0.001f)
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
        val baseBlur = 32.dp
        val atTop = resolveChromeBlurRadius(baseBlur, 0f).value
        val atBottom = resolveChromeBlurRadius(baseBlur, 1f).value
        assertTrue("顶部也应有轻微模糊", atTop > 0f)
        assertTrue("滚动后模糊更强", atBottom > atTop)
        assertEquals(baseBlur.value, atBottom, 0.001f)

        val baseFill = 0.36f
        val fillTop = resolveChromeFillAlpha(baseFill, 0f)
        val fillBottom = resolveChromeFillAlpha(baseFill, 1f)
        assertTrue("滚到顶时顶栏应接近通透", fillTop <= 0.12f)
        assertTrue("滚动后顶栏应有遮挡力", fillBottom >= 0.30f)
        assertTrue(fillBottom <= 1f)
    }

    @Test
    fun chromeInsetsDefaultIsZero() {
        assertEquals(0f, MioChromeInsets.Zero.top.value, 0.001f)
        assertEquals(0f, MioChromeInsets.Zero.bottom.value, 0.001f)
    }
}
