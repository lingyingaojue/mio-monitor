/*
 * Vendored from BiliPai v0.7.8 `app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt`
 * 的两个小函数（原样抽取，函数体未改动）：
 *   - resolveSharedBottomBarCapsuleShape  (BottomBar.kt:490)
 *   - resolveBottomBarDarkTheme           (BottomBar.kt:1412)
 * 另从同快照抽取：
 *   - resolveSegmentedControlIndicatorHeightDp (BottomBarLiquidSegmentedControl.kt:176)
 *   - BILIPAI_PROGRESSIVE_TOP_BLUR_START_FRACTION (ProgressiveTopChrome.kt:29)
 *     （LiquidGlassTuning.kt 的默认参数引用它）
 *
 * 抽取原因：这些来源文件都很大且依赖导航/设置/皮肤体系；FloatingBottomBar 只需要这些
 * 纯函数/常量，单独成文件避免整文件搬运。搬运记录见 design-system/VENDORED.md。
 */
package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.ui.AppShapes
import kotlin.math.min

internal fun resolveSharedBottomBarCapsuleShape(): Shape =
    AppShapes.SelectionIndicator

internal fun resolveBottomBarDarkTheme(backgroundColor: Color): Boolean {
    return backgroundColor.luminance() < 0.5f
}

private const val SEGMENTED_CONTROL_MIN_INDICATOR_ASPECT_RATIO = 1.6f

internal fun resolveSegmentedControlIndicatorHeightDp(
    slotWidthDp: Float,
    indicatorHeightDp: Float
): Float {
    if (slotWidthDp <= 0f || indicatorHeightDp <= 0f) return 0f
    return min(
        indicatorHeightDp,
        slotWidthDp / SEGMENTED_CONTROL_MIN_INDICATOR_ASPECT_RATIO
    )
}

/** 顶部渐进模糊起点比例（ProgressiveTopChrome.kt:29）。 */
internal const val BILIPAI_PROGRESSIVE_TOP_BLUR_START_FRACTION = 0f

