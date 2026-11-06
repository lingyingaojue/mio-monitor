package com.a41probe.monitor.glass

import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.components.GlassTransparency
import com.a41probe.monitor.ui.components.scaleBottomBarTuning
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.applyGlassGeometryTransparency
import com.a41probe.monitor.ui.glass.applyMioLiquidTransparency
import com.a41probe.monitor.ui.glass.glassTransparencyFactor
import com.a41probe.monitor.ui.glass.resolveGlassCapability
import com.a41probe.monitor.ui.glass.resolveGlassGeometry
import com.a41probe.monitor.ui.glass.resolveMioLiquidTuning
import com.a41probe.monitor.ui.glass.scaleGlassAlpha
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.6 全局玻璃透明度的纯 JVM 单测：
 * 档位排序、alpha 缩放/夹取、只改填充 alpha、默认中档与 3.5 基线完全一致。
 */
class GlassTransparencyPolicyTest {

    private val levels = listOf(
        GlassTransparency.LOW,
        GlassTransparency.MEDIUM,
        GlassTransparency.HIGH,
        GlassTransparency.ULTRA,
    )

    @Test
    fun factorOrderingAndDefault() {
        assertEquals(1f, glassTransparencyFactor(GlassTransparency.MEDIUM), 0.0001f)
        assertEquals(1.35f, glassTransparencyFactor(GlassTransparency.LOW), 0.0001f)
        assertEquals(0.70f, glassTransparencyFactor(GlassTransparency.HIGH), 0.0001f)
        assertEquals(0.45f, glassTransparencyFactor(GlassTransparency.ULTRA), 0.0001f)
        // 透明度从低到极高：alpha 倍率必须严格递减
        for (i in 0 until levels.size - 1) {
            assertTrue(glassTransparencyFactor(levels[i]) > glassTransparencyFactor(levels[i + 1]))
        }
    }

    @Test
    fun scaleClampsAndIsMonotonic() {
        // 低档不能把 alpha 推过 ceiling
        assertEquals(0.92f, scaleGlassAlpha(1f, GlassTransparency.LOW), 0.0001f)
        // 极高档不能低于 floor
        assertEquals(0.06f, scaleGlassAlpha(0.01f, GlassTransparency.ULTRA), 0.0001f)
        // 中档原样返回（在 clamp 区间内）
        assertEquals(0.34f, scaleGlassAlpha(0.34f, GlassTransparency.MEDIUM), 0.0001f)
        // 同一 base 下随档位升高单调递减
        var last = 2f
        levels.forEach { level ->
            val v = scaleGlassAlpha(0.40f, level)
            assertTrue("alpha 应随透明度档位递减：$level=$v", v < last)
            last = v
        }
    }

    @Test
    fun liquidTuningOnlySurfaceAlphaChangesAndMediumIsBaseline() {
        val cap = resolveGlassCapability(GlassLevel.FULL, 35)
        for (role in listOf(MioGlassRole.CHROME, MioGlassRole.DOCK, MioGlassRole.INDICATOR)) {
            for (dark in listOf(false, true)) {
                val base = resolveMioLiquidTuning(role, cap, dark = dark, progress = 0.5f)
                if (!base.available) continue
                val medium = applyMioLiquidTransparency(base, GlassTransparency.MEDIUM)
                // 默认中档 = 3.5 基线（只改填充 alpha，其余字段必须完全一致）
                assertEquals(base.surfaceAlpha, medium.surfaceAlpha, 0.0001f)
                assertEquals(base.scrimAlpha, medium.scrimAlpha, 0.0001f)
                assertEquals(base.blurRadius, medium.blurRadius)
                assertEquals(base.refractionAmount, medium.refractionAmount)
                assertEquals(base.highlightAlpha, medium.highlightAlpha, 0.0001f)
                // 四档单调递减
                var last = 2f
                levels.forEach { level ->
                    val alpha = applyMioLiquidTransparency(base, level).surfaceAlpha
                    assertTrue("${role}/${dark}: $level=$alpha", alpha < last)
                    last = alpha
                }
                // 可读性 scrim 永不随档位变化
                levels.forEach { level ->
                    assertEquals(
                        base.scrimAlpha,
                        applyMioLiquidTransparency(base, level).scrimAlpha,
                        0.0001f,
                    )
                }
            }
        }
    }

    @Test
    fun unavailableTuningIsUntouched() {
        val cap = resolveGlassCapability(GlassLevel.FULL, 31) // 无 Miuix Backdrop
        val unavailable = resolveMioLiquidTuning(MioGlassRole.DOCK, cap, dark = false)
        assertFalse(unavailable.available)
        levels.forEach { level ->
            assertEquals(unavailable, applyMioLiquidTransparency(unavailable, level))
        }
    }

    @Test
    fun geometryScalingSkipsDisabledGlassAndKeepsOtherFields() {
        val fullCap = resolveGlassCapability(GlassLevel.FULL, 33)
        for (role in MioGlassRole.entries) {
            for (dark in listOf(false, true)) {
                val base = resolveGlassGeometry(role, fullCap, dark = dark)
                val medium = applyGlassGeometryTransparency(base, GlassTransparency.MEDIUM, enabled = true)
                assertEquals(base, medium)
                var last = 2f
                levels.forEach { level ->
                    val geometry = applyGlassGeometryTransparency(base, level, enabled = true)
                    assertTrue("$role/$dark/$level", geometry.fillAlpha < last)
                    last = geometry.fillAlpha
                    // 除 fillAlpha 外的材质字段不变
                    assertEquals(base.blurRadius, geometry.blurRadius)
                    assertEquals(base.tintMix, geometry.tintMix, 0.0001f)
                    assertEquals(base.rimLightAlpha, geometry.rimLightAlpha, 0.0001f)
                    assertEquals(base.lensAlpha, geometry.lensAlpha, 0.0001f)
                    assertEquals(base.shadowAlpha, geometry.shadowAlpha, 0.0001f)
                }
            }
        }
        // 玻璃关闭：实色 fillAlpha 必须原样保留
        val offCap = resolveGlassCapability(GlassLevel.OFF, 33)
        val solid = resolveGlassGeometry(MioGlassRole.CARD, offCap, dark = false)
        assertEquals(1f, solid.fillAlpha, 0.0001f)
        levels.forEach { level ->
            assertEquals(1f, applyGlassGeometryTransparency(solid, level, enabled = false).fillAlpha, 0.0001f)
        }
    }

    @Test
    fun biliPaiBottomBarTuningScalesButKeepsReadability() {
        val base = resolveLiquidGlassTuning(progress = 0.5f)
        // 默认中档必须保留 vendored BiliPai 原始参数
        val medium = scaleBottomBarTuning(base, GlassTransparency.MEDIUM)
        assertEquals(base.surfaceAlpha, medium.surfaceAlpha, 0.0001f)
        assertEquals(base.whiteOverlayAlpha, medium.whiteOverlayAlpha, 0.0001f)
        assertEquals(base.contentReadabilityScrimAlpha, medium.contentReadabilityScrimAlpha, 0.0001f)
        assertEquals(base.indicatorTintAlpha, medium.indicatorTintAlpha, 0.0001f)
        // 高/极高档更透，且可读性 scrim 不变
        var last = 2f
        listOf(GlassTransparency.LOW, GlassTransparency.MEDIUM, GlassTransparency.HIGH, GlassTransparency.ULTRA)
            .forEach { level ->
                val tuned = scaleBottomBarTuning(base, level)
                assertTrue("$level surfaceAlpha=${tuned.surfaceAlpha}", tuned.surfaceAlpha < last)
                last = tuned.surfaceAlpha
                assertEquals(
                    base.contentReadabilityScrimAlpha,
                    tuned.contentReadabilityScrimAlpha,
                    0.0001f,
                )
                assertTrue(tuned.surfaceAlpha in 0.10f..0.85f)
            }
    }
}
