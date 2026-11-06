package com.a41probe.monitor.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * 触感强度档位（设置 → 触感强度）。
 * LIGHT / MEDIUM / HEAVY 三档，持久化在 `mio_haptics`。
 */
enum class HapticLevel { LIGHT, MEDIUM, HEAVY }

/**
 * v3.11 触感执行曲线：不再只依赖“振幅是否被 ROM 尊重”。
 *
 * 部分 vivo/iQOO ROM 虽然报告 AMPLITUDE_CONTROL，但会忽略 App 传入的振幅，
 * 结果三档都退化成同一条连续震动。这里改成 **波形节奏 + 时长 + 振幅** 三重区分：
 *  - 即使振幅被忽略，三档的通断节奏和脉冲数量也明显不同；
 *  - 支持振幅的机型则在此基础上再得到强弱差异。
 */
internal data class HapticPattern(
    /** 波形时长数组：首项是延迟，之后交替按“振/停”循环。 */
    val timings: LongArray,
    val amplitudes: IntArray,
    /** -1=只播一次；0=从波形开头循环（按住时用）。 */
    val repeat: Int,
)

/** 单次点按/切换的曲线：轻 1 短脉冲、中 1 长脉冲、重 2 个脉冲。 */
internal fun hapticPressPattern(level: HapticLevel, isPress: Boolean): HapticPattern = when (level) {
    HapticLevel.LIGHT -> HapticPattern(
        timings = longArrayOf(0L, if (isPress) 8L else 6L),
        amplitudes = intArrayOf(0, 80),
        repeat = -1,
    )

    HapticLevel.MEDIUM -> HapticPattern(
        timings = longArrayOf(0L, if (isPress) 20L else 14L),
        amplitudes = intArrayOf(0, 170),
        repeat = -1,
    )

    HapticLevel.HEAVY -> HapticPattern(
        timings = longArrayOf(0L, if (isPress) 28L else 22L, 18L, 16L),
        amplitudes = intArrayOf(0, 255, 0, 180),
        repeat = -1,
    )
}

/** 按住持续震动的曲线：三档用不同占空比，重档接近连续。 */
internal fun hapticHoldPattern(level: HapticLevel): HapticPattern = when (level) {
    HapticLevel.LIGHT -> HapticPattern(
        timings = longArrayOf(0L, 10L, 14L),
        amplitudes = intArrayOf(0, 90, 0),
        repeat = 0,
    )

    HapticLevel.MEDIUM -> HapticPattern(
        timings = longArrayOf(0L, 22L, 6L),
        amplitudes = intArrayOf(0, 165, 0),
        repeat = 0,
    )

    HapticLevel.HEAVY -> HapticPattern(
        timings = longArrayOf(0L, 40L, 2L),
        amplitudes = intArrayOf(0, 255, 0),
        repeat = 0,
    )
}

/**
 * 触感反馈实现（v3.11）。
 *
 * 设计原则：
 *  - 强度由**波形节奏 + 脉冲时长 + 振幅**共同表达，不假设 ROM 一定尊重振幅；
 *  - 尊重系统「触感反馈」总开关；无马达/系统不允许时静默降级，绝不崩溃；
 *  - 设置页按住「轻/中/重」持续震动，松手立刻 cancel。
 */
object MioHaptics {
    private const val PREFS = "mio_haptics"
    private const val KEY_LEVEL = "haptic_level"
    private const val HOLD_MS = 10_000L

    @Volatile private var enabled = true
    @Volatile private var level: HapticLevel = HapticLevel.MEDIUM

    fun init(ctx: Context) {
        enabled = systemEnabled(ctx)
        level = runCatching {
            when (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_LEVEL, HapticLevel.MEDIUM.ordinal)) {
                0 -> HapticLevel.LIGHT
                2 -> HapticLevel.HEAVY
                else -> HapticLevel.MEDIUM
            }
        }.getOrDefault(HapticLevel.MEDIUM)
    }

    fun set(ctx: Context, l: HapticLevel) {
        level = l
        // 用户可能刚在系统里打开/关闭触感总开关，这里重新读一次
        enabled = systemEnabled(ctx)
        runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_LEVEL, l.ordinal).apply()
        }
    }

    fun current(): HapticLevel = level

    /** 单次触感（Tab 切换 / 卡片按下）；波形失败时退回系统常量。 */
    fun play(ctx: Context, view: View?, isPress: Boolean) {
        if (!enabled) return
        val pattern = hapticPressPattern(level, isPress)
        if (vibratePattern(ctx, pattern)) return
        // 无马达/波形被拒绝时退回系统常量，尽量保留原生触感
        val constant = when (level) {
            HapticLevel.LIGHT -> HapticFeedbackConstants.CONTEXT_CLICK
            HapticLevel.MEDIUM -> HapticFeedbackConstants.LONG_PRESS
            HapticLevel.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        }
        runCatching { view?.performHapticFeedback(constant) }
    }

    /* ---------- 按住即持续震动（设置页轻/中/重三个键） ---------- */

    fun startHold(ctx: Context, l: HapticLevel) {
        if (!enabled) return
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.cancel()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION") v.vibrate(HOLD_MS)
                return@runCatching
            }
            val pattern = hapticHoldPattern(l)
            v.vibrate(
                VibrationEffect.createWaveform(
                    pattern.timings,
                    pattern.amplitudes,
                    pattern.repeat,
                )
            )
        }
    }

    /** 松手：立刻停止持续震动 */
    fun stopHold(ctx: Context) {
        runCatching { vibrator(ctx)?.cancel() }
    }

    private fun vibratePattern(ctx: Context, pattern: HapticPattern): Boolean {
        val v = vibrator(ctx) ?: return false
        if (!v.hasVibrator()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createWaveform(
                        pattern.timings,
                        pattern.amplitudes,
                        pattern.repeat,
                    )
                )
            } else {
                // 旧系统没有波形振幅：按总时长发一次，保证至少能震
                @Suppress("DEPRECATION")
                v.vibrate(pattern.timings.sum().coerceAtLeast(10L))
            }
            true
        }.getOrDefault(false)
    }

    private fun systemEnabled(ctx: Context): Boolean = runCatching {
        Settings.System.getInt(
            ctx.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) == 1
    }.getOrDefault(true)

    private fun vibrator(ctx: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()
}

/** Compose 侧取用：remember 一次，避免每次重组重建回调 */
class MioHapticsScope internal constructor(
    private val ctx: Context,
    private val view: View?,
) {
    /** Tab 切换 */
    fun tick() = MioHaptics.play(ctx, view, isPress = false)

    /** 卡片 / 按钮按下 */
    fun press() = MioHaptics.play(ctx, view, isPress = true)
}

@Composable
fun rememberHaptics(): MioHapticsScope {
    val ctx = LocalContext.current
    val view = LocalView.current
    return remember(ctx, view) { MioHapticsScope(ctx, view) }
}
