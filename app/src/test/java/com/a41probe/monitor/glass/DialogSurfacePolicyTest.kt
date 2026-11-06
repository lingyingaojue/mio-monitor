package com.a41probe.monitor.glass

import androidx.compose.ui.unit.dp
import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.components.GlassTransparency
import com.a41probe.monitor.ui.glass.MioDialogContainerAlpha
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.MioSheetMinFillAlpha
import com.a41probe.monitor.ui.glass.applyGlassGeometryTransparency
import com.a41probe.monitor.ui.glass.resolveGlassCapability
import com.a41probe.monitor.ui.glass.resolveGlassGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.9：弹窗/弹层可读性策略的单测。
 * 弹窗固定高不透明；弹层在四档透明度下都有最低填充，避免文字被背景吞掉。
 */
class DialogSurfacePolicyTest {

    private val levels = listOf(
        GlassTransparency.LOW,
        GlassTransparency.MEDIUM,
        GlassTransparency.HIGH,
        GlassTransparency.ULTRA,
    )

    @Test
    fun dialogSurfaceIsAlwaysReadable() {
        assertTrue("弹窗底色必须足够不透明", MioDialogContainerAlpha >= 0.88f)
        assertTrue(MioDialogContainerAlpha <= 1f)
    }

    @Test
    fun sheetKeepsMinimumFillAcrossTransparencyLevels() {
        val cap = resolveGlassCapability(GlassLevel.FULL, 33)
        val sheet = resolveGlassGeometry(MioGlassRole.SHEET, cap, dark = false)
        // 基础填充先抬到可读区间
        assertTrue("SHEET 基础填充 >=0.78，实际 ${sheet.fillAlpha}", sheet.fillAlpha >= 0.78f)
        levels.forEach { level ->
            val scaled = applyGlassGeometryTransparency(
                sheet,
                level,
                enabled = true,
                floorAlpha = MioSheetMinFillAlpha,
            )
            assertTrue("$level 弹层填充 >=0.72，实际 ${scaled.fillAlpha}", scaled.fillAlpha >= 0.72f)
        }
        assertTrue(MioSheetMinFillAlpha >= 0.70f)
    }

    @Test
    fun darkSheetIsAtLeastAsReadableAsLight() {
        val cap = resolveGlassCapability(GlassLevel.FULL, 33)
        val light = resolveGlassGeometry(MioGlassRole.SHEET, cap, dark = false)
        val dark = resolveGlassGeometry(MioGlassRole.SHEET, cap, dark = true)
        assertTrue(dark.fillAlpha >= light.fillAlpha)
        assertEquals(0.dp.value, light.blurRadius.value, 0.001f)
    }
}
