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
import com.a41probe.monitor.ui.glass.resolveGlassVibrancy
import com.a41probe.monitor.ui.glass.resolveMioLiquidTuning
import com.a41probe.monitor.ui.glass.scaleGlassAlpha
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.7 玻璃透明度 / OriginOS 高级材质对齐的纯 JVM 单测：
 * 「低」= 参考档（强模糊 + 透彩色 + 低填充），中/高/极高逐级更透；
 * 可读性 scrim 与模糊/折射参数不随档位变化。
 */
class GlassTransparencyPolicyTest {

    private val levels = listOf(
        GlassTransparency.LOW,
        GlassTransparency.MEDIUM,
        GlassTransparency.HIGH,
        GlassTransparency.ULTRA,
    )

    @Test
    fun factorOrderingAndReferenceLevel() {
        assertEquals(1.00f, glassTransparencyFactor(GlassTransparency.LOW), 0.0001f)
        assertEquals(0.80f, glassTransparencyFactor(GlassTransparency.MEDIUM), 0.0001f)
        assertEquals(0.58f, glassTransparencyFactor(GlassTransparency.HIGH), 0.0001f)
        assertEquals(0.38f, glassTransparencyFactor(GlassTransparency.ULTRA), 0.0001f)
        for (i in 0 until levels.size - 1) {
            assertTrue(glassTransparencyFactor(levels[i]) > glassTransparencyFactor(levels[i + 1]))
        }
    }

    @Test
    fun scaleClampsAndIsMonotonic() {
        // 低档为参考档，不放大 alpha（但受 ceiling 保护）
        assertEquals(0.92f, scaleGlassAlpha(1f, GlassTransparency.LOW), 0.0001f)
        assertEquals(0.34f, scaleGlassAlpha(0.34f, GlassTransparency.LOW), 0.0001f)
        // 极高档不能低于 floor
        assertEquals(0.035f, scaleGlassAlpha(0.01f, GlassTransparency.ULTRA), 0.0001f)
        // 同一 base 下随档位升高单调递减
        var last = 2f
        levels.forEach { level ->
            val v = scaleGlassAlpha(0.40f, level)
            assertTrue("alpha 应随透明度档位递减：$level=$v", v < last)
            last = v
        }
    }

    @Test
    fun liquidTuningOnlySurfaceAlphaChangesAndLowIsReference() {
        val cap = resolveGlassCapability(GlassLevel.FULL, 35)
        for (role in listOf(MioGlassRole.CHROME, MioGlassRole.DOCK, MioGlassRole.INDICATOR)) {
            for (dark in listOf(false, true)) {
                val base = resolveMioLiquidTuning(role, cap, dark = dark, progress = 0.5f)
                if (!base.available) continue
                val low = applyMioLiquidTransparency(base, GlassTransparency.LOW)
                // 低档 = 材质基准（只改填充 alpha，其余字段必须完全一致）
                assertEquals(base.surfaceAlpha, low.surfaceAlpha, 0.0001f)
                assertEquals(base.scrimAlpha, low.scrimAlpha, 0.0001f)
                assertEquals(base.blurRadius, low.blurRadius)
                assertEquals(base.refractionAmount, low.refractionAmount)
                assertEquals(base.highlightAlpha, low.highlightAlpha, 0.0001f)
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
    fun geometryScalingSkipsDisabledGlassAndKeepsOtherFields() {
        val fullCap = resolveGlassCapability(GlassLevel.FULL, 33)
        for (role in MioGlassRole.entries) {
            for (dark in listOf(false, true)) {
                val base = resolveGlassGeometry(role, fullCap, dark = dark)
                // 低档 = 参考档
                assertEquals(base, applyGlassGeometryTransparency(base, GlassTransparency.LOW, enabled = true))
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
    fun originOsReferenceMaterialIsAlreadyTransparentAndVibrant() {
        // 「低」档必须是 OriginOS 高级材质观感：强模糊 + 低填充 + 明显边缘/高光 + 增艳
        val full33 = resolveGlassCapability(GlassLevel.FULL, 33)
        val cardLight = resolveGlassGeometry(MioGlassRole.CARD, full33, dark = false)
        val cardDark = resolveGlassGeometry(MioGlassRole.CARD, full33, dark = true)
        assertTrue("卡片基础填充要足够低，实际 ${cardLight.fillAlpha}", cardLight.fillAlpha <= 0.18f)
        assertTrue("深色卡片基础填充要足够低，实际 ${cardDark.fillAlpha}", cardDark.fillAlpha <= 0.22f)
        assertTrue("卡片模糊 >= 32dp", cardLight.blurRadius.value >= 32f)
        assertTrue("卡片要有玻璃折射边", cardLight.lensAlpha >= 0.24f)
        assertTrue("卡片要有边缘高光", cardLight.rimLightAlpha >= 0.5f)
        assertTrue("卡片需要透彩色增艳", resolveGlassVibrancy(MioGlassRole.CARD) >= 1.4f)

        // 液态 dock 同样要低填充 + 高饱和
        val dockLow = applyMioLiquidTransparency(
            resolveMioLiquidTuning(MioGlassRole.DOCK, full33, dark = false),
            GlassTransparency.LOW,
        )
        assertTrue("dock 低档填充 <=0.24，实际 ${dockLow.surfaceAlpha}", dockLow.surfaceAlpha <= 0.24f)
        assertTrue("dock 需要 vibrancy", dockLow.saturation >= 1.6f)
    }

    @Test
    fun vibrancyFollowsRole() {
        val card = resolveGlassVibrancy(MioGlassRole.CARD)
        val dock = resolveGlassVibrancy(MioGlassRole.DOCK)
        val chrome = resolveGlassVibrancy(MioGlassRole.CHROME)
        val sheet = resolveGlassVibrancy(MioGlassRole.SHEET)
        assertTrue("CARD 增艳最强", card >= 1.40f)
        assertTrue("DOCK 需要透彩色", dock >= 1.35f)
        assertTrue("CHROME 需要透彩色", chrome >= 1.30f)
        assertTrue("SHEET 保持克制", sheet in 1.0f..1.2f)
        assertTrue(card >= dock)
        assertTrue(dock >= chrome)
        assertTrue(chrome > sheet)
    }

    @Test
    fun biliPaiBottomBarTuningUsesAbsoluteTargetsAndKeepsReadability() {
        val base = resolveLiquidGlassTuning(progress = 0.5f)
        val expected = mapOf(
            GlassTransparency.LOW to 0.34f,
            GlassTransparency.MEDIUM to 0.26f,
            GlassTransparency.HIGH to 0.20f,
            GlassTransparency.ULTRA to 0.14f,
        )
        expected.forEach { (level, alpha) ->
            val tuned = scaleBottomBarTuning(base, level)
            assertEquals("$level surfaceAlpha", alpha, tuned.surfaceAlpha, 0.0001f)
            assertEquals(
                "可读性 scrim 不随档位变化",
                base.contentReadabilityScrimAlpha,
                tuned.contentReadabilityScrimAlpha,
                0.0001f,
            )
            assertEquals("指示器 tint 不随档位变化", base.indicatorTintAlpha, tuned.indicatorTintAlpha, 0.0001f)
        }
        // 低档 = 参考档，中/高/极高逐级更透
        val ordered = levels.map { scaleBottomBarTuning(base, it).surfaceAlpha }
        for (i in 0 until ordered.size - 1) {
            assertTrue("${levels[i]} > ${levels[i + 1]}", ordered[i] > ordered[i + 1])
        }
        assertTrue("最低透明度也保持透明", ordered.last() >= 0.10f)
    }
}
