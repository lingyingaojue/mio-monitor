/*
 * Vendored from BiliPai v0.7.8 `app/src/main/java/com/android/purebilibili/core/util/ModifierExt.kt`
 * （HapticType + rememberHapticFeedback 原样抽取）。
 *
 * 抽取原因：ModifierExt.kt 还包含 bouncyClickable 等大量与 Mio 无关的 Modifier；这里只保留
 * FloatingBottomBar 需要的触感声明，函数体未改动。搬运记录见 design-system/VENDORED.md。
 */
package com.android.purebilibili.core.util

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/**
 * 触觉反馈类型枚举
 */
enum class HapticType {
    LIGHT,      // 轻触 (选择/切换)
    MEDIUM,     // 中等 (确认)
    HEAVY,      // 重击 (警告/删除)
    GESTURE_STEP, // 亮度/音量等离散调节刻度
    SELECTION   // 选择变化
}

/**
 * 触发触觉反馈
 *
 * - Android 12+: 使用新的 GESTURE_START/END 等常量
 * - 旧版本: 使用 LONG_PRESS/KEYBOARD_TAP 等
 */
@Composable
fun rememberHapticFeedback(
    scene: com.android.purebilibili.core.ui.AppHapticScene = com.android.purebilibili.core.ui.AppHapticScene.CONTROLS,
): (HapticType) -> Unit {
    val view = LocalView.current
    val config by rememberUpdatedState(com.android.purebilibili.core.ui.LocalAppThemeConfig.current)
    return remember(view, scene) {
        { type: HapticType ->
            val feedbackConstant = when (type) {
                HapticType.LIGHT -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.KEYBOARD_TAP
                    }
                }
                HapticType.MEDIUM -> HapticFeedbackConstants.LONG_PRESS
                HapticType.HEAVY -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.REJECT
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
                }
                HapticType.GESTURE_STEP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.SEGMENT_TICK
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                }
                HapticType.SELECTION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                }
            }
            // 全局触感反馈开关检查
            if (config.hapticFeedbackEnabled && config.hapticSettings.isEnabled(scene)) {
                com.android.purebilibili.core.ui.AppActionHaptics.dispatch {
                    view.performHapticFeedback(feedbackConstant)
                }
            } else {
                com.android.purebilibili.core.ui.AppActionHaptics.suppressCurrentActionFeedback()
            }
        }
    }
}
