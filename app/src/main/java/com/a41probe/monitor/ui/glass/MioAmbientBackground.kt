package com.a41probe.monitor.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.a41probe.monitor.ui.theme.Ink

/**
 * 页面底色的「环境光」层。
 *
 * 玻璃必须有可折射的内容：设置页/双模式页没有角色立绘，如果背景是纯色，
 * 卡片上的实时模糊就看不出效果。这里在页面底色上叠 3 团柔和的主题色径向渐变，
 * 既是视觉底层，也让全屏玻璃有真实的明暗层次（iOS 壁纸式）。
 *
 * 只有玻璃开启时才绘制；关闭玻璃时退回纯 Ink.bg（省一次全屏绘制）。
 */
@Composable
fun MioAmbientBackground(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = Ink.bg.luminance() < 0.5f
    if (!enabled) {
        Box(modifier.fillMaxSize().background(Ink.bg))
        return
    }
    val accent = Ink.accent
    val accent2 = Ink.accent2
    val cool = Ink.clusterPerf
    // v3.2：色团更艳、更大，尤其底部一团（dock 区域）——保证不滚动时玻璃下也有颜色可透
    val a1 = if (dark) 0.30f else 0.26f
    val a2 = if (dark) 0.24f else 0.22f
    val a3 = if (dark) 0.30f else 0.28f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.bg)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val topRight = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = a1), Color.Transparent),
                    center = Offset(w * 0.92f, h * 0.04f),
                    radius = w * 1.0f,
                )
                val leftMid = Brush.radialGradient(
                    colors = listOf(accent2.copy(alpha = a2), Color.Transparent),
                    center = Offset(w * 0.02f, h * 0.52f),
                    radius = w * 0.9f,
                )
                val bottomCenter = Brush.radialGradient(
                    colors = listOf(cool.copy(alpha = a3), Color.Transparent),
                    center = Offset(w * 0.55f, h * 0.99f),
                    radius = w * 1.15f,
                )
                onDrawBehind {
                    drawRect(topRight)
                    drawRect(leftMid)
                    drawRect(bottomCenter)
                }
            },
    )
}
