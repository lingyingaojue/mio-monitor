package com.a41probe.monitor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.data.MonitorViewModel
import com.a41probe.monitor.ui.components.AnimatedNum
import com.a41probe.monitor.ui.components.AreaLine
import com.a41probe.monitor.ui.components.MioBarRow
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioChip
import com.a41probe.monitor.ui.components.MioMetric
import com.a41probe.monitor.ui.components.MioPageHeader
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.components.tempColor
import com.a41probe.monitor.ui.glass.LocalMioChromeInsets
import com.a41probe.monitor.ui.theme.Ink
import kotlin.math.roundToInt

private fun fmt1(v: Double?): String = if (v == null) "–" else ((v * 10).roundToInt() / 10.0).toString()

/** 提权小标：R（Root）/ S（Shizuku）——与旧卡头 PrivBadge 同语义 */
@Composable
private fun DashPrivChip(rootActive: Boolean) {
    if (rootActive) {
        MioChip("R", color = Ink.warn, mono = true)
    } else {
        MioChip("S", color = Ink.accent, mono = true)
    }
}

/** 卡片内标题行：左标题 + 右侧说明/动作（BiliPai 卡头语言） */
@Composable
private fun DashCardHead(
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

@Composable
fun DashboardScreen(
    vm: MonitorViewModel,
    onGoSettings: () -> Unit = {},
    onGoBattery: () -> Unit = {},
) {
    // v0.28.0: 细粒度订阅——电池/内存/结温/GPU/负载/权限各自独立，不再整页每秒重组
    val b by vm.batteryFlow.collectAsState()
    val cpuTotal by vm.cpuTotalPercent.collectAsState()
    val cpuTotalHist by vm.cpuTotalHist.collectAsState()
    val top by vm.maxThermalFlow.collectAsState()
    val tc = top?.tempC
    val stale by vm.isStale.collectAsState()
    val priv by vm.privFlow.collectAsState()
    val hasPriv = priv.shizukuActive || priv.rootAvailable
    val mem by vm.memFlow.collectAsState()
    val gpu by vm.gpuFlow.collectAsState()
    val load by vm.loadFlow.collectAsState()

    // v3.1 液态玻璃：内容从玻璃顶栏/dock 下方穿过，LazyColumn 用壳层 insets 避让首末项
    val chrome = LocalMioChromeInsets.current

    // v3.0: hero 数字由 MioMetric 内置平滑滚动呈现；冷启动"只滚一次"的状态机在此收尾
    LaunchedEffect(vm.heroReady) { if (vm.heroReady) vm.heroRollDone() }

    val inset = Modifier.padding(horizontal = 14.dp)
    val listState = rememberLazyListState()
    ScrollAware(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 2.dp + chrome.top, bottom = 20.dp + chrome.bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MioPageHeader("仪表盘", "电量 · CPU · 内存 · 温度") }

            // ① 总览 2×2：电量 / CPU / 内存 / 温度，一眼四项重点。
            //   免提权：电量、内存（meminfo）、电池温度均 App 域可读；CPU 格显示“–”，
            //   温度格自动换为“电池温度”。首屏永远有鲜活数字，未授权也不打折。
            item {
                MioCard(inset) {
                    DashCardHead("总览") {
                        if (!hasPriv) MioChip("免提权模式", color = Ink.off)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            MioMetric(
                                label = "电量",
                                value = b.capacity?.toString() ?: "–",
                                unit = "%",
                                color = if (stale || b.capacity == null) Ink.off else Ink.accent,
                                animated = b.capacity?.toFloat(),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            MioMetric(
                                label = "CPU 占用",
                                value = cpuTotal?.roundToInt()?.toString() ?: "–",
                                unit = "%",
                                color = if (stale || cpuTotal == null) Ink.off else Ink.accent,
                                animated = cpuTotal?.toFloat(),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            MioMetric(
                                label = "内存已用",
                                value = mem?.usedPercent?.toString() ?: "–",
                                unit = "%",
                                color = if (stale || mem?.usedPercent == null) Ink.off else Ink.accent,
                                animated = mem?.usedPercent?.toFloat(),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            // 有授权显示最高结温；免提权回退电池温度（BatteryManager 可读）
                            val tval: Double? = if (hasPriv) tc else b.tempC
                            val tlabel = if (hasPriv) "最高结温" else "电池温度"
                            MioMetric(
                                label = tlabel,
                                value = fmt1(tval),
                                unit = "°C",
                                color = if (stale) Ink.off else tempColor(tval),
                                animated = tval?.toFloat(),
                                format = { f -> "%.1f".format(f) },
                            )
                        }
                    }
                    if (!hasPriv) {
                        Spacer(Modifier.height(12.dp))
                        // v20.20(P2-2): 说明行整行可点直达设置——首屏就给出授权路径
                        Text(
                            "免提权可读电量 / 内存 / 电池温度 · 设置页授权后解锁 CPU 占用 / 结温 / 热区",
                            color = Ink.off, fontSize = 10.5.sp, lineHeight = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onGoSettings),
                        )
                    }
                }
            }

            // ② CPU 占用曲线（近 60s）
            item {
                MioCard(inset) {
                    DashCardHead("CPU 占用") {
                        DashPrivChip(rootActive = priv.rootAvailable)
                        Spacer(Modifier.width(8.dp))
                        if (!hasPriv) {
                            // v20.20(P1-4): 免提权用户主 CTA——去设置授权
                            Text(
                                "去授权 ›", color = Ink.accent, fontSize = 11.sp,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .clickable(onClick = onGoSettings),
                            )
                        } else {
                            Text("近 60s · proc/stat", color = Ink.off, fontSize = 10.5.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AreaLine(
                        cpuTotalHist,
                        color = if (stale) Ink.off else Ink.accent,
                        height = 92.dp,
                        fixedRange = 100f,
                        axisLabels = listOf("100%", "50%", "0%"),
                        latestLabel = { f -> "${f.roundToInt()}%" },
                        // v0.27.0: 已授权但 hist 尚未攒点时显示“读取中…”，不再误报“需授权”
                        emptyText = if (hasPriv) "读取中…" else "需 Shizuku 授权",
                    )
                }
            }

            // ③ 电池概览迷你卡：状态 + 功率 + V·I，整卡点击进电池页（详情/健康/循环在专页）
            item {
                MioCard(inset, onClick = onGoBattery) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                batteryStateText(b),
                                color = Ink.accent, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text("电池概览", color = Ink.off, fontSize = 10.5.sp)
                        }
                        val vi = if (b.voltage != null)
                            "%.1f V · %s%s A".format(
                                b.voltage,
                                if (b.isPlugged) "+" else "−",
                                b.currentDisplay?.let { "%.1f".format(it) } ?: "–")
                        else "–"
                        Column(horizontalAlignment = Alignment.End) {
                            // 功率与电池页 hero 同源：统一 uiPowerW（满电0/输入侧/回退/钳位）
                            val showW = b.uiPowerW
                            Text(
                                if (showW != null)
                                    (if (b.isPlugged) "+" else "−") + "%.1f W".format(showW)
                                else "–",
                                color = Ink.tx, fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                vi, color = Ink.off, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("›", color = Ink.off, fontSize = 15.sp)
                    }
                }
            }

            // ④ GPU 占用 | 负载（BiliPai 双卡并排）
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MioCard(Modifier.weight(1f)) {
                        MioMetric(
                            label = "GPU 占用",
                            value = gpu.busyPercent?.toString() ?: "–",
                            unit = "%",
                            color = Ink.accent,
                            animated = gpu.busyPercent?.toFloat(),
                            valueSizeSp = 28,
                            sub = "使用率 · 内核上报",
                        )
                    }
                    MioCard(Modifier.weight(1f)) {
                        MioMetric(
                            label = "负载",
                            value = load?.split(" ")?.getOrNull(0) ?: "–",
                            color = if (load != null) Ink.tx else Ink.off,
                            valueSizeSp = 28,
                            // UX: 直接显示 1/5/15 分钟三值，用户能看懂负载含义
                            sub = if (load != null) "1 / 5 / 15 分钟均值"
                            else "需 Shizuku · 1/5/15 分钟均值",
                        )
                    }
                }
            }

            // ⑤ 内存详情（总览只给已用%，此处给可用/总量/缓存/Swap）
            item {
                MioCard(inset) {
                    DashCardHead("内存") {
                        Text("meminfo · 实时", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNum(
                            mem?.availGB?.toFloat(), { f -> "%.1f".format(f) },
                            color = Ink.tx, sizeSp = 26,
                        )
                        Text(
                            " / ", color = Ink.tx2, fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        AnimatedNum(
                            mem?.totalGB?.toFloat(), { f -> "%.1f".format(f) },
                            color = Ink.tx, sizeSp = 26,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "GB", color = Ink.off, fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "可用 / 总量", color = Ink.off, fontSize = 10.5.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MioBarRow(
                        name = "已用",
                        frac = (mem?.usedPercent ?: 0) / 100f,
                        value = mem?.usedPercent?.let { "$it%" } ?: "–",
                        barColor = Ink.accent.copy(alpha = 0.85f),
                        nameWidth = 48.dp,
                    )
                    // v20.21(P2-12): Swap 青绿——与"已用"主蓝区分
                    MioBarRow(
                        name = "Swap",
                        frac = (mem?.swapUsedPercent ?: 0) / 100f,
                        value = mem?.swapUsedPercent?.let { "$it%" } ?: "–",
                        barColor = Ink.clusterEff.copy(alpha = 0.85f),
                        nameWidth = 48.dp,
                    )
                    Spacer(Modifier.height(6.dp))
                    val m = mem   // v0.28.0: 委托属性不可 smart cast，先解引用局部 val
                    Text(
                        if (m != null)
                            "缓存 %.1f GB · Swap %.1f / %.1f GB".format(
                                m.cachedKB / 1048576.0, m.swapUsedGB, m.swapTotalGB)
                        else "免提权可读 · 正在读取",
                        color = Ink.off, fontSize = 10.5.sp,
                    )
                }
            }
        }
    }
}

/** 电池状态人话文案（与电池页 stateText 同口径） */
private fun batteryStateText(b: com.a41probe.monitor.data.BatteryData): String = when {
    b.isFull -> "已充满"
    b.isNotCharging -> "未充电"
    b.capacity != null && b.capacity >= 100 && b.isCharging ->
        if ((b.currentDisplay ?: 0.0) >= 0.5) "满电收尾中" else "涓流补电中"
    b.isCharging -> if (b.chargeMode == "快充") "快充中" else "充电中"
    else -> "放电中"
}
