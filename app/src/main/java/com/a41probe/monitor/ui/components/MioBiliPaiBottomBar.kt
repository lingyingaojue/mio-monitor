package com.a41probe.monitor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a41probe.monitor.ui.glass.LocalMioChromeBackdrop
import com.a41probe.monitor.ui.glass.scaleGlassAlpha
import com.a41probe.monitor.ui.theme.Ink
import com.android.purebilibili.feature.home.components.FloatingBottomBar
import com.android.purebilibili.feature.home.components.FloatingBottomBarColors
import com.android.purebilibili.feature.home.components.FloatingBottomBarItem
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.FloatingBottomBarMode
import com.android.purebilibili.feature.home.components.LocalFloatingBottomBarContentColor
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning

/**
 * Mio 3.4：底部导航栏直接使用 BiliPai 原版 `FloatingBottomBar`（vendor 自 BiliPai v0.7.8）。
 *
 * 本文件只做「Mio tab 状态 → BiliPai 组件参数」的映射，不是视觉实现：
 * 玻璃壳体、液态指示器、拖拽选择、按压回弹、重力高光、bloom/lens 全部来自 BiliPai 原码。
 * 背景采样复用 Mio 3.3 已录好的 `LocalMioChromeBackdrop`（Miuix Backdrop）。
 */
@Composable
fun MioBiliPaiBottomBar(
    items: List<MioTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalMioChromeBackdrop.current?.backdrop
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    ) {
        FloatingBottomBar(
            selectedIndex = { selectedIndex },
            onSelected = onSelect,
            onReselected = {},
            backdrop = backdrop,
            tabsCount = items.size,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            mode = FloatingBottomBarMode.LiquidGlass,
            colors = FloatingBottomBarColors(
                containerColor = Ink.panel,
                indicatorColor = Ink.accent,
                contentColor = Ink.tx2,
                activeContentColor = Ink.accent,
            ),
            shellHeight = 56.dp,
            indicatorHeight = 56.dp,
            contentHorizontalPadding = 12.dp,
            liquidGlassTuning = scaleBottomBarTuning(
                resolveLiquidGlassTuning(progress = 0.5f),
                GlassTransparencyConfig.current(),
            ),
        ) {
            items.forEachIndexed { index, item ->
                FloatingBottomBarItem(
                    onClick = { onSelect(index) },
                    selected = index == selectedIndex,
                    itemIndex = index,
                    iconCrossScaleEnabled = true,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = LocalFloatingBottomBarContentColor.current,
                    )
                }
            }
        }
    }
}
/**
 * v3.6 全局玻璃透明度：只缩放 BiliPai 壳体的 surfaceAlpha / 白色叠加，
 * 可读性 scrim 与指示器 tint 保持原值，避免文字对比度下降。
 */
internal fun scaleBottomBarTuning(
    tuning: LiquidGlassTuning,
    level: GlassTransparency,
): LiquidGlassTuning = tuning.copy(
    surfaceAlpha = scaleGlassAlpha(tuning.surfaceAlpha, level, floor = 0.10f, ceiling = 0.85f),
    whiteOverlayAlpha = scaleGlassAlpha(tuning.whiteOverlayAlpha, level, floor = 0f, ceiling = 0.50f),
)