package com.a41probe.monitor.glass

import androidx.compose.ui.unit.dp
import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.liquidChromeAvailable
import com.a41probe.monitor.ui.glass.liquidEngineLabel
import com.a41probe.monitor.ui.glass.resolveGlassCapability
import com.a41probe.monitor.ui.glass.resolveMioLiquidTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mio 3.3 Miuix Backdrop 液态玻璃策略的纯 JVM 单测。 */
class MioLiquidPolicyTest {

    private fun cap(level: GlassLevel, sdk: Int) = resolveGlassCapability(level, sdk)

    @Test
    fun availableOnlyInFullModeOnApi33Plus() {
        // 33+ 完整档：可用
        for (sdk in listOf(33, 34, 35)) {
            assertTrue(liquidChromeAvailable(cap(GlassLevel.FULL, sdk)))
        }
        // 31/32：系统没有 RuntimeShader 折射，走 Haze
        for (sdk in listOf(31, 32)) {
            assertFalse(liquidChromeAvailable(cap(GlassLevel.FULL, sdk)))
        }
        // 更低版本：不可用
        for (sdk in listOf(26, 30)) {
            assertFalse(liquidChromeAvailable(cap(GlassLevel.FULL, sdk)))
        }
        // 简约/关闭档：不进液态玻璃（省电/实色）
        assertFalse(liquidChromeAvailable(cap(GlassLevel.LITE, 35)))
        assertFalse(liquidChromeAvailable(cap(GlassLevel.OFF, 35)))
    }

    @Test
    fun dockTuningIsRealGlassWithLensAndHighlight() {
        val full35 = cap(GlassLevel.FULL, 35)
        val light = resolveMioLiquidTuning(MioGlassRole.DOCK, full35, dark = false)
        val dark = resolveMioLiquidTuning(MioGlassRole.DOCK, full35, dark = true)

        assertTrue(light.available)
        assertTrue("dock 必须有背板模糊", light.blurRadius >= 8.dp)
        assertTrue("dock 必须有边缘折射", light.refractionHeight >= 16.dp)
        assertTrue("dock 必须有折射强度", light.refractionAmount >= 16.dp)
        assertTrue("dock 必须有 Bloom 高光", light.highlightAlpha >= 0.6f)
        assertTrue("dock 必须有悬浮投影", light.shadowRadius >= 10.dp)
        assertTrue("dock 必须有内阴影", light.innerShadowAlpha > 0f)
        assertTrue("dock 必须增艳", light.saturation >= 1.3f)
        // 亮/暗两套材质都必须"透"（< 0.45），不许退回实色
        assertTrue("亮色 dock 底色必须透，实际 ${light.surfaceAlpha}", light.surfaceAlpha in 0.20f..0.40f)
        assertTrue("深色 dock 底色必须透，实际 ${dark.surfaceAlpha}", dark.surfaceAlpha in 0.28f..0.48f)
    }

    @Test
    fun chromeRampsWithScrollProgress() {
        val full35 = cap(GlassLevel.FULL, 35)
        val atTop = resolveMioLiquidTuning(MioGlassRole.CHROME, full35, dark = false, progress = 0f)
        val scrolled = resolveMioLiquidTuning(MioGlassRole.CHROME, full35, dark = false, progress = 1f)

        assertTrue("滚到顶应接近通透", atTop.surfaceAlpha <= 0.10f)
        assertTrue("滚动后要有遮挡力", scrolled.surfaceAlpha >= 0.20f)
        assertTrue("滚动后模糊更强", scrolled.blurRadius > atTop.blurRadius)
        assertTrue("滚动后折射更强", scrolled.refractionAmount > atTop.refractionAmount)
        assertTrue("滚动后高光更强", scrolled.highlightAlpha > atTop.highlightAlpha)
        // 越界进度被 clamp
        val clamped = resolveMioLiquidTuning(MioGlassRole.CHROME, full35, dark = false, progress = 2f)
        assertEquals(scrolled.surfaceAlpha, clamped.surfaceAlpha, 0.0001f)
    }

    @Test
    fun indicatorHasChromaticAberrationAndAccentFill() {
        val full35 = cap(GlassLevel.FULL, 35)
        val indicator = resolveMioLiquidTuning(MioGlassRole.INDICATOR, full35, dark = false)
        assertTrue(indicator.available)
        assertTrue("指示器以 accent 为主色", indicator.tintMix >= 0.8f)
        assertTrue("指示器要有色散", indicator.chromaticAberration > 0.2f)
        assertTrue("指示器也要有折射", indicator.refractionHeight >= 6.dp)
    }

    @Test
    fun cardsAndSheetsDoNotUseBackdropLiquidGlass() {
        val full35 = cap(GlassLevel.FULL, 35)
        assertFalse(resolveMioLiquidTuning(MioGlassRole.CARD, full35, dark = false).available)
        assertFalse(resolveMioLiquidTuning(MioGlassRole.SHEET, full35, dark = false).available)
        // 覆盖层卡片（引导页）例外：采样内容背板，可用
        val overlay = resolveMioLiquidTuning(
            MioGlassRole.CARD, full35, dark = false, overlay = true,
        )
        assertTrue(overlay.available)
        assertTrue(overlay.surfaceAlpha in 0.10f..0.28f)
        // 不可用设备的任何角色都返回 Unavailable
        val api31 = cap(GlassLevel.FULL, 31)
        assertFalse(resolveMioLiquidTuning(MioGlassRole.DOCK, api31, dark = false).available)
        assertFalse(liquidEngineLabel(api31).contains("Backdrop"))
    }

    @Test
    fun engineLabelReflectsActualPath() {
        assertTrue(liquidEngineLabel(cap(GlassLevel.FULL, 35)).contains("Backdrop"))
        assertTrue(liquidEngineLabel(cap(GlassLevel.FULL, 31)).contains("Haze"))
        assertTrue(liquidEngineLabel(cap(GlassLevel.FULL, 26)).contains("半透明"))
        assertTrue(liquidEngineLabel(cap(GlassLevel.OFF, 35)).contains("实色"))
    }
}
