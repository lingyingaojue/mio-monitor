package com.a41probe.monitor.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.a41probe.monitor.data.CoolingDev
import com.a41probe.monitor.data.MonitorViewModel
import com.a41probe.monitor.data.ThermalZone
import com.a41probe.monitor.ui.components.MioBarRow
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioChip
import com.a41probe.monitor.ui.components.MioDivider
import com.a41probe.monitor.ui.components.MioInfoRow
import com.a41probe.monitor.ui.components.MioMetric
import com.a41probe.monitor.ui.components.MioPageHeader
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.components.pressClick
import com.a41probe.monitor.ui.components.tempColor
import com.a41probe.monitor.ui.components.tempFrac
import com.a41probe.monitor.ui.theme.Ink
import kotlin.math.roundToInt

/** 页面正文左右留白：与仪表盘/CPU/传感器屏一致（BiliPai 卡片边距 14dp） */
private val PageHPad = 14.dp

private fun f1(v: Double?): String = if (v == null) "–" else ((v * 10).roundToInt() / 10.0).toString()

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

private data class HeadRow(val label: String, val cur: Double, val throttle: Double)

/** 按 CPU/GPU/外壳/电池/NPU 五类，取每类中"当前/降频阈值"比值最高的代表 */
private fun buildHeadroom(
    thresholds: List<com.a41probe.monitor.data.ThermalThreshold>,
    halTemps: List<com.a41probe.monitor.data.HalTemp>,
): List<HeadRow> {
    val typeMap = mapOf("CPU" to "CPU", "GPU" to "GPU", "SKIN" to "外壳",
        "BATTERY" to "电池", "NPU" to "NPU")
    // P2-13: 阈值必须 >0，否则 cur/throttle 除零得 Infinity，误显成"已顶满红线"
    return thresholds.filter { (it.throttleC ?: 0.0) > 0.0 && it.type in typeMap }
        .mapNotNull { th ->
            val cur = halTemps.firstOrNull { it.name == th.name }?.tempC
                ?: return@mapNotNull null
            HeadRow(typeMap[th.type]!!, cur, th.throttleC!!)
        }
        .groupBy { it.label }
        .map { (_, rows) -> rows.maxByOrNull { it.cur / it.throttle }!! }
        .sortedByDescending { it.cur / it.throttle }
}

private fun headColor(frac: Float): Color = when {
    frac >= 0.9f -> Ink.danger
    frac >= 0.75f -> Ink.warn
    else -> Ink.ok
}

/** cooling type → 人话 */
private fun coolingZh(type: String): String = when (type) {
    "thermal-cpufreq-0" -> "小核限频"
    "thermal-cpufreq-1" -> "中核限频"
    "thermal-cpufreq-2" -> "大核限频"
    "gpu" -> "GPU 限频"
    "thermal-devfreq-0" -> "总线限频"
    "ddr-cdev" -> "DDR 降速"
    "display-fps" -> "屏幕降帧"
    "panel0-backlight" -> "屏幕降亮"
    "ufs" -> "存储限速"
    "battery" -> "充电限流"
    "cdsp_hw" -> "NPU 限频"
    "cdsp" -> "DSP 限频"
    "wlan" -> "网络限速"
    else -> type
}

private fun coolingColor(r: Float): Color = when {
    r >= 0.67f -> Ink.danger
    r >= 0.34f -> Ink.warn
    else -> Ink.accent
}

/** Thermal HAL 官方热状态徽章（0 正常 … 6 关机临界） */
@Composable
private fun ThermalStatusBadge(status: Int?) {
    if (status == null) return
    val pair = when (status) {
        0 -> "正常" to Ink.ok
        1 -> "偏热" to Ink.accent
        2 -> "温热" to Ink.warn
        3 -> "较热" to Ink.warn
        4 -> "很热" to Ink.danger
        5 -> "紧急" to Ink.danger
        else -> "关机临界" to Ink.danger
    }
    MioChip(text = "热状态·${pair.first}", color = pair.second)
}

/** 热力：电池温度 hero / 最高·均值 / 距红线 / 缓解动作 / 分组热区 + 84 网格 */
@Composable
fun ThermalScreen(vm: MonitorViewModel) {
    // v0.28.0: 细粒度订阅——热区/冷却/HAL/权限/电池各自独立流
    val all by vm.thermalAllFlow.collectAsState()
    val priv by vm.privFlow.collectAsState()
    val b by vm.batteryFlow.collectAsState()
    val cooling by vm.coolingFlow.collectAsState()
    val halTemps by vm.halFlow.collectAsState()
    val status by vm.thermalStatusFlow.collectAsState()
    val thresholds by vm.thresholdsFlow.collectAsState()
    val avg = all.mapNotNull { it.tempC }.filter { it > 0 }
        .let { if (it.isEmpty()) null else it.sum() / it.size }

    val inset = Modifier.padding(horizontal = PageHPad)
    val listState = rememberLazyListState()
    ScrollAware(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MioPageHeader("温度", "结温 · 外壳 · 84 区热区") }

            // ① 概览：无权限时电池温度 hero 打底，不用整页空白引导
            item {
                MioCard(inset) {
                    if (all.isEmpty()) {
                        // v20.10: 无 Shizuku 也先给出可读温度（电池温度 App 域可读）
                        MioMetric(
                            label = "电池温度",
                            value = f1(b.tempC),
                            unit = "°C",
                            color = tempColor(b.tempC),
                            valueSizeSp = 32,
                            sub = "内核上报 · 免提权可读",
                        )
                        Spacer(Modifier.height(12.dp))
                        MioDivider()
                        Spacer(Modifier.height(12.dp))
                        CardHead("84 区热区") { PrivChip(priv.rootAvailable) }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "未解锁", color = Ink.off, fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (priv.rootAvailable) "Root 读取中…" else "需 Shizuku 授权",
                            color = Ink.off, fontSize = 10.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "授权后解锁全部 84 个温度区（结温/外壳/射频等）· 设置页一键发起",
                            color = Ink.tx2, fontSize = 11.sp,
                        )
                    } else {
                        CardHead("温度概览") { PrivChip(priv.rootAvailable) }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                val top by vm.maxThermalFlow.collectAsState()
                                MioMetric(
                                    label = "最高结温",
                                    value = f1(top?.tempC),
                                    unit = "°C",
                                    color = tempColor(top?.tempC),
                                    valueSizeSp = 30,
                                    sub = top?.name ?: "无权限",
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                MioMetric(
                                    label = "平均",
                                    value = f1(avg),
                                    unit = "°C",
                                    color = Ink.tx,
                                    valueSizeSp = 30,
                                    sub = "全温区均值",
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // 外壳侧温度：只取背板热敏电阻 skin*（真实外壳温度）。
                        // tof-therm 是相机 ToF 传感器、非外壳温度且读数恒定 46.9℃ 异常
                        val shellZones = all.filter { z ->
                            z.tempC != null && z.tempC > 0 && z.name.startsWith("skin")
                        }
                        val shellMax = shellZones.maxByOrNull { it.tempC!! }
                        MioInfoRow("外壳侧", f1(shellMax?.tempC), if (shellMax != null) "°C" else "")
                        if (shellMax != null) {
                            Text(
                                shellMax.name, color = Ink.off, fontSize = 9.5.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "背板热敏电阻(skin) · 手感参考，握持面热惯性偏热，背板通常低于结温 15~25°C",
                            color = Ink.off, fontSize = 10.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // ② 距温度红线（HAL 官方阈值 vs 当前温度）
            item {
                val rows = remember(thresholds, halTemps) { buildHeadroom(thresholds, halTemps) }
                if (rows.isNotEmpty()) {
                    MioCard(inset) {
                        CardHead("距温度红线") {
                            PrivChip(priv.rootAvailable)
                            Spacer(Modifier.width(8.dp))
                            Text("Thermal HAL", color = Ink.off, fontSize = 10.5.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        rows.forEach { r ->
                            val frac = (r.cur / r.throttle).toFloat().coerceIn(0f, 1f)
                            MioBarRow(
                                name = r.label, nameWidth = 48.dp, frac = frac,
                                value = "${f1(r.cur)}/${f1(r.throttle)}°",
                                barColor = headColor(frac),
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "进度 = 当前温度 / 官方温度阈值 · 外壳/电池为安全线，其余为降频线",
                            color = Ink.off, fontSize = 10.sp,
                        )
                    }
                }
            }

            // ③ 热缓解动作（cooling devices 实时等级；默认只显示已触发，避免常温灰条刷屏）
            item {
                val devs = cooling
                if (devs.isNotEmpty()) {
                    MioCard(inset) {
                        CardHead("热缓解动作") {
                            PrivChip(priv.rootAvailable)
                            Spacer(Modifier.width(8.dp))
                            ThermalStatusBadge(status)
                        }
                        Spacer(Modifier.height(6.dp))
                        var expanded by remember { mutableStateOf(false) }
                        val ordered = remember(devs) {
                            devs.sortedWith(compareByDescending<CoolingDev> { it.active }
                                .thenByDescending { it.ratio })
                        }
                        val shown = if (expanded) ordered else ordered.filter { it.active }
                        if (shown.isEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "当前无热缓解动作 · 系统未限制性能",
                                color = Ink.tx2, fontSize = 12.sp,
                            )
                        } else {
                            shown.forEach { c ->
                                val col = if (c.active) coolingColor(c.ratio) else Ink.off
                                MioBarRow(
                                    name = coolingZh(c.name), nameWidth = 88.dp,
                                    frac = c.ratio,
                                    value = "${c.cur ?: "–"}/${c.max ?: "–"}",
                                    barColor = col,
                                )
                            }
                        }
                        val inactiveN = devs.count { !it.active }
                        if (inactiveN > 0) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.fillMaxWidth().pressClick { expanded = !expanded },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (expanded) "收起未触发项"
                                    else "查看全部 ${devs.size} 项 · $inactiveN 项未触发",
                                    color = Ink.accent, fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            // ④ 分组热区（每类 Top-5 + 总数提示）
            item { GroupedZones(all, priv.rootAvailable) }

            // ⑤ 84 区网格
            item {
                MioCard(inset) {
                    CardHead(if (all.isEmpty()) "全部 84 区" else "全部 ${all.size} 区") {
                        PrivChip(priv.rootAvailable)
                    }
                    Spacer(Modifier.height(8.dp))
                    Grid84(all)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (all.isEmpty()) "App 域无权限 · 需 Shizuku" else "20–50°C 归一化",
                        color = Ink.off, fontSize = 10.sp,
                    )
                }
            }
            item {
                Text(
                    "≥42°C 红 · ≥36°C 橙 · 其余绿 · 灰格=未启用传感器",
                    color = Ink.off, fontSize = 10.5.sp,
                    modifier = Modifier.padding(horizontal = PageHPad + 4.dp),
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

private val GROUP_KEYS = listOf(
    "CPU簇" to listOf("cpuss", "cpu"),
    "GPU/NPU/内存" to listOf("gpuss", "nsp", "ddr", "video"),
    "射频/充电/外壳" to listOf("pa", "pm8350", "battery", "skin", "usb", "tof"),
)

/** UX(M10): sysfs 热区代号 → 人话前缀 + 编号（cpuss-0-0 → "CPU 0-0"，原名语义保留） */
private fun zoneNameZh(raw: String): String {
    val (zh, rest) = when {
        raw.startsWith("cpuss") -> "CPU" to raw.removePrefix("cpuss")
        raw.startsWith("cpu") -> "CPU" to raw.removePrefix("cpu")   // cpu-1-5 单核热点
        raw.startsWith("gpuss") -> "GPU" to raw.removePrefix("gpuss")
        raw.startsWith("nsp") -> "NPU" to raw.removePrefix("nsp")
        raw.startsWith("ddr") -> "内存" to raw.removePrefix("ddr")
        raw.startsWith("video") -> "多媒体" to raw.removePrefix("video")
        raw.startsWith("battery") -> "电池" to raw.removePrefix("battery")
        raw.startsWith("skin") -> "外壳" to raw.removePrefix("skin")
        raw.startsWith("usb") -> "充电" to raw.removePrefix("usb")
        raw.startsWith("tof") -> "ToF" to raw.removePrefix("tof")
        raw.startsWith("pm8350") -> "电源" to raw.removePrefix("pm8350")
        raw.startsWith("pa") -> "射频" to raw.removePrefix("pa")
        else -> return raw
    }
    return zh + rest.replaceFirst("-", " ").replace("_", " ")
}

@Composable
private fun GroupedZones(all: List<ThermalZone>, rootActive: Boolean) {
    // M10: 分组过滤/排序只随数据变化重算，滚动重组不再全量执行
    // L16: 保留每组总数，超出 5 条时给出"还有 N 个"提示
    val groups = remember(all) {
        GROUP_KEYS.map { (title, prefixes) ->
            val allItems = prefixes.flatMap { p ->
                all.filter { it.name.startsWith(p) && it.tempC != null }
            }.distinctBy { it.id }.sortedByDescending { it.tempC }
            Triple(title, allItems.take(5), allItems.size)
        }.filter { (_, items, _) -> items.isNotEmpty() }
    }
    if (groups.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = PageHPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { (title, items, total) ->
            MioCard {
                CardHead(title) { PrivChip(rootActive) }
                Spacer(Modifier.height(2.dp))
                items.forEach { z ->
                    MioBarRow(
                        name = zoneNameZh(z.name),
                        frac = tempFrac(z.tempC),
                        value = "${f1(z.tempC)}°",
                        barColor = tempColor(z.tempC),
                    )
                }
                if (total > items.size) {
                    Spacer(Modifier.height(3.dp))
                    Text("还有 ${total - items.size} 个热区", color = Ink.off, fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
private fun Grid84(all: List<ThermalZone>) {
    val cells = all.take(84)
    if (cells.isEmpty()) {
        // 占位 84 格（置灰）
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(7) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(12) {
                        Box(Modifier.weight(1f).height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Ink.stroke))
                    }
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        cells.chunked(12).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { z ->
                    // 负值 = 未使能/未接传感器（mmw、sdr 等 -273℃ 占位），画明显灰格不误导
                    val valid = z.tempC != null && z.tempC > 0
                    Box(Modifier.weight(1f).height(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (valid) tempColor(z.tempC).copy(alpha = 0.85f)
                            else Ink.stroke))
                }
                repeat(12 - row.size) {
                    Box(Modifier.weight(1f).height(14.dp))
                }
            }
        }
    }
}
