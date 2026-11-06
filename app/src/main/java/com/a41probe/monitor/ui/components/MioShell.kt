package com.a41probe.monitor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.ui.glass.DockBottomGap
import com.a41probe.monitor.ui.glass.DockHeight
import com.a41probe.monitor.ui.glass.DockHorizontalInset
import com.a41probe.monitor.ui.glass.DockTopGap
import com.a41probe.monitor.ui.glass.LocalMioChromeBackdrop
import com.a41probe.monitor.ui.glass.LocalMioGlass
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.MioGlassSurface
import com.a41probe.monitor.ui.glass.TopBarHeight
import com.a41probe.monitor.ui.glass.mioGlassGloss
import com.a41probe.monitor.ui.theme.Ink
import kotlinx.coroutines.delay

/** 底部导航项：图标 + 文案（BiliPai 底部 dock 的 5 项结构） */
data class MioTabItem(val label: String, val icon: ImageVector)

/**
 * 主 tab 顶栏（v3.1 真玻璃）：
 * 悬浮在内容之上，内容从状态栏下方穿过；滚动时模糊/着色逐渐增强（progress 0→1）。
 * 结构：品牌 + 机型 + 提权/新鲜度 chips + 设置齿轮。
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
    modifier: Modifier = Modifier,
) {
    val host = LocalMioGlass.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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

    MioGlassSurface(
        modifier = modifier.fillMaxWidth().height(statusTop + TopBarHeight),
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        role = MioGlassRole.CHROME,
        // 液态分支在 draw 阶段读进度，避免滚动时每个像素重组并重建 effect 链
        progressProvider = { host?.chromeProgress ?: 0f },
    ) {
        // 状态栏区域保护：玻璃之上再压一层向下渐隐的底色，确保状态栏图标可读
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusTop)
                .background(
                    Brush.verticalGradient(
                        0f to Ink.bg.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusTop, start = 16.dp, end = 10.dp),
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
                    val tr = rememberInfiniteTransition(label = "staleTop")
                    tr.animateFloat(
                        initialValue = 0.45f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(durationMillis = 600),
                            RepeatMode.Reverse,
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
            Spacer(Modifier.width(8.dp))
            // 玻璃圆形齿轮（内层玻璃-lite：不再嵌套第二层模糊）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Ink.glassFill.copy(alpha = 0.34f))
                        .mioGlassGloss(CircleShape, lightAlpha = 0.5f, sheenAlpha = 0.10f),
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
 * 底部导航栏（v3.4）：
 *
 *  - API 33+ 且玻璃完整档：**直接渲染 BiliPai 原版 FloatingBottomBar**（见 MioBiliPaiBottomBar，
 *    vendor 自 BiliPai v0.7.8：壳体玻璃 / 液态指示器 / 拖拽选择 / 重力高光全部为原码）；
 *  - 其它情况（API<33、简约/关闭档，或用户在设置里切回 Mio 简版）：Mio 简版 Haze 玻璃 dock。
 */
@Composable
fun MioBottomDock(
    items: List<MioTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val backdrop = LocalMioChromeBackdrop.current
    if (backdrop != null && BottomBarStyleConfig.current() == BottomBarStyle.BILIPAI) {
        MioBiliPaiBottomBar(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier,
        )
        return
    }

    // ---- Mio 简版降级 dock（v3.2 Haze 材质）----
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = DockHorizontalInset,
                end = DockHorizontalInset,
                top = DockTopGap,
                bottom = DockBottomGap,
            ),
    ) {
        MioGlassSurface(
            modifier = Modifier.fillMaxWidth().height(DockHeight),
            shape = RoundedCornerShape(32.dp),
            role = MioGlassRole.DOCK,
        ) {
            Box(Modifier.fillMaxSize()) {
                // 选中胶囊：主题色半透明玻璃 + 高光边（降级路径不再叠第二层模糊/形变）
                val frac = 1f / items.size
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .graphicsLayer { translationX = pos * size.width }
                        .padding(horizontal = 7.dp, vertical = 7.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Ink.accent.copy(alpha = 0.20f))
                        .mioGlassGloss(
                            RoundedCornerShape(25.dp),
                            lightAlpha = 0.32f,
                            sheenAlpha = 0.10f,
                        ),
                )
                Row(Modifier.fillMaxSize()) {
                    items.forEachIndexed { i, item ->
                        val active = i == selectedIndex
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val itemScale by animateFloatAsState(
                            targetValue = if (pressed) 0.92f else 1f,
                            animationSpec = tween(
                                durationMillis = if (pressed) 90 else 170,
                                easing = Motion.easing,
                            ),
                            label = "dockItemScale",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                ) {
                                    if (i != selectedIndex) haptics.tick()
                                    onSelect(i)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = if (active) 0.22f else 0.14f),
                                    modifier = Modifier
                                        .size(21.dp)
                                        .graphicsLayer { translationY = 0.7.dp.toPx() },
                                )
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = if (active) Ink.accent else Ink.tx2,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.label,
                                color = if (active) Ink.accent else Ink.tx2,
                                fontSize = 10.5.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(
                                            alpha = if (Ink.bg.luminance() < 0.5f) 0.45f else 0.18f,
                                        ),
                                        offset = Offset(0f, 1f),
                                        blurRadius = 2.5f,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}