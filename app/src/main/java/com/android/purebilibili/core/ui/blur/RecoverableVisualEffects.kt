/*
 * Mio vendor patch — 截取 BiliPai v0.7.8 `app/src/main/java/com/android/purebilibili/core/ui/blur/
 * RecoverableVisualEffects.kt` 中 FloatingBottomBar 闭包实际用到的两个函数。
 *
 * BiliPai 原文件还依赖 BackgroundManager（前后台重建 haze state）与
 * AdaptiveScaffoldHazeHandle，属于首页 scaffold 生命周期基建；Mio 不搬运该链，
 * 这里保留原函数语义并固定为“始终可恢复/允许 RenderEffect”：
 *   - recoverableBlurEnabled(state) 原实现：未注册 gate 的 state → true（同一默认值）
 *   - shouldAllowRenderEffectBackedHazeEffect(sdkInt) 原实现逐字保留
 * 搬运记录见 design-system/VENDORED.md。
 */
package com.android.purebilibili.core.ui.blur

import android.os.Build
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState

@Composable
fun recoverableBlurEnabled(state: HazeState): Boolean = true

internal fun shouldAllowRenderEffectBackedHazeEffect(
    sdkInt: Int
): Boolean {
    return sdkInt >= Build.VERSION_CODES.S
}
