package com.a41probe.monitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.data.monitor.MonitorRepository
import com.a41probe.monitor.data.remote.ConnState
import com.a41probe.monitor.data.remote.RemoteSnapshot
import com.a41probe.monitor.ui.components.AreaLine
import com.a41probe.monitor.ui.components.MioBarRow
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioInfoRow
import com.a41probe.monitor.ui.components.MioPageTopBar
import com.a41probe.monitor.ui.components.ProbeButton
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.components.SpecialFace
import com.a41probe.monitor.ui.theme.Ink

/* ============================================================
 * 远端设备详情（BiliPai / Miuix 版）：固定返回条 + 分组卡片。
 * 本地累积远端快照历史、复用全套图表；曲线/统计口径与旧实现一致。
 * ============================================================ */

/** 远端设备详情：本地累积远端快照历史，复用全套图表。 */
@Composable
fun RemoteDetailScreen(
    deviceKey: String,
    onBack: () -> Unit,
) {
    val devices by MonitorRepository.devices.collectAsState()
    val e = devices.firstOrNull { it.key == deviceKey }
    val s = e?.snap

    val cpuHist = remember { mutableStateOf<List<Float>>(emptyList()) }
    val tempJHist = remember { mutableStateOf<List<Float>>(emptyList()) }
    val tempSHist = remember { mutableStateOf<List<Float>>(emptyList()) }
    val powerHist = remember { mutableStateOf<List<Float>>(emptyList()) }
    val voltHist = remember { mutableStateOf<List<Float>>(emptyList()) }
    val curHist = remember { mutableStateOf<List<Float>>(emptyList()) }

    LaunchedEffect(s) {
        if (s != null) {
            append(cpuHist, s.cpuTotal?.toFloat())
            append(tempJHist, halTemp(s, 0)?.toFloat())
            append(tempSHist, halTemp(s, 3)?.toFloat())
            append(voltHist, s.battery.voltage?.toFloat())
            // 电流/功率带符号：充电为正(0轴上方)、放电为负(下方)，配合 0 轴直观看方向
            // 充/放电判定与下方电池曲线卡共用 isCharging/isFull，保证符号、颜色、文案口径一致
            val charging = isCharging(s)
            val full = isFull(s)
            // 满电电流≈0，符号取正，颜色用中性，不显示为放电橙
            val sgn = if (charging || full) 1f else -1f
            append(powerHist, s.battery.powerW?.toFloat()?.times(sgn))
            append(curHist, s.battery.currentA?.toFloat()?.times(sgn))
        }
    }

    val listState = rememberLazyListState()

    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        MioPageTopBar(title = "设备详情", onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ScrollAware(listState) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 2.dp, bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 设备身份 + 连接状态
                    item {
                        Column(Modifier.padding(horizontal = 4.dp)) {
                            Text(
                                e?.info?.model?.ifBlank { null } ?: e?.host ?: "设备",
                                color = Ink.tx, fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                buildString {
                                    append(if (e?.state == ConnState.ONLINE) "已连接" else "未连接")
                                    if (e?.latencyMs != null && e.latencyMs >= 0) append(" · ${e.latencyMs}ms")
                                    if (e?.host != null) append(" · ${e.host}")
                                },
                                color = Ink.tx3, fontSize = 12.sp,
                            )
                        }
                    }

                    if (s == null) {
                        item {
                            MioCard(contentPadding = PaddingValues(16.dp)) {
                                // v1.3.0: 等数据时的表情（随角色预设换人）
                                SpecialFace("shy_head", Modifier.fillMaxWidth().height(112.dp))
                                Text(
                                    e?.log?.ifBlank { "等待数据…" } ?: "等待数据…",
                                    color = Ink.tx2, fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(12.dp))
                                ProbeButton(
                                    text = "重新连接",
                                    onClick = { if (e != null) MonitorRepository.connect(e.key) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        return@LazyColumn
                    }

                    // CPU 占用曲线
                    item {
                        MioCard(contentPadding = PaddingValues(16.dp)) {
                            DetailCardTitle("CPU 占用")
                            Spacer(Modifier.height(8.dp))
                            AreaLine(
                                series = cpuHist.value,
                                color = Ink.accent,
                                height = 120.dp,
                                stretchToFull = true,
                                fixedRange = 100f,
                                axisLabels = listOf("100%", "50%", "0%"),
                                emptyText = "需 Shizuku 授权",
                            )
                        }
                    }

                    // 分核占用
                    item {
                        MioCard(contentPadding = PaddingValues(16.dp)) {
                            DetailCardTitle("分核占用")
                            Spacer(Modifier.height(4.dp))
                            val labelColor = remember(s.clusters, s.maxFreqs) { remoteClusterColorMap(s) }
                            s.coreBusy.forEachIndexed { i, p ->
                                val label = s.clusters.getOrNull(i) ?: "cpu$i"
                                MioBarRow(
                                    name = "$label·$i",
                                    frac = (p ?: 0) / 100f,
                                    value = (p?.toString() ?: "–") + "%",
                                    barColor = labelColor[label] ?: Ink.accent,
                                    nameWidth = 72.dp,
                                )
                            }
                        }
                    }

                    // 温度曲线
                    item {
                        MioCard(contentPadding = PaddingValues(16.dp)) {
                            DetailCardTitle("温度曲线")
                            Spacer(Modifier.height(8.dp))
                            Text("结温（CPU）", color = Ink.tx2, fontSize = 11.sp)
                            AreaLine(
                                series = tempJHist.value, color = Ink.warn, height = 96.dp,
                                stretchToFull = true,
                                emptyText = "需授权",
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("外壳", color = Ink.tx2, fontSize = 11.sp)
                            AreaLine(
                                series = tempSHist.value, color = Ink.accent, height = 84.dp,
                                stretchToFull = true,
                                emptyText = "采集中…",
                            )
                        }
                    }

                    // 电池曲线
                    item {
                        MioCard(contentPadding = PaddingValues(16.dp)) {
                            val chargingNow = isCharging(s)
                            val fullNow = isFull(s)
                            val powerColor = when {
                                chargingNow -> Ink.ok
                                fullNow -> Ink.tx2
                                else -> Ink.warn
                            }
                            DetailCardTitle("电池功率")
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    chargingNow -> "充电中 · 功率为正（0轴上方）"
                                    fullNow -> "已充满 · 电流/功率接近 0"
                                    else -> "放电中 · 功率为负（0轴下方）"
                                },
                                color = Ink.tx3, fontSize = 10.5.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            AreaLine(
                                series = powerHist.value,
                                color = powerColor, height = 104.dp,
                                stretchToFull = true,
                                fixedRange = 60f, fixedCenter = 0f, zeroLineValue = 0f,
                                axisLabels = listOf("+30W", "0W", "-30W"),
                                latestLabel = { "%+.1f W".format(it) },
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("电压", color = Ink.tx2, fontSize = 11.sp)
                            AreaLine(
                                series = voltHist.value, color = Ink.accent, height = 80.dp,
                                stretchToFull = true,
                                fixedRange = 1.2f, fixedCenter = 3.9f,
                                axisLabels = listOf("4.5V", "3.9V", "3.3V"),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("电流", color = Ink.tx2, fontSize = 11.sp)
                            AreaLine(
                                series = curHist.value,
                                color = powerColor, height = 80.dp,
                                stretchToFull = true,
                                fixedRange = 12f, fixedCenter = 0f, zeroLineValue = 0f,
                                axisLabels = listOf("6A", "0A", "-6A"),
                                latestLabel = { "%+.2f A".format(it) },
                            )
                        }
                    }

                    // 电池详情
                    item {
                        MioCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                            DetailCardTitle("电池详情")
                            Spacer(Modifier.height(4.dp))
                            val b = s.battery
                            MioInfoRow("电量", b.capacity?.toString() ?: "–", unit = "%")
                            MioInfoRow("状态", b.chargeMode ?: b.status ?: "–", mono = false)
                            MioInfoRow("健康度", b.healthPercent?.let { "%.0f".format(it) } ?: "–", unit = "%")
                            MioInfoRow("循环次数", b.cycle?.toString() ?: "–", unit = "次")
                            MioInfoRow("电池温度", b.tempC?.fmt1() ?: "–", unit = "°C")
                            MioInfoRow("技术", b.tech ?: "–", mono = false)
                            MioInfoRow(
                                "充电协商",
                                "${b.negotVoltV?.fmt1() ?: "–"}V · ${b.negotCurA?.fmt1() ?: "–"}A",
                                mono = false,
                            )
                            MioInfoRow(
                                "充电器输入",
                                b.inputPowerW?.let { "${it.fmt1()}" } ?: "–",
                                unit = if (b.inputPowerW != null) "W" else "",
                                mono = false,
                            )
                        }
                    }

                    // 内存 / GPU / 热缓解
                    item {
                        MioCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                            DetailCardTitle("内存 · GPU · 热缓解")
                            Spacer(Modifier.height(4.dp))
                            MioInfoRow(
                                "内存",
                                "%.1f / %.1f GB".format(s.mem.totalGB - s.mem.availGB, s.mem.totalGB),
                                mono = false,
                            )
                            MioInfoRow("GPU 频率", s.gpuFreq?.let { "$it" } ?: "–", unit = if (s.gpuFreq != null) "MHz" else "", mono = false)
                            MioInfoRow("GPU 占用", s.gpuBusy?.let { "$it" } ?: "–", unit = if (s.gpuBusy != null) "%" else "", mono = false)
                            val activeCool = s.cooling.filter { it.active }
                            if (activeCool.isEmpty()) {
                                MioInfoRow("热缓解", "运行正常", mono = false)
                            } else {
                                activeCool.forEach { c ->
                                    MioInfoRow(c.name, "等级 ${c.cur}/${c.max}", mono = false)
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

/** 卡片标题（BiliPai 卡片内标题语言） */
@Composable
private fun DetailCardTitle(text: String) {
    Text(
        text, color = Ink.tx, fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun append(
    state: androidx.compose.runtime.MutableState<List<Float>>,
    v: Float?,
) {
    if (v == null) return
    state.value = (state.value + v).takeLast(60)
}

/** 统一充放电判定：优先 status，缺失时用 chargeMode 兜底（写历史与定颜色/副标题共用同一口径）。 */
private fun isCharging(s: RemoteSnapshot): Boolean {
    val st = s.battery.status
    return st?.startsWith("Charg") == true ||
        (s.battery.chargeMode?.contains("充") == true &&
            !s.battery.chargeMode.contains("放"))
}

/** 满电判定（电流≈0，符号取正、颜色中性）。 */
private fun isFull(s: RemoteSnapshot): Boolean = s.battery.status == "Full"

private fun halTemp(s: RemoteSnapshot, type: Int): Double? =
    s.hal.filter { it.type == type }.mapNotNull { it.tempC }.maxOrNull()

private fun Double.fmt1(): String = "%.1f".format(this)

/**
 * 远端 label→颜色：按各簇 maxFreq 降序推断角色（与本机 CpuTopologyDetector 同口径）。
 */
private fun remoteClusterColorMap(s: RemoteSnapshot): Map<String, androidx.compose.ui.graphics.Color> {
    val byLabel = HashMap<String, Int>()
    s.maxFreqs.forEachIndexed { i, m ->
        if (m != null) {
            val l = s.clusters.getOrNull(i) ?: return@forEachIndexed
            byLabel[l] = maxOf(byLabel[l] ?: 0, m)
        }
    }
    val order = byLabel.entries.sortedByDescending { it.value }.map { it.key }
    val n = order.size
    return order.mapIndexed { idx, l ->
        l to when {
            n <= 1 -> Ink.clusterPrime
            idx == 0 -> Ink.clusterPrime
            idx == 1 -> if (n >= 3) Ink.clusterPerf else Ink.clusterEff
            idx == 2 -> if (n >= 4) Ink.clusterBal else Ink.clusterEff
            else -> Ink.clusterEff
        }
    }.toMap()
}
