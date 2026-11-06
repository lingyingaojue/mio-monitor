package com.a41probe.monitor.haptics

import com.a41probe.monitor.ui.components.HapticLevel
import com.a41probe.monitor.ui.components.HapticPattern
import com.a41probe.monitor.ui.components.hapticHoldPattern
import com.a41probe.monitor.ui.components.hapticPressPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.11：触感强度三档的波形映射单测。
 * 不依赖 Android 设备，验证三档在“时长/脉冲数/峰值振幅/占空比”上严格可区分。
 */
class HapticPatternPolicyTest {

    private fun onSegments(p: HapticPattern): List<Pair<Long, Int>> =
        (1 until p.timings.size step 2).map { p.timings[it] to p.amplitudes[it] }

    private fun onDuration(p: HapticPattern): Long = onSegments(p).sumOf { it.first }

    private fun energy(p: HapticPattern): Long = onSegments(p).sumOf { it.first * it.second }

    @Test
    fun patternsAreWellFormed() {
        HapticLevel.entries.forEach { level ->
            listOf(
                hapticPressPattern(level, isPress = true),
                hapticPressPattern(level, isPress = false),
                hapticHoldPattern(level),
            ).forEach { p ->
                assertEquals("首项必须是延迟 0", 0L, p.timings.first())
                assertEquals("timings/amplitudes 长度必须一致", p.timings.size, p.amplitudes.size)
                assertTrue("振幅不能越界", p.amplitudes.all { it in 0..255 })
            }
            assertEquals(-1, hapticPressPattern(level, true).repeat)
            assertEquals(0, hapticHoldPattern(level).repeat)
        }
    }

    @Test
    fun pressPatternsIncreaseInStrength() {
        val light = hapticPressPattern(HapticLevel.LIGHT, isPress = true)
        val medium = hapticPressPattern(HapticLevel.MEDIUM, isPress = true)
        val heavy = hapticPressPattern(HapticLevel.HEAVY, isPress = true)

        assertTrue("中应该比轻长", onDuration(medium) > onDuration(light))
        assertTrue("重应该比中长", onDuration(heavy) > onDuration(medium))
        assertTrue("重的能量应严格最大", energy(heavy) > energy(medium))
        assertTrue("中的能量应严格大于轻", energy(medium) > energy(light))

        assertEquals("轻为单脉冲", 1, onSegments(light).size)
        assertEquals("中为单脉冲", 1, onSegments(medium).size)
        assertEquals("重至少双脉冲", 2, onSegments(heavy).size)
    }

    @Test
    fun tickIsShorterThanPressForSameLevel() {
        HapticLevel.entries.forEach { level ->
            val press = hapticPressPattern(level, isPress = true)
            val tick = hapticPressPattern(level, isPress = false)
            assertTrue("$level Tab 切换应短于卡片按下", onDuration(tick) <= onDuration(press))
        }
    }

    @Test
    fun holdPatternsUseDifferentDutyCycles() {
        val light = hapticHoldPattern(HapticLevel.LIGHT)
        val medium = hapticHoldPattern(HapticLevel.MEDIUM)
        val heavy = hapticHoldPattern(HapticLevel.HEAVY)

        // timings: [0, on, off]；off 越大越“轻”
        val lightOff = light.timings[2]
        val mediumOff = medium.timings[2]
        val heavyOff = heavy.timings[2]
        assertTrue("轻档停顿应最长", lightOff > mediumOff)
        assertTrue("重档停顿应最短（接近连续）", mediumOff > heavyOff)

        val lightPeak = light.amplitudes.max()
        val mediumPeak = medium.amplitudes.max()
        val heavyPeak = heavy.amplitudes.max()
        assertTrue("峰值振幅必须递增", lightPeak < mediumPeak && mediumPeak < heavyPeak)
        assertEquals("重档峰值拉满", 255, heavyPeak)
    }
}
