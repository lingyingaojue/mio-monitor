package com.a41probe.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.data.ClusterRole
import com.a41probe.monitor.data.CpuCore
import com.a41probe.monitor.data.CpuReader
import com.a41probe.monitor.data.MonitorViewModel
import com.a41probe.monitor.data.Sysfs
import com.a41probe.monitor.ui.components.AreaLine
import com.a41probe.monitor.ui.components.MioBarRow
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioChip
import com.a41probe.monitor.ui.components.MioInfoRow
import com.a41probe.monitor.ui.components.MioMetric
import com.a41probe.monitor.ui.components.MioPageHeader
import com.a41probe.monitor.ui.components.MultiLine
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 提权小标：R（Root）/ S（Shizuku）——与旧卡头 PrivBadge 同语义 */
@Composable
private fun CpuPrivChip(rootActive: Boolean) {
    if (rootActive) {
        MioChip("R", color = Ink.warn, mono = true)
    } else {
        MioChip("S", color = Ink.accent, mono = true)
    }
}

/** 卡片内标题行：左标题 + 右侧说明/动作（BiliPai 卡头语言） */
@Composable
private fun CpuCardHead(
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

/** CPU：总占用（S）、8 核频率（—）、分核占用（S）、档位分布（—）、调度与限频 */
@Composable
fun CpuScreen(vm: MonitorViewModel) {
    // v0.28.0: 细粒度订阅——核状态/权限/电池各自独立流
    val cores by vm.coresFlow.collectAsState()
    val priv by vm.privFlow.collectAsState()
    val b by vm.batteryFlow.collectAsState()
    val cpuTotal by vm.cpuTotalPercent.collectAsState()
    val cpuTotalHist by vm.cpuTotalHist.collectAsState()
    val corePercent by vm.corePercent.collectAsState()
    // v3.0: 频率历史保留订阅——每核 60s 走势（同簇 DVFS 共享，簇曲线天然重合）
    val hist by vm.freqHist.collectAsState()

    // sysfs 读取移出组合阶段：后台线程读一次，避免每秒主线程 IO
    var gov by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { gov = withContext(Dispatchers.IO) { CpuReader.governor() } }

    // time_in_state：动态取最低频簇（能效核）的首个核，标题用该簇 label
    // 每帧重算 groupBy（核数≤10，开销可忽略）——授权后 maxMHz 由 null 变真值立即生效，不缓存
    val tisCluster = cores.groupBy { it.cluster }
        .map { (label, cs) -> Triple(label, cs.first().index,
            cs.mapNotNull { it.maxMHz }.minOrNull() ?: Int.MAX_VALUE) }
        .minByOrNull { it.third }
    val tisCore = tisCluster?.second ?: 0
    val tisLabel = tisCluster?.first ?: "小核"
    var tis by remember { mutableStateOf<List<Pair<Int, Float>>>(emptyList()) }
    // M22: time_in_state 读一次文件约数毫秒，频率变化会每秒触发重算 → 5s 节流
    var lastTisRead by remember { mutableStateOf(0L) }
    LaunchedEffect(tisCore, cores.getOrNull(tisCore)?.freqMHz) {
        val now = System.currentTimeMillis()
        if (now - lastTisRead < 5_000) return@LaunchedEffect
        lastTisRead = now
        tis = withContext(Dispatchers.IO) { timeInStateFreqs(tisCore) }
    }

    val inset = Modifier.padding(horizontal = 14.dp)
    val listState = rememberLazyListState()
    ScrollAware(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MioPageHeader("CPU 核心", "占用 · 频率 · 档位 · 调度") }

            // ① 总占用 hero + 近 60s 曲线
            item {
                MioCard(inset) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CpuPrivChip(rootActive = priv.rootAvailable)
                        Spacer(Modifier.weight(1f))
                        Text("proc/stat 差分", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    MioMetric(
                        label = "总占用",
                        value = cpuTotal?.roundToInt()?.toString() ?: "–",
                        unit = "%",
                        color = if (cpuTotal == null) Ink.off else Ink.accent,
                        animated = cpuTotal?.toFloat(),
                        valueSizeSp = 36,
                        sub = when {
                            cpuTotal != null -> null
                            priv.rootAvailable -> "Root 读取中…"
                            else -> "需 Shizuku · 授权后显示总占用"
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    AreaLine(
                        cpuTotalHist, color = Ink.accent, height = 84.dp,
                        fixedRange = 100f, axisLabels = listOf("100%", "50%", "0%"),
                        latestLabel = { f -> "${f.roundToInt()}%" },
                        emptyText = if (priv.rootAvailable) "Root 读取中…" else "需 Shizuku 授权",
                    )
                }
            }

            // ② 核心频率：60s 走势 + 每核 MioBarRow（当前频率 / 簇上限）
            item {
                MioCard(inset) {
                    CpuCardHead("核心频率") {
                        Text("实时 · 60s", color = Ink.off, fontSize = 10.5.sp)
                    }
                    if (cores.isNotEmpty()) {
                        if (hist.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            MultiLine(
                                hist,
                                colors = cores.map { clusterColorForRole(it.colorRole) },
                                height = 56.dp,
                                strokeW = 1.3f,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        // 归一化基准：优先该核频率上限（scaling_max_freq），缺失退回全场最高频
                        val maxSeen = (cores.mapNotNull { it.freqMHz }.maxOrNull() ?: 0)
                            .coerceAtLeast(1)
                        cores.forEach { core ->
                            val cap = (core.maxMHz ?: maxSeen).coerceAtLeast(1)
                            MioBarRow(
                                name = "C${core.index} · ${core.cluster}",
                                frac = (core.freqMHz ?: 0).toFloat() / cap,
                                value = core.freqMHz?.let { "$it MHz" } ?: "–",
                                barColor = clusterColorForRole(core.colorRole).copy(alpha = 0.85f),
                                nameWidth = 96.dp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        ClusterLegend(cores)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("同簇共享频率域（DVFS）· 同簇核心频率始终一致",
                        color = Ink.off, fontSize = 10.sp)
                }
            }

            // ③ 分核占用（S /proc/stat 差分）
            item {
                MioCard(inset) {
                    CpuCardHead("分核占用") {
                        CpuPrivChip(rootActive = priv.rootAvailable)
                        Spacer(Modifier.width(8.dp))
                        Text("proc/stat · 实时", color = Ink.off, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (corePercent.none { it != null }) {
                        Box(
                            Modifier.fillMaxWidth().height(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (priv.rootAvailable) "Root 读取中…"
                                else "需 Shizuku · 授权后显示每核占用",
                                color = Ink.off, fontSize = 11.sp,
                            )
                        }
                    } else {
                        cores.indices.forEach { idx ->
                            val core = cores.getOrNull(idx)
                            val pct = corePercent.getOrNull(idx)
                            MioBarRow(
                                name = "C$idx" + (core?.cluster?.let { " · $it" } ?: ""),
                                frac = if (pct != null) pct / 100f else 0f,
                                value = pct?.let { "$it%" } ?: "–",
                                barColor = clusterColorForRole(core?.colorRole).copy(alpha = 0.85f),
                                nameWidth = 96.dp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        ClusterLegend(cores)
                    }
                }
            }

            // ④ 档位分布（A510 time_in_state；频率变化时后台重算）
            item {
                if (tis.isNotEmpty()) {
                    MioCard(inset) {
                        CpuCardHead("频率停留 · $tisLabel")
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            tis.forEach { (freq, frac) ->
                                val active = freq == cores.getOrNull(tisCore)?.freqMHz
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentAlignment = Alignment.BottomCenter,
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(frac.coerceIn(0.02f, 1f))
                                                .background(if (active) Ink.accent else Ink.panel),
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    // v20.15: 每档标 MHz——柱子不再是"无字灰条"，当前档蓝色加粗
                                    Text("$freq", color = if (active) Ink.accent else Ink.off,
                                        fontSize = 8.5.sp, fontFamily = FontFamily.Monospace,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("当前频率蓝色高亮 · 数字为 MHz · 柱高=该频率停留时间占比",
                            color = Ink.off, fontSize = 10.sp)
                    }
                }
            }

            // ⑤ 调度与限频（MioInfoRow 信息行）
            item {
                MioCard(inset) {
                    CpuCardHead("调度与限频")
                    Spacer(Modifier.height(2.dp))
                    MioInfoRow("governor", gov ?: "–", stateColor = Ink.ok, mono = true)
                    // 各簇上限：按簇动态显示 label+"上限"（PRIME→PERF→BAL→EFF 排序），Root 补读真值，未读到置灰
                    val roleOrder = listOf(ClusterRole.PRIME, ClusterRole.PERFORMANCE, ClusterRole.BALANCE, ClusterRole.EFFICIENCY)
                    val clusterInfo = cores.groupBy { it.cluster }.map { (label, cs) ->
                        Triple(label, cs.mapNotNull { it.maxMHz }.maxOrNull(), cs.first().colorRole)
                    }.sortedBy { roleOrder.indexOf(it.third) }
                    clusterInfo.forEach { (label, maxMhz, _) ->
                        MioInfoRow(
                            "$label 上限", maxMhz?.toString() ?: "–",
                            unit = "MHz",
                            stateColor = if (maxMhz != null) Ink.ok else Ink.off,
                            mono = true,
                        )
                    }
                    MioInfoRow(
                        "降频事件", if (b.isCharging) "限频中（快充）" else "无",
                        stateColor = if (b.isCharging) Ink.warn else Ink.ok,
                        mono = false,
                    )
                    Spacer(Modifier.height(4.dp))
                    // UX: 空闲核驻留最低档是 cpufreq 正常行为（如 X2 806MHz），消除"频率卡死"误解
                    Text("核心空闲时停在最低频率（如 X2 806MHz），有负载后调度器自动抬频",
                        color = Ink.off, fontSize = 10.sp, maxLines = 2)
                }
            }
        }
    }
}

/** 读取小核 time_in_state，返回 [(MHz, 驻留占比)] */
private val RE_WS = Regex("\\s+")   // M18: 文件级复用，避免热路径反复编译
private fun timeInStateFreqs(core: Int): List<Pair<Int, Float>> {
    val raw = Sysfs.read("/sys/devices/system/cpu/cpu$core/cpufreq/stats/time_in_state") ?: return emptyList()
    val rows = raw.lines().mapNotNull { l ->
        val p = l.trim().split(RE_WS)
        if (p.size == 2) (p[0].toIntOrNull() to p[1].toLongOrNull()) else null
    }.filter { it.first != null && it.second != null }
    val total = rows.sumOf { it.second!! }.toFloat()
    if (total <= 0) return emptyList()
    return rows.map { (it.first!! / 1000) to (it.second!! / total) }
}

/** 簇角色色：PRIME 暖橙（最大性能核最醒目）· PERF 蓝 · EFF 青绿 · BAL 紫（统一引用 Ink 令牌） */
private fun clusterColorForRole(role: ClusterRole?): Color = when (role) {
    ClusterRole.PRIME -> Ink.clusterPrime
    ClusterRole.PERFORMANCE -> Ink.clusterPerf
    ClusterRole.EFFICIENCY -> Ink.clusterEff
    ClusterRole.BALANCE -> Ink.clusterBal
    null -> Ink.accent
}

/** 簇图例：按 snapshot.cores 动态去重，圆点 + 簇名（含核心数），按角色排序 */
@Composable
private fun ClusterLegend(cores: List<CpuCore>) {
    val roleOrder = listOf(ClusterRole.PRIME, ClusterRole.PERFORMANCE, ClusterRole.BALANCE, ClusterRole.EFFICIENCY)
    val items = remember(cores) {
        val byLabel = LinkedHashMap<String, Pair<ClusterRole?, Int>>()
        cores.forEach { c ->
            val prev = byLabel[c.cluster]
            byLabel[c.cluster] = (c.colorRole ?: prev?.first) to ((prev?.second ?: 0) + 1)
        }
        byLabel.entries.map { (label, p) -> label to p }
            .sortedBy { roleOrder.indexOf(it.second.first) }
    }
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (label, p) ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(clusterColorForRole(p.first)))
                Text(if (p.second > 1) "$label×${p.second}" else label,
                    color = Ink.tx2, fontSize = 10.5.sp)
            }
        }
    }
}
