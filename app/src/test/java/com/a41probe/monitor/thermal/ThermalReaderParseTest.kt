package com.a41probe.monitor.thermal

import com.a41probe.monitor.data.BulkReader
import com.a41probe.monitor.data.parseTripScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.12：温度/冷却 bulk 文本解析单测（不依赖真机）。
 */
class ThermalReaderParseTest {

    @Test
    fun tripScanParsesTempsAndTypes() {
        val out = """
            ==TRIPTEMP==
            /sys/class/thermal/thermal_zone0/trip_point_0_temp:125000
            /sys/class/thermal/thermal_zone0/trip_point_1_temp:135000
            /sys/class/thermal/thermal_zone8/trip_point_0_temp:95000
            ==TRIPTYPE==
            /sys/class/thermal/thermal_zone0/trip_point_0_type:passive
            /sys/class/thermal/thermal_zone0/trip_point_1_type:hot
            /sys/class/thermal/thermal_zone8/trip_point_0_type:critical
        """.trimIndent()
        val map = parseTripScan(out)
        assertEquals(125.0, map.getValue(0).first().tempC!!, 0.001)
        assertEquals("passive", map.getValue(0).first().type)
        assertEquals(135.0, map.getValue(0).last().tempC!!, 0.001)
        assertEquals("hot", map.getValue(0).last().type)
        assertEquals(95.0, map.getValue(8).single().tempC!!, 0.001)
    }

    @Test
    fun tripScanIgnoresMalformedLines() {
        val out = """
            ==TRIPTEMP==
            garbage
            /sys/class/thermal/thermal_zoneX/trip_point_0_temp:100000
            /sys/class/thermal/thermal_zone1/trip_point_0_temp:NaN
            ==TRIPTYPE==
            garbage
        """.trimIndent()
        assertTrue(parseTripScan(out).isEmpty())
        assertTrue(parseTripScan(null).isEmpty())
        assertTrue(parseTripScan("").isEmpty())
    }

    @Test
    fun bulkParsesAllCoolingDevicesNotJustWhitelist() {
        val out = """
            /sys/class/thermal/thermal_zone0/temp:45000
            ==LOAD==
            0.5 0.4 0.3
            ==STAT==
            cpu  100 0 100 800 0 0 0 0 0 0
            ==COOL==
            10|0
            99|5
            ==CPUFREQ==
            ==PSU==
        """.trimIndent()
        val types = mapOf(0 to "cpu-0-0")
        val meta = mapOf(
            10 to ("cpufreq-cpu0" to 10),
            99 to ("modem_vdd" to 64),
        )
        val bulk = BulkReader.parseBulk(out, types, meta, listOf(10, 99))
        assertNotNull(bulk)
        assertEquals("非白名单 cooling 也必须出现", 2, bulk!!.cooling.size)
        assertTrue(bulk.cooling.any { it.name == "modem_vdd" && it.cur == 5 && it.max == 64 })
        assertTrue(bulk.cooling.any { it.name == "cpufreq-cpu0" && it.cur == 0 })
        assertEquals(1, bulk.zones.size)
        assertEquals(45.0, bulk.zones.first().tempC!!, 0.001)
    }
}
