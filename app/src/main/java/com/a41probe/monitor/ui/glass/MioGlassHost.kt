package com.a41probe.monitor.ui.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.a41probe.monitor.ui.components.GlassLevel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/* ============================================================
 * 液态玻璃宿主：两个 Haze 源 + 滚动进度 + 能力
 *
 *  1) pageState    —— 背景层（页面底色 + 环境光 + 角色立绘），供内容卡片采样模糊；
 *  2) chromeState  —— 内容层（各屏 UI），供顶栏 / 底部 dock / 页面返回条采样模糊。
 *
 * 卡片在内容层内部，因此只能采样「背景层」（iOS 式：玻璃透出壁纸，而不是模糊别的 UI）。
 * ============================================================ */

@Stable
class MioGlassHostState(
    val chromeState: HazeState,
    val pageState: HazeState,
    val scrollFraction: MutableState<Float>,
) {
    /** 顶栏雾化进度 0..1（由页面滚动回写）。 */
    val chromeProgress: Float
        get() = resolveChromeProgress(scrollFraction.value)
}

/** 玻璃宿主（MainActivity 最外层提供）。 */
val LocalMioGlass = staticCompositionLocalOf<MioGlassHostState?> { null }

/** 主题层算出的能力（MioTheme 提供；表面/宿主都从这里读，避免双份状态）。 */
val LocalMioGlassCapability = staticCompositionLocalOf {
    resolveGlassCapability(GlassLevel.FULL, Build.VERSION.SDK_INT)
}

/** 滚动进度回写口：ScrollAware 检测到后把页面滚动进度写进来，顶栏读它做渐强。 */
val LocalScrollFractionSink = staticCompositionLocalOf<MutableState<Float>?> { null }

/** 内容避让壳层的高度（tab 页/整页由各自 Shell 提供）。 */
val LocalMioChromeInsets = compositionLocalOf { MioChromeInsets.Zero }

/**
 * 创建玻璃宿主：两个 HazeState + 滚动进度状态。
 * 放在 MainActivity 的最外层，包住「背景层 + 内容层 + 壳层 + 弹层」。
 */
@Composable
fun MioGlassHost(content: @Composable () -> Unit) {
    val chromeState = remember { HazeState() }
    val pageState = remember { HazeState() }
    val scrollFraction = remember { mutableStateOf(0f) }
    val host = remember { MioGlassHostState(chromeState, pageState, scrollFraction) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMioGlass provides host,
        LocalScrollFractionSink provides scrollFraction,
    ) {
        content()
    }
}

/** 背景层：标记为 Haze 源，内容卡片从它采样模糊。 */
@Composable
fun MioBackdropLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalMioGlass.current
    val m = if (host != null) modifier.hazeSource(host.pageState) else modifier
    Box(m, content = content)
}

/** 内容层：标记为 Haze 源，顶栏 / dock / 返回条从它采样模糊。 */
@Composable
fun MioChromeContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalMioGlass.current
    val m = if (host != null) modifier.hazeSource(host.chromeState) else modifier
    Box(m, content = content)
}

/** 当前生效的玻璃能力（主题层提供）。 */
@Composable
fun currentGlassCapability(): GlassCapability = LocalMioGlassCapability.current
