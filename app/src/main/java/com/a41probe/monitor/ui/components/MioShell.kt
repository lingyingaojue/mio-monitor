package com.a41probe.monitor.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.a41probe.monitor.ui.glass.LocalMioGlass
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.MioGlassSurface
import com.a41probe.monitor.ui.glass.TopBarHeight
import com.a41probe.monitor.ui.glass.currentGlassCapability
import com.a41probe.monitor.ui.glass.mioGlassGloss
import com.a41probe.monitor.ui.glass.mioGlassSweep
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
    val progress = host?.chromeProgress ?: 0f
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
        progress = progress,
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
 * 底部悬浮玻璃 dock（v3.2 液态玻璃）：
 * 低不透明度真模糊 + 边缘折射环 + 镜面高光 + 缓慢扫过的液态高光；
 * 内容从 dock 下方滚过并被实时模糊；选中项为玻璃胶囊 + 液态指示器滑动。
 */
@Composable
fun MioBottomDock(
    items: List<MioTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val animScale = rememberAnimatorScale()
    val haptics = rememberHaptics()
    val capability = currentGlassCapability()
    val pos by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, items.lastIndex).toFloat(),
        animationSpec = tween(
            durationMillis = if (animScale > 0f) Motion.indicatorMs else 0,
            easing = Motion.easing,
        ),
        label = "dockIndicator",
    )
    // 液态高光扫过（Reduce Motion / 无模糊引擎时关闭）
    val sweep = if (animScale > 0f && capability.realtimeBlur) {
        val tr = rememberInfiniteTransition(label = "dockSweep")
        tr.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 7500, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "sweep",
        ).value
    } else {
        0f
    }
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
                // 液晶扫光：低频、低强度，只为"液态"感
                if (sweep > 0f) {
                    Box(Modifier.matchParentSize().mioGlassSweep(sweep, 0.055f))
                }
                // 选中胶囊：主题色半透明玻璃 + 内高光 + 外发光（不嵌套模糊层）
                val frac = 1f / items.size
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .graphicsLayer { translationX = pos * size.width }
                        .padding(horizontal = 7.dp, vertical = 7.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(25.dp),
                            clip = false,
                            ambientColor = Ink.accent.copy(alpha = 0.28f),
                            spotColor = Ink.accent.copy(alpha = 0.18f),
                        )
                        .clip(RoundedCornerShape(25.dp))
                        .background(Ink.accent.copy(alpha = 0.20f))
                        .mioGlassGloss(
                            RoundedCornerShape(25.dp),
                            lightAlpha = 0.30f,
                            sheenAlpha = 0.12f,
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
                            // 图标：底层偏移一层半透明黑影，保证低 alpha 玻璃上依旧清晰
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
