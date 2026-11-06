package com.a41probe.monitor.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.ui.theme.Ink
import kotlinx.coroutines.delay

/** 底部导航项：图标 + 文案（BiliPai 底部 dock 的 5 项结构） */
data class MioTabItem(val label: String, val icon: ImageVector)

/**
 * 主 tab 顶栏（BiliPai 首页式）：
 * 品牌 + 机型 + 提权/新鲜度 chips + 设置齿轮；滚动时底部细线渐入。
 */
@Composable
fun MioTopBar(
    model: String,
    authorized: Boolean,
    serviceUp: Boolean,
    rootAvailable: Boolean,
    lastSuccessAt: Long,
    onStatusClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    // 顶栏底色与页面一致（内容在 Column 布局中从其下方开始，无需透明渐变）
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val ageMs = if (lastSuccessAt > 0) (nowMs - lastSuccessAt).coerceAtLeast(0) else Long.MAX_VALUE
    val fresh = ageMs < 3000
    val animScale = rememberAnimatorScale()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.bg)
            .statusBarsPadding()
            .height(58.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Mio 澪",
                color = Ink.tx,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                model,
                color = Ink.tx3,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clickable(onClick = onStatusClick)) {
                    when {
                        rootAvailable -> DotBadge(Ink.warn, "Root")
                        authorized -> DotBadge(Ink.ok, "Shizuku")
                        serviceUp -> DotBadge(Ink.warn, "待授权")
                        else -> DotBadge(Ink.off, "免提权")
                    }
                }
                val stale = !fresh && ageMs != Long.MAX_VALUE && ageMs > 10_000
                val badgeAlpha = if (stale && animScale > 0f) {
                    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "staleTop")
                    tr.animateFloat(
                        initialValue = 0.45f,
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            tween(durationMillis = 600),
                            androidx.compose.animation.core.RepeatMode.Reverse,
                        ),
                        label = "blinkTop",
                    ).value
                } else 1f
                DotBadge(
                    if (fresh) Ink.ok else Ink.warn,
                    when {
                        ageMs == Long.MAX_VALUE -> "启动中"
                        fresh -> "实时"
                        else -> "停更 ${ageMs / 1000}s"
                    },
                    alpha = badgeAlpha,
                )
            }
            Spacer(Modifier.width(10.dp))
            // 设置：48dp 触控目标内放 40dp 圆形齿轮按钮（BiliPai 首页右上角入口）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Ink.panel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "设置",
                        tint = Ink.tx2,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

/**
 * 底部悬浮导航 dock（BiliPai 液态玻璃底栏的实心降级版）：
 * 66dp 高、32dp 大圆角、悬浮阴影、选中项胶囊高亮 + 液态指示器滑动。
 * API 33+ 且玻璃开启时视觉上更透，但无需 Miuix blur 依赖即可稳定工作。
 */
@Composable
fun MioBottomDock(
    items: List<MioTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    if (items.isEmpty()) return
    val animScale = rememberAnimatorScale()
    val haptics = rememberHaptics()
    val pos by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, items.lastIndex).toFloat(),
        animationSpec = tween(
            durationMillis = if (animScale > 0f) Motion.indicatorMs else 0,
            easing = Motion.easing,
        ),
        label = "dockIndicator",
    )
    val shape = RoundedCornerShape(33.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Ink.shadowTint.copy(alpha = 0.14f),
                    spotColor = Ink.shadowTint.copy(alpha = 0.12f),
                )
                .clip(shape)
                .background(Ink.card.copy(alpha = 0.94f))
                .border(1.dp, Ink.stroke.copy(alpha = 0.7f), shape),
        ) {
            // 液态指示器：随选中项平滑滑动
            val frac = 1f / items.size
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .graphicsLayer { translationX = pos * size.width }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Ink.accentSoft),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, item ->
                    val active = i == selectedIndex
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (i != selectedIndex) haptics.tick()
                                onSelect(i)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (active) Ink.accent else Ink.off,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label,
                            color = if (active) Ink.accent else Ink.off,
                            fontSize = 10.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
