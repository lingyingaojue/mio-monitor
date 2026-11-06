package com.a41probe.monitor.glass

import com.a41probe.monitor.ui.components.BottomBarStyle
import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.components.GlassTransparency
import com.a41probe.monitor.ui.components.InterfaceTexture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.10：设置页「界面质感」三档的映射单测。
 * 一个选择同时决定玻璃档位、透明度、底部栏实现。
 */
class InterfaceTexturePolicyTest {

    @Test
    fun exactlyThreeModesWithExpectedLabels() {
        assertEquals(3, InterfaceTexture.entries.size)
        assertEquals(
            listOf("关闭", "Mio 简版", "全透"),
            InterfaceTexture.entries.map { it.label },
        )
    }

    @Test
    fun fullClearMapsToFullGlassUltraLiquidBar() {
        val mode = InterfaceTexture.FULL_CLEAR
        assertEquals(GlassLevel.FULL, mode.glassLevel)
        assertEquals(GlassTransparency.ULTRA, mode.transparency)
        assertEquals(BottomBarStyle.BILIPAI, mode.bottomBarStyle)
        assertTrue(mode.summary.contains("全透"))
    }

    @Test
    fun mioSimpleMapsToLiteMediumMioBar() {
        val mode = InterfaceTexture.MIO_SIMPLE
        assertEquals(GlassLevel.LITE, mode.glassLevel)
        assertEquals(GlassTransparency.MEDIUM, mode.transparency)
        assertEquals(BottomBarStyle.MIO, mode.bottomBarStyle)
    }

    @Test
    fun offMapsToSolidMioBar() {
        val mode = InterfaceTexture.OFF
        assertEquals(GlassLevel.OFF, mode.glassLevel)
        assertEquals(GlassTransparency.MEDIUM, mode.transparency)
        assertEquals(BottomBarStyle.MIO, mode.bottomBarStyle)
    }

    @Test
    fun legacyGlassLevelFallsIntoClosestMode() {
        assertEquals(InterfaceTexture.OFF, InterfaceTexture.fromGlassLevel(GlassLevel.OFF))
        assertEquals(InterfaceTexture.MIO_SIMPLE, InterfaceTexture.fromGlassLevel(GlassLevel.LITE))
        assertEquals(InterfaceTexture.FULL_CLEAR, InterfaceTexture.fromGlassLevel(GlassLevel.FULL))
    }
}
