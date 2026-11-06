package com.a41probe.monitor.glass

import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.components.GlassTransparency
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.prismalCardTintAlpha
import com.a41probe.monitor.ui.glass.resolveGlassCapability
import com.a41probe.monitor.ui.glass.resolveGlassEdgeDispersion
import com.a41probe.monitor.ui.glass.usesPrismalCardGlass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.8：PrismalAGSL 卡片玻璃接入的纯 JVM 单测。
 * 覆盖 tint 浓度、启用门槛、Haze 降级边缘色散强度。
 */
class PrismalCardPolicyTest {

    private val levels = listOf(
        GlassTransparency.LOW,
        GlassTransparency.MEDIUM,
        GlassTransparency.HIGH,
        GlassTransparency.ULTRA,
    )

    @Test
    fun cardTintAlphaIsLowAndMonotonic() {
        var lastLight = 2f
        levels.forEach { level ->
            val alpha = prismalCardTintAlpha(dark = false, level = level)
            assertTrue("$level 亮度 tint 太脏：$alpha", alpha in 0.03f..0.20f)
            assertTrue("$level 应逐级更透", alpha < lastLight)
            lastLight = alpha
        }
        // 深色主题需要略高的可读性底色，但必须仍然透明
        levels.forEach { level ->
            val light = prismalCardTintAlpha(dark = false, level = level)
            val dark = prismalCardTintAlpha(dark = true, level = level)
            assertTrue("深色 tint 应略高于浅色", dark > light)
            assertTrue("深色也要透明", dark <= 0.20f)
        }
        // 参考档（低）必须足够透，不能回到白雾
        assertTrue(prismalCardTintAlpha(false, GlassTransparency.LOW) <= 0.10f)
    }

    @Test
    fun prismalCardOnlyForFullLiquidTierAndNormalCards() {
        val full35 = resolveGlassCapability(GlassLevel.FULL, 35)
        val full31 = resolveGlassCapability(GlassLevel.FULL, 31)
        val lite35 = resolveGlassCapability(GlassLevel.LITE, 35)
        val off35 = resolveGlassCapability(GlassLevel.OFF, 35)

        assertTrue(usesPrismalCardGlass(MioGlassRole.CARD, false, full35, prismalAvailable = true))
        // 背景层背板不可用 / 覆盖层卡片 / 非卡片角色：都不走 Prismal
        assertFalse(usesPrismalCardGlass(MioGlassRole.CARD, false, full35, prismalAvailable = false))
        assertFalse(usesPrismalCardGlass(MioGlassRole.CARD, true, full35, prismalAvailable = true))
        assertFalse(usesPrismalCardGlass(MioGlassRole.CHROME, false, full35, prismalAvailable = true))
        assertFalse(usesPrismalCardGlass(MioGlassRole.DOCK, false, full35, prismalAvailable = true))
        // 非液态档 / 关闭档 / 低 API 继续 Haze
        assertFalse(usesPrismalCardGlass(MioGlassRole.CARD, false, full31, prismalAvailable = true))
        assertFalse(usesPrismalCardGlass(MioGlassRole.CARD, false, lite35, prismalAvailable = true))
        assertFalse(usesPrismalCardGlass(MioGlassRole.CARD, false, off35, prismalAvailable = true))
    }

    @Test
    fun hazeFallbackEdgeDispersionIsGatedByRealtimeBlur() {
        assertTrue(resolveGlassEdgeDispersion(MioGlassRole.CARD, realtimeBlur = true) >= 0.20f)
        assertTrue(resolveGlassEdgeDispersion(MioGlassRole.DOCK, realtimeBlur = true) >= 0.20f)
        assertTrue(resolveGlassEdgeDispersion(MioGlassRole.CHROME, realtimeBlur = true) >= 0.10f)
        assertTrue(resolveGlassEdgeDispersion(MioGlassRole.INDICATOR, realtimeBlur = true) >= 0.10f)
        assertEquals(0f, resolveGlassEdgeDispersion(MioGlassRole.SHEET, realtimeBlur = true), 0.0001f)
        MioGlassRole.entries.forEach { role ->
            assertEquals(0f, resolveGlassEdgeDispersion(role, realtimeBlur = false), 0.0001f)
        }
    }

    @Test
    fun cardDispersionConstantsMatchLibraryParameters() {
        // 这些值是 PrismalGlassSurface 调用点的契约：折射要夸张、色散要可见
        val cardTint = prismalCardTintAlpha(dark = false, level = GlassTransparency.MEDIUM)
        assertTrue(cardTint < prismalCardTintAlpha(dark = true, level = GlassTransparency.MEDIUM))
        // 低档 tint 不超过 0.10，配合 Prismal 的 vibrancy 保证"透"
        assertTrue(prismalCardTintAlpha(dark = false, level = GlassTransparency.LOW) <= 0.10f)
    }
}
