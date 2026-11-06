package com.a41probe.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.data.monitor.DeviceEntry
import com.a41probe.monitor.data.monitor.MonitorRepository
import com.a41probe.monitor.data.remote.ConnState
import com.a41probe.monitor.data.remote.PrivLevel
import com.a41probe.monitor.data.remote.RemoteSnapshot
import com.a41probe.monitor.ui.components.MioBarRow
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioInfoRow
import com.a41probe.monitor.ui.components.MioPageScaffold
import com.a41probe.monitor.ui.components.ProbeButton
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.components.SpecialFace
import com.a41probe.monitor.ui.glass.LocalMioChromeInsets
import com.a41probe.monitor.ui.glass.scaledGlassFillAlpha
import com.a41probe.monitor.ui.glass.currentGlassCapability
import com.a41probe.monitor.ui.theme.Ink
import com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect
import com.android.purebilibili.core.ui.components.AppTextField

/* ============================================================
 * 监控端（BiliPai / Miuix 版）：固定返回条 + 扫描状态卡 + 设备分组卡片。
 * 发现 / 连接 / 重连 / 添加 / 移除等业务逻辑与旧实现一致。
 * ============================================================ */

/** 监控端：多设备仪表盘。 */
@Composable
fun MonitorScreen(
    onOpenDevice: (String) -> Unit,
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { MonitorRepository.start(ctx) }
    // P2-16: 离开监控页仅停 mDNS 扫描（省电），保活已建立的 TCP 连接；再进入时恢复扫描
    DisposableEffect(Unit) { onDispose { MonitorRepository.stopDiscovery() } }
    val devices by MonitorRepository.devices.collectAsState()
    val scanning by MonitorRepository.scanning.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    MioPageScaffold {
        val chrome = LocalMioChromeInsets.current
        Box(Modifier.fillMaxSize()) {
            ScrollAware(listState) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 2.dp + chrome.top, bottom = 20.dp + chrome.bottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "局域网 · mDNS 自动发现",
                            color = Ink.tx3, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    // 扫描状态
                    item {
                        MioCard(contentPadding = PaddingValues(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(9.dp).clip(CircleShape)
                                        .background(if (scanning) Ink.ok else Ink.off),
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    if (scanning) "正在扫描局域网 · 发现 ${devices.size} 台设备"
                                    else "扫描已停止",
                                    color = Ink.tx, fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                ProbeButton(text = "添加设备", onClick = { showAdd = true })
                            }
                        }
                    }

                    if (devices.isEmpty()) {
                        item {
                            MioCard(contentPadding = PaddingValues(16.dp)) {
                                // v0.26.4 / v1.3.0: 空态表情——随角色预设换人，头部特写小框内脸完整可读
                                SpecialFace("think_head", Modifier.fillMaxWidth().height(128.dp))
                                Text(
                                    "尚未发现被监控设备", color = Ink.tx, fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "请在另一台手机的「被监控模式」中开启服务，并确保两台手机连同一局域网；" +
                                        "也可点「添加设备」手动输入 IP。",
                                    color = Ink.tx2, fontSize = 12.sp, lineHeight = 17.sp,
                                )
                                Spacer(Modifier.height(12.dp))
                                ProbeButton(
                                    text = "手动添加设备",
                                    onClick = { showAdd = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    items(devices.size) { idx ->
                        DeviceCard(
                            e = devices[idx],
                            onOpen = { onOpenDevice(devices[idx].key) },
                            onRemove = { MonitorRepository.remove(devices[idx].key) },
                        )
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        AddDeviceDialog(
            onDismiss = { showAdd = false },
            onConfirm = { host, port ->
                showAdd = false
                MonitorRepository.manualAdd(host, port)
            },
        )
    }
}

@Composable
private fun DeviceCard(
    e: DeviceEntry,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val s = e.snap
    val info = e.info
    val online = e.state == ConnState.ONLINE && s != null
    val title = info?.model?.ifBlank { null } ?: e.host
    val soc = info?.soc?.ifBlank { null }

    MioCard(
        onClick = onOpen,
        contentPadding = PaddingValues(16.dp),
    ) {
        // 头部：机型 / 提权档 / 连接状态
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, color = Ink.tx, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            PrivTag(s?.priv ?: info?.priv ?: PrivLevel.FREE)
            Spacer(Modifier.width(6.dp))
            StateDot(e.state)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            buildString {
                append("${e.host}:${e.port}")
                if (soc != null) append(" · $soc")
                if (e.latencyMs >= 0) append(" · ${e.latencyMs}ms")
            },
            color = Ink.tx2, fontSize = 11.sp,
        )

        if (!online) {
            Spacer(Modifier.height(10.dp))
            Text(
                e.log.ifBlank { stateText(e.state) },
                color = if (e.state == ConnState.RETRYING) Ink.warn else Ink.tx3,
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                ProbeButton(text = "重新连接", onClick = { MonitorRepository.connect(e.key) })
                Spacer(Modifier.width(8.dp))
                ProbeButton(text = "移除", onClick = onRemove)
            }
            return@MioCard
        }

        // hero 三指标
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroCell(
                Modifier.weight(1f),
                s.cpuTotal?.let { "%.0f".format(it) } ?: "–",
                "CPU 占用", Ink.accent,
            )
            HeroCell(
                Modifier.weight(1f),
                halTemp(s, 0)?.let { "%.1f".format(it) } ?: "–",
                "结温", Ink.warn,
            )
            HeroCell(
                Modifier.weight(1f),
                s.battery.capacity?.toString() ?: "–",
                "电量", Ink.ok,
            )
        }

        // CPU 频率
        CardSectionTitle("CPU 频率 · ${s.freqs.size} 核")
        val labelColor = remember(s.clusters, s.maxFreqs) { remoteClusterColorMap(s) }
        s.freqs.forEachIndexed { i, f ->
            val label = s.clusters.getOrNull(i) ?: "cpu$i"
            MioBarRow(
                name = label,
                frac = if (f != null && s.maxFreqs.getOrNull(i) != null && s.maxFreqs[i]!! > 0) {
                    (f.toFloat() / s.maxFreqs[i]!!).coerceIn(0f, 1f)
                } else 0f,
                value = f?.toString() ?: "–",
                barColor = labelColor[label] ?: Ink.accent,
                nameWidth = 52.dp,
            )
        }

        // 温度（HAL）
        CardSectionTitle("温度 · Thermal HAL")
        MiniInfoGrid(
            listOf(
                "结温最高" to halTemp(s, 0)?.fmt1(),
                "外壳" to halTemp(s, 3)?.fmt1(),
                "GPU" to halTemp(s, 1)?.fmt1(),
                "电池" to halTemp(s, 2)?.fmt1(),
                "NPU" to halTemp(s, 9)?.fmt1(),
                "热状态" to thermalStatusText(s.thermalStatus),
            )
        )

        // 电池
        CardSectionTitle("电池")
        MiniInfoGrid(
            listOf(
                "电压" to s.battery.voltage?.fmt1(),
                "电流" to s.battery.currentA?.fmt1(),
                "功率" to s.battery.powerW?.fmt1(),
                "状态" to (s.battery.chargeMode ?: s.battery.status),
                "健康度" to s.battery.healthPercent?.let { "%.0f".format(it) },
                "循环" to s.battery.cycle?.toString(),
            )
        )

        // 内存 / GPU / 充电
        CardSectionTitle("内存 · GPU · 充电")
        MioInfoRow(
            "内存占用",
            "%.1f / %.1f GB".format(s.mem.totalGB - s.mem.availGB, s.mem.totalGB),
            mono = false,
        )
        MioInfoRow("GPU 频率", s.gpuFreq?.let { "$it" } ?: "–", unit = if (s.gpuFreq != null) "MHz" else "", mono = false)
        MioInfoRow(
            "充电协商上限",
            if (s.battery.negotVoltV != null || s.battery.negotCurA != null)
                "${s.battery.negotVoltV?.fmt1() ?: "–"}V · ${s.battery.negotCurA?.fmt1() ?: "–"}A"
            else "–",
            mono = false,
        )
        MioInfoRow(
            "充电器输入",
            s.battery.inputPowerW?.let { "${it.fmt1()}" }
                ?: if (s.priv == PrivLevel.ROOT) "–" else "仅 Root",
            unit = if (s.battery.inputPowerW != null) "W" else "",
            mono = false,
        )

        Spacer(Modifier.height(12.dp))
        Row {
            ProbeButton(text = "查看详情", accent = true, onClick = onOpen)
            Spacer(Modifier.width(8.dp))
            ProbeButton(text = "移除", onClick = onRemove)
        }
    }
}

/** 卡片内分组小标题 */
@Composable
private fun CardSectionTitle(t: String) {
    Text(
        t, color = Ink.tx2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

/** 两列信息网格（MioInfoRow 复用） */
@Composable
private fun MiniInfoGrid(items: List<Pair<String, String?>>) {
    Column {
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                rowItems.forEach { (name, value) ->
                    MioInfoRow(
                        name = name,
                        value = value ?: "–",
                        mono = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroCell(modifier: Modifier, value: String, label: String, color: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.panel)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Ink.tx2, fontSize = 10.5.sp)
    }
}

@Composable
private fun PrivTag(priv: PrivLevel) {
    val (bg, fg) = when (priv) {
        PrivLevel.SHIZUKU -> Ink.accentSoft to Ink.accent
        PrivLevel.ROOT -> Ink.warn.copy(alpha = 0.14f) to Ink.warn
        PrivLevel.FREE -> Ink.panel to Ink.tx2
    }
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(priv.label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StateDot(state: ConnState) {
    val c = when (state) {
        ConnState.ONLINE -> Ink.ok
        ConnState.CONNECTING, ConnState.DISCOVERED -> Ink.accent
        ConnState.RETRYING -> Ink.warn
        ConnState.OFFLINE -> Ink.off
    }
    Box(Modifier.size(9.dp).clip(CircleShape).background(c))
}

/** 添加设备弹窗（v3.1 液态玻璃：透明窗口 + 弹窗背后模糊 + 玻璃材质底） */
@Composable
private fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    val cap = currentGlassCapability()
    val dialogShape = RoundedCornerShape(32.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogShape,
        containerColor = Ink.glassFill.copy(alpha = scaledGlassFillAlpha(0.82f)),
        titleContentColor = Ink.tx,
        textContentColor = Ink.tx2,
        title = {
            // 弹窗背后内容实时模糊（API 31+；低版本自动 no-op，退回半透明材质）
            ModalWindowBlurBehindEffect(enabled = cap.realtimeBlur, radius = 24.dp)
            Text(
                "手动添加设备", fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold, color = Ink.tx,
            )
        },
        text = {
            Column {
                Text(
                    "输入被监控端的 IP 与端口（端口见被监控模式「运行状态」）",
                    color = Ink.tx2, fontSize = 12.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = "IP 地址",
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = "端口",
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = port.toIntOrNull() ?: 0
                if (host.isNotBlank() && p > 0) onConfirm(host.trim(), p)
            }) {
                Text("连接", color = Ink.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Ink.tx2) }
        },
    )
}

// ---- helpers ----
private fun halTemp(s: RemoteSnapshot, type: Int): Double? =
    s.hal.filter { it.type == type }.mapNotNull { it.tempC }.maxOrNull()

private fun Double.fmt1(): String = "%.1f".format(this)

private fun thermalStatusText(st: Int?): String = when (st) {
    null, 0 -> "正常"
    1 -> "轻微"
    2 -> "中度"
    3 -> "严重"
    4 -> "紧急"
    5 -> "关机"
    6 -> "关机"
    else -> "–"
}

private fun stateText(state: ConnState): String = when (state) {
    ConnState.DISCOVERED -> "已发现，准备连接…"
    ConnState.CONNECTING -> "连接中…"
    ConnState.RETRYING -> "连接失败，准备重连…"
    ConnState.OFFLINE -> "设备离线"
    ConnState.ONLINE -> "在线"
}

/**
 * 远端 label→颜色：RemoteSnapshot 无角色字段，按各簇 maxFreq 降序推断角色
 * （与本机 CpuTopologyDetector 同口径：最大=PRIME 暖橙 / 次大=PERF 蓝 / 最小=EFF 青绿 / 4簇第3=BAL 紫）。
 */
private fun remoteClusterColorMap(s: RemoteSnapshot): Map<String, Color> {
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
