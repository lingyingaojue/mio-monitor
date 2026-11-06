package com.a41probe.monitor.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.ui.theme.Ink
import com.a41probe.monitor.ui.theme.SpecialConfig

/**
 * 页面背景立绘（Miuix + 液态玻璃版）：澪固定立于内容区右下，作为玻璃卡片的
 * 模糊采样底纹——卡片背后要有细节，实时模糊才看得出「液态玻璃」。
 *
 * - 立绘不透明度随主题：浅色 0.68 / 深色 0.48（比 v3.0 提高，供玻璃透出细节）；
 * - 顶部叠一层更透的 bg 渐变遮罩：保证大标题/卡片可读，同时不把角色盖死；
 * - art==0 不绘制（角色素材缺失的页面自然为空）；
 * - tab 切换时 220ms 交叉淡入；离屏缓存避免滚动时重复绘制全幅 PNG。
 * 注意：本组件只画立绘与可读性遮罩，页面底色/环境光由 MioAmbientBackground 负责。
 */
@Composable
fun MioArtBackground(art: Int, modifier: Modifier = Modifier) {
    if (art == 0) return
    // 深色底判断：Ink.bg 亮度 < 0.5 视为深色主题（Ink 令牌本身随主题即时变化）
    val dark = Ink.bg.luminance() < 0.5f
    val artAlpha = if (dark) 0.48f else 0.68f
    AnimatedContent(
        targetState = art,
        transitionSpec = {
            fadeIn(tween(220)).togetherWith(fadeOut(tween(220)))
        },
        label = "mioArt",
    ) { a ->
        Box(modifier.fillMaxSize()) {
            Image(
                painter = painterResource(a),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                // v0.28.3: 离屏缓存——立绘成为固定位图，滚动时 GPU 直接合成缓存
                // 层，不再每帧重新绘制 420dp 全幅透明 PNG（滑动卡顿根因之一）
                modifier = Modifier.align(Alignment.BottomEnd).height(420.dp)
                    .graphicsLayer {
                        alpha = artAlpha
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            )
            // 可读性遮罩（v3.1 更透）：顶部仍压住大标题区，中段快速渐隐，
            // 下半屏完全透明——给玻璃卡片留出可采样的立绘细节，又不影响文字可读。
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Ink.bg.copy(alpha = 0.85f),
                        0.30f to Ink.bg.copy(alpha = 0.50f),
                        0.58f to Color.Transparent,
                        1f to Color.Transparent,
                    )
                )
            )
        }
    }
}

/**
 * 当前角色的表情小图（v1.3.0）：空态、提示、引导统一用它，随角色预设自动换人。
 * key 取自 expr 表：`think_head / hi_head / shy_head / wow_head / go_head`（缺项自动回落澪）。
 * 尺寸由调用方通过 modifier 控制，默认按 Fit 居中，不会裁到脸；
 * 大圆角与页面卡片语言一致。
 */
@Composable
fun SpecialFace(
    key: String,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    scale: ContentScale = ContentScale.Fit,
) {
    val res = SpecialConfig.current().exprFor(key)
    if (res == 0) return   // 素材缺失：不画，也不留空占位
    Box(modifier.clip(RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            contentScale = scale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 页面大标题（Miuix 版）：32sp SemiBold 主标题 + 14sp 次级灰副标题，
 * 上 20dp / 下 4dp，与 BiliPai 设置页的大标题节奏一致。
 * 置于 LazyColumn 首个 item；与 MioArtBackground 搭配——立绘在背景层，标题在内容流顶部。
 */
@Composable
fun MioPageTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text(
            title,
            color = Ink.tx,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            color = Ink.tx2,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}
