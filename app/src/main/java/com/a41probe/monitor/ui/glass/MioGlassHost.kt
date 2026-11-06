package com.a41probe.monitor.ui.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/* ============================================================
 * 液态玻璃宿主：Haze 源 + Miuix Backdrop 背板 + 滚动进度 + 能力
 *
 *  1) pageState     —— 背景层（页面底色 + 环境光 + 角色立绘），供内容卡片采样模糊；
 *  2) chromeState   —— 内容层（各屏 UI），供 API<33 的顶栏/dock/返回条 Haze 模糊；
 *  3) chromeBackdrop—— 内容层录制成的 Miuix Backdrop，供 API33+ 液态玻璃
 *                      （drawBackdrop + 折射 + 高光）采样。
 *
 * 卡片在内容层内部，因此只能采样「背景层」（iOS 式：玻璃透出壁纸，而不是模糊别的 UI）。
 * API33+ 完整档时 chromeBackdrop 取代 chromeState（同一份内容不重复录制）。
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

/**
 * 液态玻璃的光学采样背板（Miuix Backdrop）。
 * null 表示当前设备/档位不使用 Backdrop 引擎（API<33、LITE 或 OFF）。
 */
val LocalMioChromeBackdrop = staticCompositionLocalOf<ChromeBackdropSource?> { null }

/** 滚动进度回写口：ScrollAware 检测到后把页面滚动进度写进来，顶栏读它做渐强。 */
val LocalScrollFractionSink = staticCompositionLocalOf<MutableState<Float>?> { null }

/** 内容避让壳层的高度（tab 页/整页由各自 Shell 提供）。 */
val LocalMioChromeInsets = compositionLocalOf { MioChromeInsets.Zero }

/**
 * 创建玻璃宿主：两个 HazeState + 滚动进度状态 +（API33+）Miuix Backdrop 录制。
 * 放在 MainActivity 的最外层，包住「背景层 + 内容层 + 壳层 + 弹层」。
 */
@Composable
fun MioGlassHost(content: @Composable () -> Unit) {
    val chromeState = remember { HazeState() }
    val pageState = remember { HazeState() }
    val scrollFraction = remember { mutableStateOf(0f) }
    val host = remember { MioGlassHostState(chromeState, pageState, scrollFraction) }
    val capability = LocalMioGlassCapability.current
    // API 33+ 且完整档：内容层额外录制一份 Miuix Backdrop（顶栏/dock/返回条共享同一背板）
    val liquidSource = if (liquidChromeAvailable(capability)) {
        rememberChromeBackdropSource(
            drawContentToScreen = true,
            isolateContentDrawing = true,
        )
    } else {
        null
    }
    CompositionLocalProvider(
        LocalMioGlass provides host,
        LocalScrollFractionSink provides scrollFraction,
        LocalMioChromeBackdrop provides liquidSource,
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

/**
 * 内容层：API33+ 完整档录制为 Miuix Backdrop（供液态玻璃壳层采样）；
 * 其余情况标记为 Haze 源（供顶栏/dock/返回条模糊）。
 */
@Composable
fun MioChromeContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalMioGlass.current
    val liquidSource = LocalMioChromeBackdrop.current
    val m = when {
        liquidSource != null -> modifier.then(liquidSource.modifier)
        host != null -> modifier.hazeSource(host.chromeState)
        else -> modifier
    }
    Box(m, content = content)
}

/** 当前生效的玻璃能力（主题层提供）。 */
@Composable
fun currentGlassCapability(): GlassCapability = LocalMioGlassCapability.current
