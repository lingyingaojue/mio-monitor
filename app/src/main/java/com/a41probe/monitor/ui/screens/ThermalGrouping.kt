package com.a41probe.monitor.ui.screens

import com.a41probe.monitor.data.ThermalTrip
import com.a41probe.monitor.data.ThermalZone

/**
 * v3.12：温度页分组（纯函数，可单测）。
 *
 * 全量 zone 按语义分组，保证每个热区只进一组、且所有热区都有归属；
 * 组内按当前温度降序（无效读数置后），UI 默认 Top-5、展开看全部。
 */
data class ThermalZoneGroup(
    val title: String,
    val zones: List<ThermalZone>,
)

private val CPU_PREFIXES = listOf("cpuss", "cpu")
private val GPU_PREFIXES = listOf("gpuss", "gpu")
private val NPU_MEM_PREFIXES = listOf("nsp", "ddr", "video")
private val BOARD_PREFIXES = listOf(
    "pa", "ltepa", "nrpa", "pm", "battery", "skin", "usb", "tof", "wlan",
    "quiet", "chg", "bat_btb", "tz_shell", "wireless", "bq_bms", "sub_board",
)

internal fun groupThermalZones(all: List<ThermalZone>): List<ThermalZoneGroup> {
    if (all.isEmpty()) return emptyList()
    val used = HashSet<Int>(all.size)
    fun take(title: String, match: (String) -> Boolean): ThermalZoneGroup? {
        val zones = all.filter { it.id !in used && match(it.name) }
        if (zones.isEmpty()) return null
        used.addAll(zones.map { it.id })
        return ThermalZoneGroup(title, sortZones(zones))
    }
    val groups = listOfNotNull(
        take("CPU") { name -> CPU_PREFIXES.any { name.startsWith(it) } },
        take("GPU") { name -> GPU_PREFIXES.any { name.startsWith(it) } },
        take("NPU / 内存 / 多媒体") { name -> NPU_MEM_PREFIXES.any { name.startsWith(it) } },
        take("射频 / 充电 / 外壳") { name -> BOARD_PREFIXES.any { name.contains(it) } },
        take("其他") { true },
    )
    return groups
}

internal fun sortZones(zones: List<ThermalZone>): List<ThermalZone> =
    zones.sortedWith(
        compareByDescending<ThermalZone> { it.tempC != null && it.tempC > 0 }
            .thenByDescending { it.tempC ?: Double.MIN_VALUE }
            .thenBy { it.id },
    )

/** 默认 Top-5，展开后全部；总数不超过 limit 时无需展开。 */
internal fun visibleZoneRows(
    zones: List<ThermalZone>,
    expanded: Boolean,
    limit: Int = 5,
): List<ThermalZone> = if (expanded) zones else zones.take(limit)

/** 组内最低的**有效高温** trip 阈值（过滤冷却/低温触发的 6°C 等异常阈值），用于展开行显示「/ 阈105°」。 */
internal fun tripThresholdLabel(zone: ThermalZone): String? {
    val trip = lowestTrip(zone) ?: return null
    val temp = trip.tempC ?: return null
    return "阈${temp.toInt()}°"
}

internal fun lowestTrip(zone: ThermalZone): ThermalTrip? =
    // 低于 20°C 的 trip 多为传感器/冷却触发的低温阈值，不是“降频/高温线”，不予展示
    zone.trips.filter { it.tempC != null && it.tempC!! >= 20.0 }
        .minByOrNull { it.tempC!! }
