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
import com.a41probe.monitor.ui.theme.Ink
import com.android.purebilibili.feature.home.components.FloatingBottomBar
import com.android.purebilibili.feature.home.components.FloatingBottomBarColors
import com.android.purebilibili.feature.home.components.FloatingBottomBarItem
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
            liquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
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
