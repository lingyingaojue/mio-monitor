package com.a41probe.monitor.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.a41probe.monitor.ui.glass.LocalScrollFractionSink

/* ============================================================
 * 玻璃档位 / 配置 / 滚动上下文
 *
 * v3.1 起玻璃材质由 ui/glass/MioGlass* 实现（Haze 实时模糊）：
 *  - GlassLevel.FULL：全场景玻璃（壳层 + 卡片 + 弹层）
 *  - GlassLevel.LITE：仅壳层模糊，卡片半透明（省电）
 *  - GlassLevel.OFF ：全部实色
 * 能力（是否有实时模糊）由 GlassPolicy 按 SDK 解析，本文件只负责持久化与滚动进度。
 * ============================================================ */

/** 玻璃档位 */
enum class GlassLevel { FULL, LITE, OFF }

/** 全局玻璃配置（SharedPreferences 持久化，Compose 状态驱动即时切换）。 */
object GlassConfig {
    private const val PREFS = "mio_glass"
    private const val KEY_LEVEL = "glass_level"

    private val levelState = mutableStateOf(GlassLevel.FULL)

    fun init(ctx: Context) {
        levelState.value = try {
            when (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_LEVEL, GlassLevel.FULL.ordinal)) {
                1 -> GlassLevel.LITE
                2 -> GlassLevel.OFF
                else -> GlassLevel.FULL
            }
        } catch (_: Exception) {
            GlassLevel.FULL
        }
    }

    fun set(ctx: Context, l: GlassLevel) {
        levelState.value = l
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_LEVEL, l.ordinal).apply()
        } catch (_: Exception) {
        }
    }

    fun current(): GlassLevel = levelState.value

    /** 用户档位（不再按 SDK 降级：能力由 GlassPolicy 表达，档位本身保持用户选择）。 */
    fun effective(): GlassLevel = levelState.value
}

/** 全局玻璃透明度档位（v3.6）：只影响玻璃填充 alpha，不影响模糊/折射/可读性 scrim。 */
enum class GlassTransparency { LOW, MEDIUM, HIGH, ULTRA }

/** 全局玻璃透明度配置（与玻璃档位共用 `mio_glass` prefs；默认中 = 3.5 当前外观）。 */
object GlassTransparencyConfig {
    private const val PREFS = "mio_glass"
    private const val KEY_TRANSPARENCY = "glass_transparency"

    private val transparencyState = mutableStateOf(GlassTransparency.MEDIUM)

    fun init(ctx: Context) {
        transparencyState.value = try {
            val ordinal = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TRANSPARENCY, GlassTransparency.MEDIUM.ordinal)
            GlassTransparency.entries.firstOrNull { it.ordinal == ordinal }
                ?: GlassTransparency.MEDIUM
        } catch (_: Exception) {
            GlassTransparency.MEDIUM
        }
    }

    fun current(): GlassTransparency = transparencyState.value

    fun set(ctx: Context, level: GlassTransparency) {
        transparencyState.value = level
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_TRANSPARENCY, level.ordinal).apply()
        } catch (_: Exception) {
        }
    }
}

/** 底部导航栏实现选择（v3.4：默认直接使用 BiliPai 原版 FloatingBottomBar）。 */
enum class BottomBarStyle { BILIPAI, MIO }

/** 底部导航栏实现配置（持久化；API<33 或玻璃关闭时自动回退 Mio 简版）。 */
object BottomBarStyleConfig {
    private const val PREFS = "mio_glass"
    private const val KEY_STYLE = "bottom_bar_style"

    private val styleState = mutableStateOf(BottomBarStyle.BILIPAI)

    fun init(ctx: Context) {
        styleState.value = try {
            when (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_STYLE, BottomBarStyle.BILIPAI.ordinal)) {
                1 -> BottomBarStyle.MIO
                else -> BottomBarStyle.BILIPAI
            }
        } catch (_: Exception) {
            BottomBarStyle.BILIPAI
        }
    }

    fun current(): BottomBarStyle = styleState.value

    fun set(ctx: Context, style: BottomBarStyle) {
        styleState.value = style
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_STYLE, style.ordinal).apply()
        } catch (_: Exception) {
        }
    }
}

/**
 * v3.10 界面质感三档：把原「液态玻璃 / 玻璃透明度 / 底部导航栏」收敛成一个选择。
 * 只改 UI 入口与档位组合，内部 GlassLevel/GlassTransparency/BottomBarStyle 仍保留。
 */
enum class InterfaceTexture(val label: String, val summary: String) {
    OFF("关闭", "Mio 最初样式：实色卡片 + Mio 简版底栏"),
    MIO_SIMPLE("Mio 简版", "半透卡片 + Mio 简版底栏"),
    FULL_CLEAR("全透", "全透玻璃卡片 + 液态玻璃底栏");

    val glassLevel: GlassLevel
        get() = when (this) {
            OFF -> GlassLevel.OFF
            MIO_SIMPLE -> GlassLevel.LITE
            FULL_CLEAR -> GlassLevel.FULL
        }

    val transparency: GlassTransparency
        get() = if (this == FULL_CLEAR) GlassTransparency.ULTRA else GlassTransparency.MEDIUM

    val bottomBarStyle: BottomBarStyle
        get() = if (this == FULL_CLEAR) BottomBarStyle.BILIPAI else BottomBarStyle.MIO

    companion object {
        fun fromGlassLevel(level: GlassLevel): InterfaceTexture = when (level) {
            GlassLevel.OFF -> OFF
            GlassLevel.LITE -> MIO_SIMPLE
            GlassLevel.FULL -> FULL_CLEAR
        }
    }
}

/** 界面质感读写：一次写入玻璃档位、透明度、底部栏三个旧键，保持向后兼容。 */
object InterfaceTextureConfig {
    val modes: List<InterfaceTexture> = InterfaceTexture.entries

    fun current(): InterfaceTexture = InterfaceTexture.fromGlassLevel(GlassConfig.current())

    fun select(ctx: Context, mode: InterfaceTexture) {
        GlassConfig.set(ctx, mode.glassLevel)
        GlassTransparencyConfig.set(ctx, mode.transparency)
        BottomBarStyleConfig.set(ctx, mode.bottomBarStyle)
    }

    /** 启动时把旧四档/旧底栏设置规范化到三档（旧 FULL 自动落到全透）。 */
    fun normalize(ctx: Context) = select(ctx, current())
}

/* ---- 滚动上下文：各页面 LazyColumn 写入，顶栏/动画组件读取 ---- */

/** 页面列表是否正在滚动（滚动中动画组件自动直切，避免掉帧） */
val LocalScrolling = compositionLocalOf { false }

/** 页面滚动进度 0..1（首个 item 滚出约 200dp 即满），供顶栏透明→模糊渐变 */
val LocalScrollFraction = compositionLocalOf { 0f }

/**
 * 页面接入滚动上下文的包裹层。用法：
 *   val listState = rememberLazyListState()
 *   ScrollAware(listState) { LazyColumn(state = listState) { ... } }
 *
 * v3.1：若外层存在 MioGlassHost（LocalScrollFractionSink），同步把进度回写给顶栏。
 */
@Composable
fun ScrollAware(listState: LazyListState, content: @Composable () -> Unit) {
    val ctx by remember(listState) {
        derivedStateOf {
            val frac = ((listState.firstVisibleItemIndex +
                listState.firstVisibleItemScrollOffset / 200f).coerceAtLeast(0f)).coerceAtMost(1f)
            listState.isScrollInProgress to frac
        }
    }
    // 回写给玻璃宿主的顶栏（顶栏是页面内容的兄弟节点，读不到页面内部的 Local）
    val sink = LocalScrollFractionSink.current
    if (sink != null) {
        LaunchedEffect(sink, listState) {
            snapshotFlow {
                ((listState.firstVisibleItemIndex +
                    listState.firstVisibleItemScrollOffset / 200f).coerceIn(0f, 1f))
            }.collect { sink.value = it }
        }
    }
    /* overscroll 柔和视觉变化：拉到顶/底还在拉时，页面轻微位移 + 极轻压缩，松手软弹簧归位 */
    val animScale = rememberAnimatorScale()
    var over by remember { mutableFloatStateOf(0f) }
    val conn = remember(listState, animScale) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource,
            ): Offset {
                if (available.y != 0f && animScale > 0f) {
                    over = (over + available.y * 0.28f).coerceIn(-70f, 70f)
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && animScale > 0f && over != 0f) {
                val a = Animatable(over)
                a.animateTo(0f, Motion.softSpring()) { over = value }
            }
        }
    }
    CompositionLocalProvider(
        LocalScrolling provides ctx.first,
        LocalScrollFraction provides ctx.second,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(conn)
                .graphicsLayer {
                    translationY = over * 0.30f
                    val k = 1f - kotlin.math.abs(over) * 0.00045f
                    scaleX = k
                    scaleY = k
                },
        ) { content() }
    }
}

/** 页面便捷入口：remember state + 滚动上下文一步完成 */
@Composable
fun rememberScrollAwareState(): LazyListState {
    val state = rememberLazyListState()
    val ctx by remember(state) {
        derivedStateOf {
            val frac = ((state.firstVisibleItemIndex +
                state.firstVisibleItemScrollOffset / 200f).coerceAtLeast(0f)).coerceAtMost(1f)
            state.isScrollInProgress to frac
        }
    }
    CompositionLocalProvider(
        LocalScrolling provides ctx.first,
        LocalScrollFraction provides ctx.second,
    ) { }
    return state
}
