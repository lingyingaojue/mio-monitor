package com.a41probe.monitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.data.CapacityHistory
import com.a41probe.monitor.data.MonitorViewModel
import com.a41probe.monitor.ui.components.AnimatedNum
import com.a41probe.monitor.ui.components.AreaLine
import com.a41probe.monitor.ui.components.GaugeRing
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioChip
import com.a41probe.monitor.ui.components.MioDivider
import com.a41probe.monitor.ui.components.MioInfoRow
import com.a41probe.monitor.ui.components.MioMetric
import com.a41probe.monitor.ui.components.MioPageHeader
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.components.Sparkline
import com.a41probe.monitor.ui.components.tempColor
import com.a41probe.monitor.ui.glass.LocalMioChromeInsets
import com.a41probe.monitor.ui.theme.DataState
import com.a41probe.monitor.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 页面正文左右留白：与仪表盘/CPU/传感器屏一致（BiliPai 卡片边距 14dp） */
private val PageHPad = 14.dp

private fun f1(v: Double?): String = if (v == null) "–" else ((v * 10).roundToInt() / 10.0).toString()

/** v20.21: 小时数 → 人类可读时长（"X 小时 Y 分"/"X 分钟"） */
private fun estH(hours: Double): String {
    val min = (hours.coerceAtLeast(0.0) * 60).roundToInt()
    return if (min >= 60) "${min / 60} 小时 ${min % 60} 分" else "$min 分钟"
}

/** 提权小标：R（Root）/ S（Shizuku）——与旧卡头 PrivBadge 同语义 */
@Composable
private fun PrivChip(rootActive: Boolean) {
    if (rootActive) {
        MioChip("R", color = Ink.warn, mono = true)
    } else {
        MioChip("S", color = Ink.accent, mono = true)
    }
}

/** 卡片内标题行：左标题 + 右侧说明/动作（BiliPai 卡头语言） */
@Composable
private fun CardHead(
    label: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = Ink.tx, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** GPU：占用 hero（MioMetric 32sp）+ 频率/温度 chip + 核心温度（MioInfoRow）+ 温度曲线 */
@Composable
fun GpuScreen(vm: MonitorViewModel) {
    // v0.28.0: 细粒度订阅——GPU 数据/权限独立流
    val g by vm.gpuFlow.collectAsState()
    val priv by vm.privFlow.collectAsState()
    val busyHist by vm.busyHist.collectAsState()
    val tempH by vm.gpuTempHist.collectAsState()

    // v3.1 液态玻璃：内容从玻璃顶栏/dock 下方穿过，LazyColumn 用壳层 insets 避让首末项
    val chrome = LocalMioChromeInsets.current

    val inset = Modifier.padding(horizontal = PageHPad)
    val listState = rememberLazyListState()
    ScrollAware(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 2.dp + chrome.top, bottom = 20.dp + chrome.bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MioPageHeader("GPU", "占用 · 频率 · 核心温度") }

            // ① 占用 hero：绝对 0..100 语义，固定量程，3% 显示在 3% 位置（真实比例，不放大）
            item {
                val f = g.freqMHz
                // v20.12(S1-7): 频率档位随真实状态——未提权标 R，已提权无节点标"不可用"
                val freqNote = when {
                    f != null -> "kgsl gpuclk · ${if (priv.rootAvailable) "Root 通道" else "App 域直读"}"
                    g.freqState == DataState.NEED_PRIV -> "需 Root · 授权后读取"
                    else -> "未找到频率节点 · 已试 kgsl/devfreq"
                }
                MioCard(inset) {
                    MioMetric(
                        label = "占用",
                        value = g.busyPercent?.toString() ?: "–",
                        unit = "%",
                        color = Ink.accent,
                        animated = g.busyPercent?.toFloat(),
                        valueSizeSp = 32,
                        sub = "使用率 · 内核上报",
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MioChip(
                            text = f?.let { "$it MHz" } ?: "–",
                            color = if (f != null) Ink.accent else Ink.off,
                            mono = true,
                        )
                        MioChip(
                            text = "${f1(g.temp0C)}°C",
                            color = tempColor(g.temp0C),
                            mono = true,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(freqNote, color = Ink.off, fontSize = 10.5.sp)
                    Spacer(Modifier.height(10.dp))
                    // S3: y 轴刻度 0/50/100%，曲线不再只有形状没有量级
                    AreaLine(
                        busyHist, color = Ink.accent, height = 96.dp,
                        fixedRange = 100f,
                        axisLabels = listOf("100%", "50%", "0%"),
                        latestLabel = { fr -> "${fr.roundToInt()}%" },
                    )
                }
            }

            // ② 核心温度：双热区信息行（空值/未授权文案与旧版一致）
            item {
                val tempHint = if (priv.rootAvailable) "Root 读取中…" else "需 Shizuku · 授权后读取"
                MioCard(inset) {
                    CardHead("核心温度") {
                        PrivChip(priv.rootAvailable)
                        Spacer(Modifier.width(8.dp))
                        Text("GPU 温区 · 核心", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    MioInfoRow(
                        "GPU 核心 0",
                        f1(g.temp0C),
                        "°C",
                        stateColor = tempColor(g.temp0C),
                    )
                    MioInfoRow(
                        "GPU 核心 1",
                        f1(g.temp1C),
                        "°C",
                        stateColor = tempColor(g.temp1C),
                    )
                    if (g.temp0C == null || g.temp1C == null) {
                        Text(tempHint, color = Ink.off, fontSize = 10.5.sp)
                    }
                }
            }

            // ③ 温度曲线
            item {
                MioCard(inset) {
                    CardHead("温度曲线") {
                        // UX(M4): 时间窗 + 数据源，同屏可读
                        Text("GPU 核心 0 · 近 60s", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    AreaLine(tempH, color = Ink.warn, height = 96.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("当前 ${f1(g.temp0C)} °C", color = Ink.tx2, fontSize = 11.sp)
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

/** 电池：GaugeRing 概览 + 功率/电压电流/温度曲线 + 详情列表 + 24h 容量趋势 */
@Composable
fun BatteryScreen(vm: MonitorViewModel) {
    // v0.28.0: 细粒度订阅——电池数据/权限独立流
    val b by vm.batteryFlow.collectAsState()
    val priv by vm.privFlow.collectAsState()
    val health = b.healthPercent
    val powerHist by vm.powerHist.collectAsState()
    val voltHist by vm.voltHist.collectAsState()
    val curHist by vm.curHist.collectAsState()
    val tempHist by vm.tempHist.collectAsState()

    // v3.1 液态玻璃：内容从玻璃顶栏/dock 下方穿过，LazyColumn 用壳层 insets 避让首末项
    val chrome = LocalMioChromeInsets.current

    // v20: 24h 容量趋势（本地记录，60s 粒度；页面可见时每 60s 刷新一次）
    var cap24 by remember { mutableStateOf(emptyList<Pair<Long, Int>>()) }
    LaunchedEffect(Unit) {
        while (true) {
            cap24 = withContext(Dispatchers.IO) { CapacityHistory.last24h() }
            delay(60_000)
        }
    }

    // v19 双口径：大字优先适配器输入侧功率（充电器实际输出，需提权）；读不到回退电池侧 V×I
    // v0.26.4: 输入侧功率物理范围双保险（>150W 视为脏值，回退电池侧 V×I）
    // v0.26.7: 输入侧功率仅在电池确实进电时采用（满电停充后节点残留会形成假功率）
    val inputW = b.effectiveInputPowerW
    val showW = b.uiPowerW
    val mode = b.chargeMode
    val cap0 = b.capacity
    val cur0 = b.currentDisplay
    val stateText = when {
        b.isFull -> "已充满"
        b.isNotCharging -> "未充电"
        cap0 != null && cap0 >= 100 && b.isCharging ->
            if ((cur0 ?: 0.0) >= 0.5) "满电收尾中" else "涓流补电中"
        b.isCharging -> if (mode == "快充") "快充中" else "充电中"
        else -> "放电中"
    }

    val inset = Modifier.padding(horizontal = PageHPad)
    val listState = rememberLazyListState()
    ScrollAware(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 2.dp + chrome.top, bottom = 20.dp + chrome.bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MioPageHeader("电池", "容量 · 电压电流 · 健康与循环") }

            // ① 电量 + 实时功率 hero
            item {
                MioCard(inset) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // UX(M7): 读不到电量时画空环 + "–"，不把"未知"伪装成 0%
                        GaugeRing(
                            fraction = b.capacity?.let { it / 100f },
                            center = b.capacity?.toString() ?: "–",
                            color = Ink.accent,   // v20.20(P1-6): 与仪表盘恒蓝统一
                            size = 92.dp, strokeW = 8.dp, unit = "%",
                            centerAnimated = b.capacity?.toFloat(),
                        )
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            MioChip(text = stateText, color = Ink.accent)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    if (b.isPlugged) "+" else "−",
                                    color = Ink.accent, fontSize = 24.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                AnimatedNum(showW?.toFloat(), { f -> "%.1f".format(f) },
                                    color = Ink.accent, sizeSp = 26, weight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                Text("W", color = Ink.tx2, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            // 双口径副行：适配器输入侧（快充真相） + 电池侧 V×I
                            Text(
                                if (inputW != null)
                                    "充电器 ${f1(b.inputVoltV)}V · ${f1(b.inputCurA)}A · ~${f1(inputW)}W"
                                else if (priv.rootAvailable) "电池侧 V×I · 输入侧节点不可用"
                                else "电池侧 V×I · 充电器输入需提权",
                                color = if (inputW != null) Ink.accent else Ink.tx2,
                                fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "电池 ${f1(b.voltage)} V · ${if (b.isPlugged) "+" else "−"}${f1(b.currentDisplay)} A · $mode · ${b.tech ?: "–"}",
                                color = Ink.tx2, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "66W 为适配器额定峰值 · 实时功率随电量/温度/协议浮动",
                        color = Ink.off, fontSize = 10.sp,
                    )
                    // v20.21: 充满 / 续航估算——按当前功率与学习容量推算，标注口径不冒充实测
                    val cap = b.capacity
                    val full = b.chargeFull
                    val volt = b.voltage
                    val curD = b.currentDisplay
                    val est = when {
                        b.isCharging && cap != null && cap < 100 && full != null &&
                                volt != null && (inputW ?: 0.0) > 0.5 ->
                            // v20.24(P0-1): chargeFull=mAh → Ah（÷1000）再乘电压除功率得小时
                            "预计充满 " + estH(((100 - cap) / 100.0) * full / 1000.0 * volt / (inputW ?: 0.0))
                        !b.isCharging && cap != null && full != null &&
                                (curD ?: 0.0) > 0.02 ->
                            "预计续航 " + estH(cap / 100.0 * full / (curD!! * 1000))
                        else -> null
                    }
                    if (est != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("$est（按当前功率估算）", color = Ink.tx2, fontSize = 10.5.sp)
                    }
                }
            }

            // ② 充电器协商（免提权广播上限）
            item {
                val negV = b.maxChargingVoltageUv
                val negA = b.maxChargingCurrentUa
                if (b.isCharging && (negV != null || negA != null)) {
                    MioCard(inset) {
                        CardHead("充电器协商") {
                            Text("系统上报 · 免提权", color = Ink.off, fontSize = 10.5.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${f1(negV?.div(1_000_000.0))} V", color = Ink.accent, fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(16.dp))
                            Text("${f1(negA?.div(1_000_000.0))} A", color = Ink.clusterEff, fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            val peak = if (negV != null && negA != null) negV * negA / 1e12 else null
                            Text("上限 ~${f1(peak)} W", color = Ink.tx2, fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "协商档位 ≠ 充电器标称：66W 头受线材 / 配件认证 / 电量温度限制，可能只协商到低压小电流",
                            color = Ink.off, fontSize = 10.sp,
                        )
                    }
                }
            }

            // ③ 功率曲线
            item {
                MioCard(inset) {
                    CardHead("功率曲线") {
                        // UX(M4): 明示时间窗，用户知道窗口宽度与方向
                        Text("近 60s · V×I 实时", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    // O11: 均值只随历史变化重算；powerHist 为原始 W 值，图表内部动态归一化
                    val avgW = remember(powerHist) {
                        if (powerHist.isEmpty()) null else powerHist.average().toFloat()
                    }
                    // v0.26.6(P0-2): 功率量程动态化——按历史最大绝对值自适应
                    val pwMaxAbs = powerHist.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
                    val pwRange = (pwMaxAbs * 1.3f).coerceAtLeast(5f)   // v0.27.0: 30→5
                    AreaLine(
                        powerHist, color = Ink.accent, height = 110.dp,
                        avgValue = avgW, fixedRange = pwRange,
                        axisLabels = listOf(
                            "${f1(pwRange.toDouble())}W",
                            "${f1((pwRange / 2f).toDouble())}W",
                            "0W",
                        ),
                        latestLabel = { f -> "%.1fW".format(f) },
                    )
                    Spacer(Modifier.height(6.dp))
                    // v0.26.6: 与 hero 大数字同源（输入侧优先，回退电池侧）
                    val showWv = b.uiPowerW
                    Text(
                        "当前 ${f1(showWv)} W${if (avgW != null) " · 均值 ${f1(avgW.toDouble())} W" else ""}",
                        color = Ink.tx2, fontSize = 11.sp,
                    )
                }
            }

            // ④ 电压 / 电流实时曲线
            item {
                MioCard(inset) {
                    CardHead("电压 / 电流") {
                        Text("近 60s · 同源实时", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    // S1#3: 电压固定量程 3.3-4.5V（电池实际工作区间）
                    AreaLine(
                        voltHist, color = Ink.accent, height = 78.dp,
                        fixedRange = 1.2f, fixedCenter = 3.9f,
                        axisLabels = listOf("4.5V", "3.9V", "3.3V"),
                        latestLabel = { f -> "%.2fV".format(f) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("电压", color = Ink.off, fontSize = 10.sp)
                    Spacer(Modifier.height(10.dp))
                    // S1#4 / v0.26.6: 电流量程动态化，快充不截顶、涓流有可见起伏
                    val curMaxAbs = curHist.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
                    val curRange = (curMaxAbs * 1.3f).coerceAtLeast(1f)   // v0.27.0: 2→1
                    AreaLine(
                        curHist, color = Ink.clusterEff, height = 78.dp,
                        fixedRange = curRange,
                        axisLabels = listOf(
                            "${f1(curRange.toDouble())}A",
                            "${f1((curRange / 2f).toDouble())}A",
                            "0A",
                        ),
                        latestLabel = { f -> "%.1fA".format(f) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("电流（取绝对值）", color = Ink.off, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("当前 ${f1(b.voltage)} V · ${f1(b.currentDisplay)} A",
                        color = Ink.tx2, fontSize = 11.sp)
                }
            }

            // ⑤ 温度曲线
            item {
                MioCard(inset) {
                    CardHead("温度曲线") {
                        Text("内核上报", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    AreaLine(tempHist, color = Ink.warn, height = 96.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("当前 ${f1(b.tempC)} °C · 广播 ${f1(b.tempBmC)} °C",
                        color = Ink.tx2, fontSize = 11.sp)
                    // v20.21: 近60s 极值——窗口内温度范围一眼可见
                    val tMax = tempHist.maxOrNull()
                    val tMin = tempHist.minOrNull()
                    if (tMax != null && tMin != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("近60s 最高 ${f1(tMax.toDouble())} °C · 最低 ${f1(tMin.toDouble())} °C",
                            color = Ink.off, fontSize = 10.5.sp)
                    }
                }
            }

            // ⑥ 电池详情（MioInfoRow 信息列表）
            item {
                MioCard(inset) {
                    CardHead("电池详情")
                    Spacer(Modifier.height(4.dp))
                    val wear = if (health != null) (100 - health.roundToInt()) else null
                    // sysfs health 原始值映射（Good/Overheat/Cold...），不伪造"良好"
                    val healthLabel = when (b.health?.lowercase()) {
                        "good" -> "良好"
                        "overheat" -> "过热"
                        "cold" -> "过冷"
                        "dead" -> "损坏"
                        "over voltage" -> "过压"
                        "unknown" -> "未知"
                        else -> b.health ?: "–"
                    }
                    // UX(M13): 只有真异常（过热/损坏/过压/过冷）才橙色；"未知"中性灰
                    val healthCol = when (b.health?.lowercase()) {
                        "overheat", "dead", "over voltage", "cold" -> Ink.warn
                        else -> Ink.off
                    }
                    MioInfoRow("健康状态", healthLabel, stateColor = healthCol, mono = false)
                    MioInfoRow("设计容量", "${b.chargeDesign ?: "–"}", "mAh", mono = true)
                    MioInfoRow("满电学习容量", "${b.chargeFull ?: "–"}", "mAh", mono = true)
                    // UX(L15): 磨损度并入健康度，不再单独成行（互为补数，重复）
                    MioInfoRow(
                        "健康度（学习/设计）",
                        if (health != null) "${health.roundToInt()}%${if (wear != null) "（磨损 $wear%）" else ""}" else "–",
                        stateColor = if (health != null && health >= 80) Ink.ok else Ink.warn,
                    )
                    MioInfoRow("循环次数", "${b.cycleCount ?: "–"}", "次", mono = true)
                    MioDivider()
                    MioInfoRow("充电类型", mode, mono = false)
                    MioInfoRow("电池技术", b.tech ?: "–", mono = false)
                    MioInfoRow("开路电压 OCV", f1(b.voltageOcvV), "V", mono = true)
                    MioInfoRow("充电截止电压", f1(b.voltageMaxV), "V", mono = true)
                    MioInfoRow("充入电量（循环）", "${b.chargeCounterUah?.div(1000) ?: "–"}", "mAh", mono = true)
                    // P2-11: PMIC 语义 cur=0 = 未施加额外限流，裸 "0/40" 易误读为"被限到 0"
                    MioInfoRow(
                        "充电限流档位",
                        run {
                            val c = b.chargeControlLimit
                            val m = b.chargeControlLimitMax
                            when {
                                c == null && m == null -> "–"
                                c == 0 -> "未限流（0/${m ?: "–"}）"
                                else -> "$c/${m ?: "–"}"
                            }
                        },
                        mono = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("容量口径：燃料计学习容量（单位 mAh，随机型电芯结构而异）",
                        color = Ink.off, fontSize = 10.sp)
                }
            }

            // ⑦ 双口径温度：内核 / 系统广播
            item {
                MioCard(inset) {
                    CardHead("电池温度") {
                        Text("内核 / 系统广播", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            MioMetric(
                                label = "内核",
                                value = f1(b.tempC),
                                unit = "°C",
                                color = Ink.tx,
                                valueSizeSp = 24,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            MioMetric(
                                label = "系统广播",
                                value = f1(b.tempBmC),
                                unit = "°C",
                                color = Ink.tx,
                                valueSizeSp = 24,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // UX(M12): 解释双口径差异，避免"到底哪个准"的困惑
                    Text("两处采样点不同，属正常小差异", color = Ink.off, fontSize = 10.sp)
                }
            }

            // ⑧ v20: 24h 容量趋势落地（本地记录，非占位）
            item {
                MioCard(inset) {
                    CardHead("容量趋势 · 24h") {
                        Text("本地记录 · 60s 粒度", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (cap24.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                            Text("积累中 · 每 60s 记录一次容量", color = Ink.off, fontSize = 11.sp)
                        }
                    } else {
                        // S1#2: 24h 稀疏序列按点数铺满宽度，不再挤在右端 40%
                        Sparkline(
                            cap24.map { it.second.toFloat() },
                            color = Ink.accent,
                            height = 56.dp,
                            stretchToFull = true,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("最近 ${cap24.size} 小时 · 当前 ${b.capacity ?: "–"}%",
                            color = Ink.tx2, fontSize = 11.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}
