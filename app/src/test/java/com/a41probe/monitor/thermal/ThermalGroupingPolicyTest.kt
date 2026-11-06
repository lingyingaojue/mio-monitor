package com.a41probe.monitor.thermal

import com.a41probe.monitor.data.ThermalTrip
import com.a41probe.monitor.data.ThermalZone
import com.a41probe.monitor.ui.screens.groupThermalZones
import com.a41probe.monitor.ui.screens.tripThresholdLabel
import com.a41probe.monitor.ui.screens.visibleZoneRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.12：温度页分组/展开逻辑单测。
 * 保证 95 区全量都能归组、CPU/GPU 识别正确、Top-5 与展开行为稳定。
 */
class ThermalGroupingPolicyTest {

    private fun zone(id: Int, name: String, temp: Double? = 40.0) =
        ThermalZone(id = id, name = name, tempC = temp)

    @Test
    fun everyZoneIsAssignedExactlyOnce() {
        val zones = listOf(
            zone(0, "cpuss-0-0", 50.0),
            zone(1, "cpu-0-0-0", 49.0),
            zone(2, "gpuss-0", 48.0),
            zone(3, "nsp0", 47.0),
            zone(4, "ddr", 46.0),
            zone(5, "battery", 34.0),
            zone(6, "skin", 38.0),
            zone(7, "usb-therm", 36.0),
            zone(8, "aoss-0", 45.0),
            zone(9, "sdr0", 44.0),
        )
        val groups = groupThermalZones(zones)
        val seen = groups.flatMap { g -> g.zones.map { it.id } }
        assertEquals("不能重复归组", zones.size, seen.size)
        assertEquals("不能遗漏热区", zones.map { it.id }.toSet(), seen.toSet())

        val cpu = groups.first { it.title == "CPU" }
        assertTrue(cpu.zones.any { it.name == "cpuss-0-0" })
        assertTrue(cpu.zones.any { it.name == "cpu-0-0-0" })
        val gpu = groups.first { it.title == "GPU" }
        assertEquals(listOf("gpuss-0"), gpu.zones.map { it.name })
        assertTrue(groups.first { it.title == "其他" }.zones.any { it.name == "aoss-0" })
    }

    @Test
    fun topFiveAndExpandedRowsBehaveAsExpected() {
        val zones = (0 until 12).map { zone(it, "cpu-0-$it", 30.0 + it) }
        val groups = groupThermalZones(zones)
        val cpu = groups.first { it.title == "CPU" }
        assertEquals(5, visibleZoneRows(cpu.zones, expanded = false).size)
        assertEquals(12, visibleZoneRows(cpu.zones, expanded = true).size)
        // 组内按温度降序，无效/负值置后
        assertEquals("cpu-0-11", cpu.zones.first().name)
    }

    @Test
    fun tripThresholdUsesLowestTemperature() {
        val z = zone(1, "cpu-0", 55.0).copy(
            trips = listOf(
                ThermalTrip("hot", 135.0),
                ThermalTrip("passive", 125.0),
                ThermalTrip("critical", null),
            ),
        )
        assertEquals("阈125°", tripThresholdLabel(z))
        assertEquals(null, tripThresholdLabel(zone(2, "cpu-1", 40.0)))
        // 低温/冷却触发阈值（如 7°C）不当作降频线展示
        assertEquals(
            null,
            tripThresholdLabel(zone(3, "cpu-2", 0.0).copy(trips = listOf(ThermalTrip("passive", 7.0)))),
        )
    }

    @Test
    fun unreadableZonesAreFilteredBeforeGrouping() {
        val zones = listOf(
            zone(0, "cpu-a", null),
            zone(1, "cpu-b", -5.0),
            zone(2, "cpu-c", 60.0),
            zone(3, "cpu-d", 40.0),
        )
        val sorted = groupThermalZones(zones).first { it.title == "CPU" }.zones
        assertEquals(listOf("cpu-c", "cpu-d"), sorted.map { it.name })
    }
}
