package com.a41probe.monitor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.ui.theme.Ink
import kotlin.math.min

/**
 * 历史窗口容量（与 MonitorViewModel.updateHist 的 takeLast(60) 对齐）。
 * x 轴按固定槽位映射：最新数据始终在最右端，历史向左滚动；
 * 点数不足时左侧留空——打开页面曲线从右端出现，不再"最左一条、右侧空白"。
 */
private const val HIST_WINDOW = 60

// ===== 绘制缓存：Paint/数组文件级复用，避免每帧 new（GC 抖动 = 滚动掉帧）=====
// 注意：Android Paint 非线程安全，但 Canvas 绘制恒在主线程，单例缓存安全。
// 颜色一律在绘制时从 Ink 读取（主题切换即时生效，不再缓存旧色）。
private val axisPaint = android.graphics.Paint().apply {
    isAntiAlias = true
    typeface = android.graphics.Typeface.MONOSPACE
}
private val emptyPaint = android.graphics.Paint().apply {
    isAntiAlias = true
    typeface = android.graphics.Typeface.SANS_SERIF
}
private val valuePaint = android.graphics.Paint().apply {
    isAntiAlias = true
    typeface = android.graphics.Typeface.MONOSPACE
}
private val avgDashes = floatArrayOf(5f, 6f)
private val zeroDashes = floatArrayOf(4f, 5f)

/** 曲线线宽语义：单位 dp（默认 2.5dp，BiliPai 主曲线粗细） */
private fun DrawScope.strokePx(w: Float): Float = w.dp.toPx()

/** 最新点光晕半径（相对圆点半径的倍数） */
private const val HALO_SCALE = 2.6f
/** 最新点光晕透明度 */
private const val HALO_ALPHA = 0.18f

/**
 * 环形进度：容量 / 健康度等。fraction=null 表示"读不到"，只画轨道不画弧
 * （UX/M7：不把未知伪装成 0）。
 * 视觉：Ink.panel 轨道 + 强调色圆头进度弧；中心数字等宽，单位用小字。
 */
@Composable
fun GaugeRing(
    fraction: Float?,        // 0..1；null = 未知（空环 + "–"）
    center: String,
    color: Color = Ink.accent,
    size: Dp = 80.dp,
    strokeW: Dp = 7.dp,
    unit: String = "",
    centerAnimated: Float? = null,   // v20.5: 中心数字平滑滚动（不传则静态 center）
    centerFmt: (Float) -> String = { f -> "%.0f".format(f) },
) {
    // v20.4/20.6: 电量环进度平滑转动（smooth 弹簧；Reduce Motion 直切）
    val animF = remember { Animatable(fraction?.coerceIn(0f, 1f) ?: 0f) }
    val animScale = rememberAnimatorScale()
    LaunchedEffect(fraction) {
        val f = fraction?.coerceIn(0f, 1f) ?: 0f
        if (animScale <= 0f) animF.snapTo(f)
        else animF.animateTo(f, Motion.barSpring)
    }
    val animatedF = if (fraction != null) animF.value else null
    val centerSizeSp = (size.value * 0.22f).toInt().coerceAtLeast(14)
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeW.toPx()
            val inset = stroke / 2
            val arc = Size(size.toPx() - stroke, size.toPx() - stroke)
            // 轨道：Ink.panel（比页面底略深一档，安静地托住进度弧）
            drawArc(
                color = Ink.panel,
                startAngle = 0f, sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset), size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val f = animatedF
            if (f != null && f > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = 360f * f.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset), size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (centerAnimated != null) {
                AnimatedNum(centerAnimated, centerFmt, color = Ink.tx, sizeSp = centerSizeSp)
            } else {
                Text(center, color = Ink.tx,
                    fontSize = centerSizeSp.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
            if (unit.isNotEmpty()) {
                // UX(L6): 中心数字下方补单位，不再让人猜 87 是 % 还是 mAh
                Text(unit, color = Ink.tx3, fontSize = 10.sp)
            }
        }
    }
}

// ===== 曲线：最新值平滑插值 + 窗口动态归一化（实时曲线观感） =====

/**
 * 曲线归一化（v10 修正）：以窗口中心为锚（窄幅/平线居中），span = max(半幅, max×minRatio)。
 * 避免两极端：①固定上限导致小值挤底（旧 freq/2841）；②纯 min-max 把 0.1℃ 噪声放大成"心跳"。
 * 即：真实大幅变化充满画布，微小波动只显示微小起伏（不放大噪声），恒值显示中线。
 */
private class NormScope(private val center: Float, private val span: Float) {
    /** 平线/窄幅（span<=0）画中线；NaN/Inf 一律画中线（防 Canvas 非有限坐标崩溃） */
    fun y(v: Float): Float =
        if (!v.isFinite() || span <= 0f) 0.5f
        else 0.5f + ((v - center) / span).coerceIn(-1f, 1f) * 0.44f   // 0.06..0.94
    companion object {
        fun of(points: List<Float>, minRatio: Float = 0.25f): NormScope? {
            // v0.26.3(CRASH): 先滤 NaN/Inf——一个非有限值会毒化 mn/mx，span 变 NaN
            val finite = points.filter { it.isFinite() }
            if (finite.isEmpty()) return null
            val mn = finite.minOrNull() ?: return null
            val mx = finite.maxOrNull() ?: return null
            val half = (mx - mn) / 2f
            if (half < 1e-4f) return NormScope((mn + mx) / 2f, -1f)   // 恒值：中线占位
            val span = half.coerceAtLeast(mx * minRatio)
            return NormScope((mn + mx) / 2f, span)
        }
        /** 固定绝对量程：百分比等有真实 0 语义的指标（如 GPU 占用 0..100） */
        fun fixed(center: Float, half: Float): NormScope = NormScope(center, half)
    }
}

/**
 * 最新值 tween 插值：数据每秒突变，直接重绘会"跳变"；此处把末点从旧值平滑过渡到新值
 * （历史点不变，仅末点动画，波形平滑滚动）。380ms 动画在 1Hz 采样下大部分时间静止，功耗可控。
 */
@Composable
private fun rememberSmooth(series: List<Float>, animMs: Int = 380): List<Float> {
    // v0.26.6(P0-3): 首帧从真实末点起值——Animatable(0) 会让多点首帧 dropLast(1)+0 毒化 NormScope
    val anim = remember { Animatable(series.lastOrNull() ?: 0f) }
    val animScale = rememberAnimatorScale()   // v20.20(P1-3): Reduce Motion 时曲线直切
    LaunchedEffect(series) {
        val t = series.lastOrNull()
        if (t == null) { anim.snapTo(0f); return@LaunchedEffect }
        if (animScale <= 0f) { anim.snapTo(t); return@LaunchedEffect }
        if (kotlin.math.abs(anim.value - t) < 1e-6f) return@LaunchedEffect
        anim.animateTo(t, tween(animMs, easing = FastOutSlowInEasing))
    }
    return remember(series, anim.value) {
        when {
            series.isEmpty() -> emptyList()
            // 单点无动画：直接取真实值，避免 anim 初始 0 导致端点从底部闪现
            series.size == 1 -> listOf(series[0])
            else -> series.dropLast(1) + anim.value
        }
    }
}

// ===== 通用绘制小工具（BiliPai 图表语言：无网格、细线、柔和端点） =====

/**
 * 极淡基准线：只在绘图区底部画一根近乎不可见的横线（代替旧网格）。
 * 网格会与数据线抢注意力，BiliPai 的图表只留"数据本身"。
 */
private fun DrawScope.faintBaseline(w: Float, h: Float, pad: Float, padY: Float, alpha: Float = 0.55f) {
    drawLine(
        color = Ink.stroke.copy(alpha = alpha),
        start = Offset(pad, h - padY),
        end = Offset(w - pad, h - padY),
        strokeWidth = 1f,
    )
}

/**
 * 平滑折线：Catmull-Rom 转三次贝塞尔，曲线穿过每个采样点（端点位置不变），
 * 相邻点之间圆滑过渡，避免 1s 采样下的锯齿折角；控制点 y 夹在相邻两点之间，防止过冲。
 * @param xFirst 第一个点的 x；@param xStep 相邻点 x 间距；@param yOf 值 → 画布 y
 */
private fun Path.smoothCurve(
    pts: List<Float>,
    xFirst: Float,
    xStep: Float,
    yOf: (Float) -> Float,
) {
    val n = pts.size
    if (n == 0) return
    moveTo(xFirst, yOf(pts[0]))
    if (n == 1) return
    if (n == 2) {
        lineTo(xFirst + xStep, yOf(pts[1]))
        return
    }
    for (i in 0 until n - 1) {
        val v0 = pts[if (i == 0) 0 else i - 1]
        val v1 = pts[i]
        val v2 = pts[i + 1]
        val v3 = pts[if (i + 2 < n) i + 2 else n - 1]
        val x1 = xFirst + xStep * i
        val x2 = x1 + xStep
        val xp = if (i == 0) x1 else x1 - xStep
        val xn = if (i + 2 < n) x2 + xStep else x2
        val y1 = yOf(v1)
        val y2 = yOf(v2)
        val y0 = yOf(v0)
        val y3 = yOf(v3)
        val lo = min(y1, y2)
        val hi = if (y1 < y2) y2 else y1
        val c1x = x1 + (x2 - xp) / 6f
        val c1y = (y1 + (y2 - y0) / 6f).coerceIn(lo, hi)
        val c2x = x2 - (xn - x1) / 6f
        val c2y = (y2 - (y3 - y1) / 6f).coerceIn(lo, hi)
        cubicTo(c1x, c1y, c2x, c2y, x2, y2)
    }
}

/** 最新数据点：3dp 实心圆 + alpha 0.18 的柔和光晕（BiliPai 曲线端点） */
private fun DrawScope.latestDot(color: Color, center: Offset, radius: Float, pulse: Float = 1f) {
    val r = radius * pulse
    drawCircle(color.copy(alpha = HALO_ALPHA), radius = r * HALO_SCALE, center = center)
    drawCircle(color, radius = r, center = center)
}

// ===== 公开图表组件 =====

/**
 * 迷你折线（卡片内嵌）：无网格、细圆头线、渐变面积、端点圆点。
 * strokeW 单位为 dp。
 */
@Composable
fun Sparkline(
    series: List<Float>,
    color: Color = Ink.accent,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    fill: Boolean = true,
    strokeW: Float = 1.8f,
    stretchToFull: Boolean = false,   // S1#2: 稀疏序列（如 24h 24 点）按点数铺满宽度，不再挤右端
) {
    // M9: 画布对象复用，避免每秒重绘分配
    val path = remember { Path() }
    val area = remember { Path() }
    val pts = rememberSmooth(series)
    // v0.28.0: 渐变 Brush 组合期缓存（随色变化重建），不再每帧新建
    val areaBrush = remember(color) {
        Brush.verticalGradient(
            0f to color.copy(alpha = 0.24f),
            1f to color.copy(alpha = 0.02f),
        )
    }
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        // S4: 空态画"采集中"占位，不再整块空白
        if (pts.isEmpty()) {
            drawEmpty(size.width, size.height, 3.dp.toPx())
            return@Canvas
        }
        val n = NormScope.of(pts) ?: return@Canvas
        val w = size.width
        val h = size.height
        // L7: 横向内缩 ≥ 端点圆半径+1dp，末点右半圆不被裁切
        val pad = 6.dp.toPx()
        faintBaseline(w, h, pad, pad)
        // 固定窗口槽位：最新点在最右，历史向左滚动；stretchToFull 时稀疏序列按点数铺满
        // v0.26.3(CRASH): 单点 + stretchToFull → nSlots=1 → step=Infinity → Canvas 抛异常。
        // 至少 2 槽：单点画在槽 0/1 位置，杜绝除零
        val nSlots = if (stretchToFull) pts.size.coerceAtLeast(2) else HIST_WINDOW
        val step = (w - pad * 2) / (nSlots - 1)
        val startIdx = (nSlots - pts.size).coerceAtLeast(0)
        val xFirst = pad + step * startIdx
        val yOf: (Float) -> Float = { v -> h - pad - n.y(v) * (h - pad * 2) }
        // 迷你曲线（16dp）端点圆点过大 → 随高度收缩
        val dotR = (3.dp.toPx()).coerceAtMost(h * 0.22f)
        if (pts.size == 1) {
            // 单点：只画右端端点，避免"只有最左一小段"
            latestDot(color, Offset(xFirst, yOf(pts[0])), dotR)
            return@Canvas
        }
        path.reset()
        path.smoothCurve(pts, xFirst, step, yOf)
        if (fill) {
            area.reset()
            area.addPath(path)
            area.lineTo(xFirst + step * (pts.size - 1), h)
            area.lineTo(xFirst, h)
            area.close()
            drawPath(area, brush = areaBrush)
        }
        drawPath(path, color, style = Stroke(width = strokePx(strokeW), cap = StrokeCap.Round))
        latestDot(
            color,
            Offset(xFirst + step * (pts.size - 1), yOf(pts.last())),
            dotR,
        )
    }
}

/**
 * 多序列折线（当前无调用方，保留升级为平滑 + 全局归一化，供后续 8 核叠图使用）。
 * 单色不填充，避免多线叠图糊成一片；每条线末端一个小圆点。
 */
@Composable
fun MultiLine(
    series: List<List<Float>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 84.dp,
    strokeW: Float = 1.6f,
) {
    val smoothed = series.map { rememberSmooth(it) }
    val all = smoothed.flatten()
    val path = remember { Path() }   // M9: draw 内 reset 复用，避免每帧新建
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (smoothed.isEmpty()) return@Canvas
        val n = NormScope.of(all) ?: return@Canvas
        val w = size.width
        val h = size.height
        val pad = 3.dp.toPx()
        faintBaseline(w, h, pad, pad, alpha = 0.4f)
        val step = (w - pad * 2) / (HIST_WINDOW - 1)
        val lineW = strokePx(strokeW)
        val dotR = 2.5.dp.toPx()
        smoothed.forEachIndexed { si, s ->
            if (s.isEmpty()) return@forEachIndexed
            val color = colors[si % colors.size]
            val startIdx = HIST_WINDOW - s.size
            val xFirst = pad + step * startIdx
            val yOf: (Float) -> Float = { v -> h - pad - n.y(v) * (h - pad * 2) }
            if (s.size == 1) {
                drawCircle(color, radius = dotR, center = Offset(xFirst, yOf(s[0])))
                return@forEachIndexed
            }
            path.reset()
            path.smoothCurve(s, xFirst, step, yOf)
            drawPath(path, color, style = Stroke(width = lineW, cap = StrokeCap.Round))
            drawCircle(
                color, radius = dotR,
                center = Offset(xFirst + step * (s.size - 1), yOf(s.last())),
            )
        }
    }
}

/**
 * 单指标面积折线：细圆头曲线 + 强调色垂直渐变面积 + 端点光晕 + 可选参考虚线。
 * 视觉对齐 BiliPai：无网格（仅一根极淡基准线）、轴标 10sp Ink.tx3、按住可读值浮层。
 * strokeW 单位为 dp（默认 2.5dp）。
 */
@Composable
fun AreaLine(
    series: List<Float>,
    color: Color = Ink.accent,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    avgValue: Float? = null,        // 平均线原始值（与曲线同窗口归一化）
    fixedRange: Float? = null,      // 固定绝对量程（百分比类指标：GPU 占用传 100f，波动按真实比例显示）
    fixedCenter: Float? = null,     // S1#3: 固定量程中心值（配合 fixedRange=half*2：电压 3.9V±0.6 显示 3.3-4.5V）
    axisLabels: List<String>? = null,   // S3: 顶/中/底 y 轴刻度文本（如 100%/50%/0%）；null 不画
    strokeW: Float = 2.5f,
    stretchToFull: Boolean = false,   // S1#2: 稀疏序列按点数铺满宽度
    latestLabel: ((Float) -> String)? = null,   // v20.5: 按住时右上角浮层显示当前值
    zeroLineValue: Float? = null,   // 0 轴参考线对应原始值（带符号电流/功率传 0f：居中画虚线，充/放电上下分区）
    emptyText: String = "采集中…",   // v20.10: 空态文案可定制（无授权时传"需 Shizuku 授权"，不误导为正在采样）
) {
    // v20.5: 按住高亮——曲线加深加粗 + 当前值浮层（工具感的"读表"交互）
    var press by remember { mutableStateOf(false) }
    val hold = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                press = true
                try { tryAwaitRelease() } finally { press = false }
            }
        )
    }
    val path = remember { Path() }
    val area = remember { Path() }
    val dash = avgDashes
    val pts = rememberSmooth(series)
    // v0.28.0: 渐变 Brush 组合期缓存（随色变化重建），不再每帧新建
    val areaBrush = remember(color) {
        Brush.verticalGradient(
            0f to color.copy(alpha = 0.28f),
            1f to color.copy(alpha = 0.02f),
        )
    }
    // v20.4: 最新点脉冲——新数据到达时端点弹一下，曲线有呼吸感
    val pulse = remember { Animatable(1f) }
    val animScale = rememberAnimatorScale()
    var prevLen by remember { mutableStateOf(series.size) }
    val scrolling = LocalScrolling.current
    LaunchedEffect(series.size, scrolling) {
        // v0.28.3: 滚动中脉冲直切——动画帧不再与滚动帧叠加（滑动卡顿根因之一）
        if (scrolling) { pulse.snapTo(1f); prevLen = series.size; return@LaunchedEffect }
        if (prevLen != 0 && series.size > prevLen && animScale > 0f) {
            // v20.17(P2-2): 幅度 1.9→1.12——大脉冲在 1s 采样下等于每帧都在跳
            pulse.snapTo(1.12f)
            pulse.animateTo(1f, tween(durationMillis = Motion.pulseMs, easing = FastOutSlowInEasing))
        }
        prevLen = series.size
    }
    Canvas(modifier = modifier.fillMaxWidth().height(height).then(hold)) {
        // S4: 空态画"采集中"占位，不再整块空白（视觉上等同渲染崩溃）
        if (pts.isEmpty()) {
            drawEmpty(size.width, size.height, 3.dp.toPx(), emptyText)
            return@Canvas
        }
        val n = fixedRange?.let {
                NormScope.fixed(fixedCenter ?: (it / 2f), it / 2f)
            } ?: NormScope.of(pts) ?: return@Canvas
        val w = size.width
        val h = size.height
        // S3: 有 y 轴刻度时左侧留出刻度文字宽度；无刻度也 ≥6dp（L7: 端点圆不被裁切）
        // v0.26.3(P1-2): 左右刻度边距与上下安全边距分离——此前 26dp 同时充当
        // 上下边距，92dp 高曲线绘图区被砍到 40dp（纵向压扁）；padY 仅防端点圆裁切
        val pad = if (axisLabels != null) 26.dp.toPx() else 6.dp.toPx()
        val padY = 4.dp.toPx()
        axisLabels(axisLabels, pad, h)
        // 极淡基准线：只在绘图区底部（0 轴位置）；无刻度时不画，保持卡片干净
        if (axisLabels != null || fixedRange != null) {
            faintBaseline(w, h, pad, padY)
        }
        // 固定窗口槽位：最新点在最右；stretchToFull 时稀疏序列按点数铺满
        // v0.26.3(CRASH): 单点 + stretchToFull → nSlots=1 → step=Infinity → Canvas 抛异常。
        // 至少 2 槽：单点画在槽 0/1 位置，杜绝除零
        val nSlots = if (stretchToFull) pts.size.coerceAtLeast(2) else HIST_WINDOW
        val step = (w - pad * 2) / (nSlots - 1)
        val startIdx = (nSlots - pts.size).coerceAtLeast(0)
        val xFirst = pad + step * startIdx
        val plotH = h - padY * 2
        val yOf: (Float) -> Float = { v -> h - padY - n.y(v) * plotH }
        // v20.7(S2-3): 单点不再提前 return——统一走下方逻辑：
        // path 仅 moveTo（无线段）、area 为空（无填充）、端点圆照画，
        // 且不会跳过 press 块，按住高亮/浮层在单点新图上也生效。
        path.reset()
        path.smoothCurve(pts, xFirst, step, yOf)
        area.reset()
        area.addPath(path)
        area.lineTo(xFirst + step * (pts.size - 1), h)
        area.lineTo(xFirst, h)
        area.close()
        drawPath(area, brush = areaBrush)
        drawPath(path, color, style = Stroke(width = strokePx(strokeW), cap = StrokeCap.Round))
        if (avgValue != null) {
            val ay = yOf(avgValue)
            drawLine(
                color.copy(alpha = 0.45f),
                Offset(pad, ay), Offset(w - pad, ay),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(dash),
            )
        }
        // 0 轴参考线：带符号电流/功率图居中画虚线，充电(正)在上、放电(负)在下，方向一目了然
        if (zeroLineValue != null) {
            val zy = yOf(zeroLineValue)
            drawLine(
                Ink.tx2.copy(alpha = 0.35f),
                Offset(pad, zy), Offset(w - pad, zy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(zeroDashes),
            )
        }
        val last = pts.last()
        val lx = xFirst + step * (pts.size - 1)
        val ly = yOf(last)
        latestDot(color, Offset(lx, ly), 3.dp.toPx(), pulse.value)
        // v20.5/20.6.1: 按住——曲线加深加粗 + 当前值浮层。
        // S1-2 修复：半透明卡片底 pill 保证近黑字在任何底色上都可读；
        // 按端点位置自动避让——端点在下方时 pill 放顶部，反之放底部，高值不再压住曲线。
        if (press) {
            drawPath(path, color, style = Stroke(width = strokePx(strokeW) * 1.5f, cap = StrokeCap.Round))
            if (latestLabel != null) {
                val txt = latestLabel(pts.last())
                valuePaint.color = Ink.tx.toArgb()
                valuePaint.textSize = 11.sp.toPx()
                val tw = valuePaint.measureText(txt)
                val ph = valuePaint.textSize + 12f            // 上下各 6 内边距
                val pw = tw + 16f                              // 左右各 8 内边距
                val px = w - pad - pw                          // 右对齐，留 pad
                val py = if (ly > (h - padY) / 2f) padY else h - padY - ph
                drawRoundRect(
                    color = Ink.card.copy(alpha = 0.92f),
                    topLeft = Offset(px, py),
                    size = Size(pw, ph),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    txt, px + 8f,
                    py + (ph - valuePaint.textSize) / 2f + valuePaint.textSize * 0.8f,
                    valuePaint)
            }
        }
    }
}

/** S4: 空态占位——把"没数据"画成明确提示，不再与"渲染失败"的空白等价 */
private fun DrawScope.drawEmpty(w: Float, h: Float, pad: Float, msg: String = "采集中…") {
    faintBaseline(w, h, pad, pad, alpha = 0.4f)
    emptyPaint.color = Ink.tx3.copy(alpha = 0.75f).toArgb()
    emptyPaint.textSize = (h * 0.4f).coerceIn(6f, 12f)
    drawContext.canvas.nativeCanvas.drawText(
        msg, (w - emptyPaint.measureText(msg)) / 2f,
        (h + emptyPaint.textSize) / 2f, emptyPaint)
}

/**
 * S3: y 轴刻度（顶/中/底三条），nativeCanvas 文本，与曲线共用同一量程位置。
 * BiliPai 视觉：10sp、Ink.tx3、等宽，弱到只做参照不抢数据。
 */
private fun DrawScope.axisLabels(labels: List<String>?, pad: Float, h: Float) {
    if (labels == null || labels.size != 3) return
    axisPaint.color = Ink.tx3.toArgb()
    axisPaint.textSize = 10.sp.toPx()
    val xs = 2.dp.toPx()
    // 顶/中/底按画布高度均匀分布；此前底部误用左侧 pad 当底部边距
    // （pad=26dp → 0% 上移约 71px，与 50% 重叠）——现改为贴画布顶/中/底
    val ts = axisPaint.textSize
    val ys = floatArrayOf(
        ts * 1.1f,
        h / 2f + ts * 0.3f,
        h - ts * 0.25f,
    )
    labels.forEachIndexed { i, l ->
        drawContext.canvas.nativeCanvas.drawText(l, xs, ys[i], axisPaint)
    }
}
