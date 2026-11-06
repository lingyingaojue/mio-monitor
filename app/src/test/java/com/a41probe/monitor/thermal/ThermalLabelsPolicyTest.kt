package com.a41probe.monitor.thermal

import com.a41probe.monitor.data.CoolingDev
import com.a41probe.monitor.data.HalTemp
import com.a41probe.monitor.data.ThermalZone
import com.a41probe.monitor.ui.screens.coolingZh
import com.a41probe.monitor.ui.screens.halSectionTitle
import com.a41probe.monitor.ui.screens.halTempLabel
import com.a41probe.monitor.ui.screens.groupThermalZones
import com.a41probe.monitor.ui.screens.isReadableCooling
import com.a41probe.monitor.ui.screens.isReadableHal
import com.a41probe.monitor.ui.screens.isReadableTemp
import com.a41probe.monitor.ui.screens.isReadableZone
import com.a41probe.monitor.ui.screens.zoneNameZh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.13：温度页标签中文化 + 可读性过滤单测。
 */
class ThermalLabelsPolicyTest {

    @Test
    fun zoneNamesAreLocalizedWherePossible() {
        assertEquals("CPU 0-1-1", zoneNameZh("cpu-0-1-1"))
        assertEquals("CPU 0-0", zoneNameZh("cpuss-0-0"))
        assertEquals("GPU 5", zoneNameZh("gpuss-5"))
        assertEquals("NPU 3", zoneNameZh("nsphmx-3"))
        assertEquals("内存", zoneNameZh("ddr"))
        assertEquals("多媒体", zoneNameZh("video"))
        assertEquals("应用子系统 0", zoneNameZh("aoss-0"))
        assertEquals("基带 2", zoneNameZh("mdmss-2"))
        assertEquals("相机 0", zoneNameZh("camera-0"))
        assertEquals("静默温区", zoneNameZh("quiet_therm"))
        assertEquals("外壳", zoneNameZh("skin"))
        assertEquals("游戏外壳", zoneNameZh("tz_game_shell"))
        assertEquals("副板", zoneNameZh("sub_board_therm"))
        assertEquals("充电口", zoneNameZh("usb-therm"))
        assertEquals("ToF 相机", zoneNameZh("tof-therm"))
        assertEquals("Wi-Fi", zoneNameZh("wlan-therm"))
        assertEquals("4G 射频", zoneNameZh("ltepa_ntc"))
        assertEquals("5G 射频", zoneNameZh("nrpa_ntc"))
        assertEquals("电量计", zoneNameZh("bq_bms"))
        assertEquals("充电 IC", zoneNameZh("chg_ic_therm"))
        assertEquals("电源芯片 8550", zoneNameZh("pm8550_tz"))
        assertEquals("无线充电", zoneNameZh("wireless"))
        // 无法可靠翻译的型号/未知专名保留原文
        assertEquals("unknown-thermal", zoneNameZh("unknown-thermal"))
    }

    @Test
    fun coolingTypesAreLocalized() {
        assertEquals("CPU 6 限频", coolingZh("cpufreq-cpu6"))
        assertEquals("CPU 3 热插拔", coolingZh("cpu-hotplug3"))
        assertEquals("暂停 CPU 2", coolingZh("pause-cpu2"))
        assertEquals("热暂停 8", coolingZh("thermal-pause-8"))
        assertEquals("CPU 簇 1 限频", coolingZh("cpu-cluster1"))
        assertEquals("GPU 限频", coolingZh("kgsl"))
        assertEquals("内存降速", coolingZh("ddr-cdev"))
        assertEquals("屏幕降帧", coolingZh("display-fps"))
        assertEquals("存储限速", coolingZh("ufs"))
        assertEquals("基带降速", coolingZh("modem_vdd"))
        assertEquals("射频降速", coolingZh("pa_lte_sdr0_dsc"))
        assertEquals("未知冷却项", coolingZh("未知冷却项"))
    }

    @Test
    fun halLabelsAreChinese() {
        assertEquals("官方 CPU0", halTempLabel("CPU0"))
        assertEquals("官方温度 · 8 项", halSectionTitle(8))
    }

    @Test
    fun unreadableDataIsHidden() {
        assertFalse(isReadableTemp(null))
        assertFalse(isReadableTemp(0.0))
        assertFalse(isReadableTemp(-40.0))
        assertFalse(isReadableTemp(Double.NaN))
        assertFalse(isReadableTemp(151.0))
        assertTrue(isReadableTemp(0.1))
        assertTrue(isReadableTemp(45.0))

        assertFalse(isReadableZone(ThermalZone(1, "cpu-0", null)))
        assertFalse(isReadableZone(ThermalZone(2, "cpu-1", 0.0)))
        assertTrue(isReadableZone(ThermalZone(3, "cpu-2", 42.0)))

        assertFalse(isReadableCooling(CoolingDev(1, "gpu", cur = null, max = 10)))
        assertFalse(isReadableCooling(CoolingDev(2, "gpu", cur = 0, max = 0)))
        assertTrue(isReadableCooling(CoolingDev(3, "gpu", cur = 0, max = 10)))

        assertFalse(isReadableHal(HalTemp("CPU0", 0, null)))
        assertFalse(isReadableHal(HalTemp("CPU1", 0, Double.NaN)))
        assertTrue(isReadableHal(HalTemp("CPU2", 0, 55.0)))

        // 分组/网格只接收可读 zone
        val groups = groupThermalZones(
            listOf(
                ThermalZone(1, "cpu-0", null),
                ThermalZone(2, "cpu-1", 0.0),
                ThermalZone(3, "cpu-2", 50.0),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(1, groups.first().zones.size)
    }
}
