package com.a41probe.monitor.ui.screens

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.BuildConfig
import com.a41probe.monitor.data.MonitorViewModel
import com.a41probe.monitor.data.RootBridge
import com.a41probe.monitor.data.ShizukuBridge
import com.a41probe.monitor.data.ThermalReader
import com.a41probe.monitor.ui.components.BottomBarStyle
import com.a41probe.monitor.ui.components.BottomBarStyleConfig
import com.a41probe.monitor.ui.components.DotBadge
import com.a41probe.monitor.ui.components.GlassConfig
import com.a41probe.monitor.ui.components.GlassLevel
import com.a41probe.monitor.ui.components.GlassTransparency
import com.a41probe.monitor.ui.components.GlassTransparencyConfig
import com.a41probe.monitor.ui.components.HapticLevel
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioChoiceRow
import com.a41probe.monitor.ui.components.MioHaptics
import com.a41probe.monitor.ui.components.MioInfoRow
import com.a41probe.monitor.ui.components.MioPageScaffold
import com.a41probe.monitor.ui.components.MioPreferenceGroup
import com.a41probe.monitor.ui.components.MioSectionHeader
import com.a41probe.monitor.ui.components.MioSettingRow
import com.a41probe.monitor.ui.components.MioSwitchRow
import com.a41probe.monitor.ui.components.ProbeButton
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.components.rememberAnimatorScale
import com.a41probe.monitor.ui.components.rememberHaptics
import com.a41probe.monitor.ui.glass.LocalMioChromeInsets
import com.a41probe.monitor.ui.glass.MioGlassSheetSurface
import com.a41probe.monitor.ui.glass.currentGlassCapability
import com.a41probe.monitor.ui.glass.liquidEngineLabel
import com.a41probe.monitor.ui.theme.Ink
import com.a41probe.monitor.ui.theme.Palette
import com.a41probe.monitor.ui.theme.Palettes
import com.a41probe.monitor.ui.theme.SpecialConfig
import com.a41probe.monitor.ui.theme.SpecialTheme
import com.a41probe.monitor.ui.theme.Specials
import com.a41probe.monitor.ui.theme.ThemeConfig
import com.a41probe.monitor.ui.theme.ThemeMode
import com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ============================================================
 * 设置页（BiliPai / Miuix 版）：固定返回条 + 分组偏好列表。
 * 业务行为与持久化键完全沿用旧实现：
 *  - ThemeConfig  : 明暗模式 / 配色预设 / 角色立绘开关
 *  - SpecialConfig: 角色预设（整组立绘 + 配色）
 *  - GlassConfig  : 液态玻璃三档 + 全局玻璃透明度四档
 *  - MioHaptics   : 触感强度（按住持续震、轻点选中）
 *  - CSV 导出、Shizuku/Root 提权通道、双模式入口、采样/数据/设备信息/关于
 * ============================================================ */

private val GLASS_LEVELS = listOf(GlassLevel.FULL, GlassLevel.LITE, GlassLevel.OFF)
private val GLASS_LABELS = listOf("完整", "简约", "关闭")
private val GLASS_TRANSPARENCY_LEVELS = listOf(
    GlassTransparency.LOW,
    GlassTransparency.MEDIUM,
    GlassTransparency.HIGH,
    GlassTransparency.ULTRA,
)
private val GLASS_TRANSPARENCY_LABELS = listOf("低", "中", "高", "全透")
private val BOTTOM_BAR_STYLES = listOf(BottomBarStyle.BILIPAI, BottomBarStyle.MIO)
private val BOTTOM_BAR_LABELS = listOf("BiliPai 原版", "Mio 简版")
private val HAPTIC_LEVELS = listOf(HapticLevel.LIGHT, HapticLevel.MEDIUM, HapticLevel.HEAVY)
private val HAPTIC_LABELS = listOf("轻", "中", "重")

// ---- v20.14: 设备信息全部运行时真读（不写死机型/SoC，兼容任意手机） ----
private fun readSoc(): String {
    if (Build.VERSION.SDK_INT >= 31) {
        val m = Build.SOC_MODEL
        val man = Build.SOC_MANUFACTURER
        if (!m.isNullOrBlank()) return if (!man.isNullOrBlank()) "$man $m" else m
    }
    return try {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("Hardware") || it.startsWith("Processor") }
            ?.substringAfter(":")?.trim()?.takeIf { it.isNotBlank() }
            ?: "未知 SoC（Build 未提供）"
    } catch (e: Exception) { "未知 SoC（Build 未提供）" }
}

private fun readKernel(): String {
    return try {
        File("/proc/version").readText().trim()
            .substringAfter("Linux version ", "")
            .takeIf { it.isNotBlank() }?.substringBefore(" ")?.let { "Linux $it" } ?: "–"
    } catch (e: Exception) { "–" }
}

private fun readMem(): String {
    return try {
        val kb = File("/proc/meminfo").readLines()
            .firstOrNull { it.startsWith("MemTotal") }
            ?.substringAfter(":")?.trim()?.substringBefore(" ")?.toLongOrNull() ?: return "–"
        String.format(Locale.US, "%.1f GB", kb / 1048576f)
    } catch (e: Exception) { "–" }
}

private fun readStorage(): String {
    return try {
        val st = StatFs(Environment.getDataDirectory().path)
        String.format(Locale.US, "%.1f GB", st.totalBytes / 1e9)
    } catch (e: Exception) { "–" }
}

private fun readScreen(ctx: Context): String {
    val dm = ctx.resources.displayMetrics
    return "${dm.widthPixels} × ${dm.heightPixels}"
}

@Volatile private var csvExporting = false   // P2-1: 连点导出防抖，避免并发写多份

/** 导出当前快照为 CSV（下载/Mio/时间戳.csv）并提示路径；写盘移出主线程
 *  v20.21 终审修复：BOM + 全字段引号转义 + RAW 补全（/proc/stat/governor/gpu freq raw/BM temp）
 *  + RENDERED 补全（CPU 占用/GPU 温度/外壳 skin/广播温度/传感器）+ 功率口径对齐 + 小数位对齐 UI */
private fun exportCsv(vm: MonitorViewModel, ctx: Context) {
    if (csvExporting) return
    csvExporting = true
    Thread {
      try {
        val s = vm.snapshot.value
        val sb = StringBuilder()
        val b = s.battery
        val f1 = Locale.US
        // P1-4: UTF-8 BOM，Excel 双击中文不乱码
        sb.append("\uFEFF")
        // P1-1: 统一 CSV 转义——所有字段双引号包裹，内部 " → ""
        fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
        // ===== META：导出信息 =====
        sb.append("# META\n")
        sb.append("key,value,unit\n")
        fun row(k: String, v: String, u: String = "") = sb.append("${csv(k)},${csv(v)},${csv(u)}\n")
        row("meta.exported_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
        row("meta.exported_epoch_ms", System.currentTimeMillis().toString())
        val man = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        // MODEL 在部分 ROM 已含厂商前缀（如 ZTE A2023H），避免拼成 "ZTE ZTE A2023H"
        val devName = if (model.startsWith(man, ignoreCase = true)) model else "$man $model"
        row("meta.device", devName)
        row("meta.app_version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")

        // ===== RAW：读取到的原始数据（原始单位，零换算零过滤，含来源路径）=====
        // P1-2: 去掉说明行里的英文逗号，避免自成畸形行
        sb.append("# RAW - readings as read (raw unit / no conversion / no filter)\n")
        sb.append("key,value,raw_unit,source\n")
        fun raw(k: String, v: String, u: String, srcPath: String) = sb.append("${csv(k)},${csv(v)},${csv(u)},${csv(srcPath)}\n")
        // 电池节点原始值
        raw("battery.capacity", b.capacity?.toString() ?: "", "%", "/sys/class/power_supply/battery/capacity")
        raw("battery.voltage_now", b.voltageNowUv?.toString() ?: "", "uV", "/sys/class/power_supply/battery/voltage_now")
        raw("battery.current_now", b.currentNowUa?.toString() ?: "", "uA", "/sys/class/power_supply/battery/current_now")
        raw("battery.power_now", b.powerNowUw?.toString() ?: "", "uW", "/sys/class/power_supply/battery/power_now")
        raw("battery.temp", b.tempRaw?.toString() ?: "", "0.1C", "/sys/class/power_supply/battery/temp")
        // P1-7: BatteryManager 广播温度原始 int
        raw("battery.temp_bm", b.tempBmRaw?.toString() ?: "", "0.1C", "ACTION_BATTERY_CHANGED EXTRA_TEMPERATURE")
        raw("battery.charge_full", b.chargeFullUah?.toString() ?: "", "uAh", "/sys/class/power_supply/battery/charge_full")
        raw("battery.charge_full_design", b.chargeDesignUah?.toString() ?: "", "uAh", "/sys/class/power_supply/battery/charge_full_design")
        raw("battery.cycle_count", b.cycleCount?.toString() ?: "", "count", "/sys/class/power_supply/battery/cycle_count")
        raw("battery.status", b.status ?: "", "", "/sys/class/power_supply/battery/status")
        raw("battery.health", b.health ?: "", "", "/sys/class/power_supply/battery/health")
        raw("battery.technology", b.tech ?: "", "", "/sys/class/power_supply/battery/technology")
        raw("battery.charge_type", b.chargeType ?: "", "", "/sys/class/power_supply/battery/charge_type")
        // v0.23.0 P1: RAW 单位随归一化阈值动态标注（部分厂商节点为 mV/mA/MHz）
        fun voltUnit(raw: Long?): String = if (raw != null && raw > 100_000) "uV" else "mV"
        fun currUnit(raw: Long?): String = if (raw != null && raw > 10_000) "uA" else "mA"
        fun cpuUnit(raw: Int?): String = if (raw != null && raw > 100_000) "kHz" else "MHz"
        raw("charger.input_voltage_now", b.inputVoltUv?.toString() ?: "", voltUnit(b.inputVoltUv), "power_supply usb/ucsi/adapter")
        raw("charger.input_current_now", b.inputCurUa?.toString() ?: "", currUnit(b.inputCurUa), "power_supply usb/ucsi/adapter")
        // CPU 每核原始频率（单位随阈值判定：>100_000 kHz，否则 MHz）
        s.cores.forEach { c ->
            raw("cpu${c.index}.scaling_cur_freq", c.freqKHz?.toString() ?: "", cpuUnit(c.freqKHz), "/sys/devices/system/cpu/cpu${c.index}/cpufreq/scaling_cur_freq")
            raw("cpu${c.index}.scaling_max_freq", c.maxKHz?.toString() ?: "", cpuUnit(c.maxKHz), "/sys/devices/system/cpu/cpu${c.index}/cpufreq/scaling_max_freq")
        }
        // P1-6: governor 本帧原始值（从 Snapshot 取，不再现读 sysfs）
        s.governor?.let { raw("cpu.scaling_governor", it, "", "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") }
        // GPU 原始
        raw("gpu.gpu_busy_percentage", s.gpu.busyPercent?.toString() ?: "", "%", "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
        // P1-5: GPU 频率原始值 + 命中节点
        s.gpu.freqRaw?.let { rv ->
            raw("gpu.freq_raw", rv.toString(), "Hz/kHz(见node)", s.gpu.freqNode ?: "")
        }
        // 热区全部原始 mC（不过滤不截断，一个不落）
        s.thermalAll.forEach { z ->
            raw("thermal.${z.name}", z.tempMC?.toString() ?: "", "mC", "/sys/class/thermal/thermal_zone${z.id}/temp")
        }
        // P0-3: /proc/stat 原始 jiffies 行（cpu 总行 + 每核行）
        s.procStat?.let { raw("proc_stat.cpu_total", it, "jiffies", "/proc/stat") }
        s.coreStat.forEachIndexed { i, line ->
            raw("proc_stat.cpu$i", line, "jiffies", "/proc/stat")
        }
        // loadavg / meminfo 原始
        raw("loadavg", s.loadStr ?: "", "", "/proc/loadavg")
        s.mem?.let { m ->
            raw("mem.MemTotal", m.totalKB.toString(), "kB", "/proc/meminfo")
            raw("mem.MemFree", m.freeKB.toString(), "kB", "/proc/meminfo")
            raw("mem.MemAvailable", m.availKB.toString(), "kB", "/proc/meminfo")
            raw("mem.Cached", m.cachedKB.toString(), "kB", "/proc/meminfo")
            raw("mem.SwapTotal", m.swapTotalKB.toString(), "kB", "/proc/meminfo")
            raw("mem.SwapFree", m.swapFreeKB.toString(), "kB", "/proc/meminfo")
        }

        // ===== RENDERED：UI 实际显示值（同换算同过滤，与屏幕一致），含页面归属 =====
        sb.append("# RENDERED - values shown on screen (same conversion/filter as UI)\n")
        sb.append("key,value,unit,page\n")
        fun ren(k: String, v: String, u: String, pg: String) = sb.append("${csv(k)},${csv(v)},${csv(u)},${csv(pg)}\n")
        // P1-10: 小数位全部对齐 UI 主显示（%.1f），健康度取整
        // 仪表盘 / 电池页
        ren("battery.capacity", b.capacity?.toString() ?: "", "%", "仪表盘/电池")
        // P0-2: 功率口径对齐 UI——电池侧 abs V×I（powerShown），不再用带符号的 powerCalcW
        ren("battery.power_battery", b.powerShown?.let { "%.1f".format(f1, it) } ?: "", "W", "仪表盘/电池")
        // P0-2: hero 展示值 = 充电器输入功率优先，缺省电池侧 V×I（逐字复刻电池屏 hero 大字）
        ren("battery.power_shown", (b.inputPowerW ?: b.powerShown)?.let { "%.1f".format(f1, it) } ?: "", "W", "电池")
        ren("battery.charge_mode", b.chargeMode, "", "电池")
        ren("battery.voltage", b.voltage?.let { "%.1f".format(f1, it) } ?: "", "V", "电池")
        ren("battery.current", b.currentDisplay?.let { "%.1f".format(f1, it) } ?: "", "A", "电池")
        ren("battery.temp", b.tempC?.let { "%.1f".format(f1, it) } ?: "", "C", "电池")
        // P1-7: 系统广播温度渲染值
        ren("battery.temp_bm", b.tempBmC?.let { "%.1f".format(f1, it) } ?: "", "C", "电池")
        // P1-10: 健康度 UI 显示整数（Dashboard %.0f / Battery roundToInt）
        ren("battery.health_percent", b.healthPercent?.let { "%.0f".format(f1, it) } ?: "", "%", "电池")
        ren("battery.charge_full", b.chargeFull?.toString() ?: "", "mAh", "电池")
        ren("battery.charge_design", b.chargeDesign?.toString() ?: "", "mAh", "电池")
        ren("battery.cycle", b.cycleCount?.toString() ?: "", "次", "电池")
        ren("charger.input_voltage", b.inputVoltV?.let { "%.1f".format(f1, it) } ?: "", "V", "电池")
        ren("charger.input_current", b.inputCurA?.let { "%.1f".format(f1, it) } ?: "", "A", "电池")
        ren("charger.input_power", b.inputPowerW?.let { "%.1f".format(f1, it) } ?: "", "W", "电池")
        // CPU 页
        // P0-1: CPU 总占用（仪表盘 hero + CPU 页大数字）
        ren("cpu.total_percent", s.cpuTotalPercent?.let { "%.0f".format(f1, it) } ?: "", "%", "仪表盘/CPU")
        s.cores.forEach { c ->
            ren("cpu${c.index}.freq", c.freqMHz?.toString() ?: "", "MHz", "CPU")
            ren("cpu${c.index}.max", c.maxMHz?.toString() ?: "", "MHz", "CPU")
        }
        // P0-1: 8 核分核占用（CPU 页整页卡片）
        s.corePercent.forEachIndexed { i, pct ->
            ren("cpu$i.usage", pct?.toString() ?: "", "%", "CPU")
        }
        // P1-6: governor 从 Snapshot 帧内值取，不再现读 sysfs
        s.governor?.let { ren("cpu.governor", it, "", "CPU") }
        // GPU 页
        ren("gpu.busy", s.gpu.busyPercent?.toString() ?: "", "%", "GPU")
        ren("gpu.freq", s.gpu.freqMHz?.toString() ?: "", "MHz", "GPU")
        // P1-8: GPU 核心 0/1 渲染温度（gpuss-0/gpuss-1）
        ren("gpu.temp0", s.gpu.temp0C?.let { "%.1f".format(f1, it) } ?: "", "C", "GPU")
        ren("gpu.temp1", s.gpu.temp1C?.let { "%.1f".format(f1, it) } ?: "", "C", "GPU")
        // 温度页（同 UI 过滤：最高结温 + 均值 + topN(8)）
        s.maxThermal?.let { z -> ren("thermal.junction_max", z.tempC?.let { x -> "%.1f".format(f1, x) } ?: "", "C", "温度") }
        ren("thermal.avg", s.thermalAvg?.let { "%.1f".format(f1, it) } ?: "", "C", "温度")
        // P1-9: 外壳侧 skin* 最高温（温度屏顶部专设行，铁律只取 skin*）
        s.thermalAll.filter { it.name.startsWith("skin") && it.tempC != null && it.tempC > 0 }
            .maxByOrNull { it.tempC!! }?.let { z ->
                ren("thermal.shell_max", z.tempC?.let { x -> "%.1f".format(f1, x) } ?: "", "C", "温度")
                ren("thermal.shell_name", z.name, "", "温度")
            }
        // tof-therm 46.9℃ 为恒定异常传感器（与 UI 同口径不纳入热区榜），topN 前剔除避免误导
        ThermalReader.topN(s.thermalAll.filter { it.name != "tof-therm" }, 8).forEach { z ->
            ren("thermal_top.${z.name}", z.tempC?.let { x -> "%.1f".format(f1, x) } ?: "", "C", "温度")
        }
        // 内存（P1-10: 小数位对齐 UI %.1f）
        s.mem?.let { m ->
            ren("mem.used_percent", m.usedPercent.toString(), "%", "内存")
            ren("mem.total", "%.1f".format(f1, m.totalGB), "GB", "内存")
            ren("mem.available", "%.1f".format(f1, m.availGB), "GB", "内存")
            ren("mem.swap_used", "%.1f".format(f1, m.swapUsedGB), "GB", "内存")
        }
        ren("loadavg", s.loadStr ?: "", "", "仪表盘")
        // P1-11: 传感器页实时值（加速度/陀螺/磁力 XYZ、光线 lux、距离 cm）
        // 该流仅传感器页可见时刷新，导出时为空属正常，留空即可
        // 主动一次性采样传感器（不依赖用户是否访问过传感器页），保证 RENDERED 覆盖传感器页全部数据
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = HashMap<Int, FloatArray>()
        val wantedTypes = listOf(1, 2, 4, 5, 8)   // accel / mag / gyro / light / proximity
        val sensorListeners = ArrayList<SensorEventListener>()
        for (stype in wantedTypes) {
            val sensor = sm.getDefaultSensor(stype) ?: continue
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) { sensors[stype] = e.values.copyOf() }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
            sensorListeners.add(l)
        }
        // 后台导出线程内短暂等待一帧（多数传感器 16~200ms 内回调），随后立即注销，不持续耗电
        Thread.sleep(350)
        sensorListeners.forEach { sm.unregisterListener(it) }
        // Sensor.TYPE_ACCELEROMETER = 1
        sensors[1]?.let { v ->
            ren("sensor.accel_x", "%.2f".format(f1, v[0]), "m/s²", "传感器")
            ren("sensor.accel_y", "%.2f".format(f1, v[1]), "m/s²", "传感器")
            ren("sensor.accel_z", "%.2f".format(f1, v[2]), "m/s²", "传感器")
        }
        // Sensor.TYPE_MAGNETIC_FIELD = 2
        sensors[2]?.let { v ->
            ren("sensor.mag_x", "%.2f".format(f1, v[0]), "uT", "传感器")
            ren("sensor.mag_y", "%.2f".format(f1, v[1]), "uT", "传感器")
            ren("sensor.mag_z", "%.2f".format(f1, v[2]), "uT", "传感器")
        }
        // Sensor.TYPE_GYROSCOPE = 4
        sensors[4]?.let { v ->
            ren("sensor.gyro_x", "%.3f".format(f1, v[0]), "rad/s", "传感器")
            ren("sensor.gyro_y", "%.3f".format(f1, v[1]), "rad/s", "传感器")
            ren("sensor.gyro_z", "%.3f".format(f1, v[2]), "rad/s", "传感器")
        }
        // Sensor.TYPE_LIGHT = 5
        sensors[5]?.let { v ->
            ren("sensor.light", "%.1f".format(f1, v[0]), "lux", "传感器")
        }
        // Sensor.TYPE_PROXIMITY = 8
        sensors[8]?.let { v ->
            ren("sensor.proximity", "%.1f".format(f1, v[0]), "cm", "传感器")
        }

        val name = "Mio-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".csv"
        // S3: Toast 切回主线程；S1(UX): 导出到公共「下载/Mio」目录——内部 files/ 用户找不到
        val msg = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Mio")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("插入下载目录失败")
                // P2-3: 写盘中途失败 → 删掉已插入的 0 字节条目，不留脏记录
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
                        ?: error("写入失败")
                } catch (t: Throwable) {
                    runCatching { ctx.contentResolver.delete(uri, null, null) }
                    throw t
                }
                // O21: 公共目录只保留最近 5 个导出
                ctx.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    // P2-2: 追加 RELATIVE_PATH 限定，只清理本 App 导出目录，不误删其它位置同名文件
                    "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                    arrayOf("Mio-%.csv", Environment.DIRECTORY_DOWNLOADS + "/Mio/"), null,
                )?.use { c ->
                    val ids = mutableListOf<Long>()
                    while (c.moveToNext()) ids.add(c.getLong(0))
                    ids.sortedDescending().drop(5).forEach { id ->
                        runCatching {
                            ctx.contentResolver.delete(
                                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                                null, null,
                            )
                        }
                    }
                }
                "已导出至 下载/Mio/$name"
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Mio")
                dir.mkdirs()
                File(dir, name).writeText(sb.toString())
                "已导出至 下载/Mio/$name"
            }
        }.getOrElse { "导出失败: ${it.message}" }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
      } finally { csvExporting = false }
    }.start()
}

/** 打开 Shizuku App（已安装则拉起，未安装给出下载提示）——UX(M27): 新用户知道去哪获得 Shizuku */
private fun launchShizuku(ctx: Context) {
    val pm = ctx.packageManager
    val intent = pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        ?: pm.getLaunchIntentForPackage("moe.shizuku.privileged.api.delegate")
    if (intent != null) {
        runCatching { ctx.startActivity(intent) }
            .onFailure { Toast.makeText(ctx, "无法打开 Shizuku", Toast.LENGTH_SHORT).show() }
    } else if (ShizukuBridge.active) {
        // v20.9: 服务 ping 通则说明 Shizuku 已在运行（无线 adb 启动的服务也如此），
        // 不报"未安装"（包可见性查询可能因 <queries> 缺失/非标准包名而失败，但服务状态才是真相）
        Toast.makeText(ctx, "Shizuku 服务运行中", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(ctx, "未安装 Shizuku，可到酷安或官网下载", Toast.LENGTH_LONG).show()
    }
}

/** 设置：外观 · 提权通道 · 双模式 · 采样 · 数据 · 设备信息 · 关于（BiliPai 分组列表） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MonitorViewModel,
    onOpenSensors: () -> Unit = {},
    onOpenAgent: () -> Unit = {},
    onOpenMonitor: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    onOpenToolbox: () -> Unit = {},
) {
    // v0.28.0: 设置页只需权限三态 → privFlow（低频流，整页不再每秒重组）
    val snap by vm.privFlow.collectAsState()
    val ctx = LocalContext.current
    val cap = currentGlassCapability()
    val listState = rememberLazyListState()

    // 外观配置的本地镜像：写入持久化后立即反映到当前页
    var mode by remember { mutableStateOf(ThemeConfig.mode()) }
    var showModeSheet by remember { mutableStateOf(false) }
    var palSel by remember { mutableStateOf<Palette>(ThemeConfig.palette()) }
    var artwork by remember { mutableStateOf(ThemeConfig.showArtwork()) }
    var glassSel by remember { mutableStateOf(GlassConfig.current()) }
    var transparencySel by remember { mutableStateOf(GlassTransparencyConfig.current()) }
    var barStyle by remember { mutableStateOf(BottomBarStyleConfig.current()) }
    var hapticSel by remember { mutableStateOf(MioHaptics.current()) }
    var specSel by remember { mutableStateOf<SpecialTheme>(SpecialConfig.current()) }
    val haptics = rememberHaptics()
    val animScale = rememberAnimatorScale()

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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    /* ---------------- 外观 ---------------- */
                    item {
                        MioSectionHeader(
                            "外观",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        MioPreferenceGroup {
                            MioChoiceRow(
                                title = "明暗模式",
                                value = mode.label,
                                subtitle = "跟随系统，或锁定浅色 / 深色",
                                onClick = { showModeSheet = true },
                            )
                            PalettePicker(
                                selected = palSel,
                                onSelect = { p ->
                                    palSel = p
                                    ThemeConfig.set(ctx, p)
                                },
                            )
                            MioSwitchRow(
                                title = "显示角色立绘",
                                subtitle = "页面背景展示当前角色插画",
                                checked = artwork,
                                onCheckedChange = { on ->
                                    artwork = on
                                    ThemeConfig.setArtwork(ctx, on)
                                },
                            )
                            val specials = Specials.all()
                            if (specials.size > 1) {
                                CharacterPicker(
                                    selected = specSel,
                                    specials = specials,
                                    onSelect = { sp ->
                                        specSel = sp
                                        SpecialConfig.set(ctx, sp)
                                        // 角色自带配色时一并套用；澪不绑定配色，保留当前配色预设
                                        sp.palette?.let { p ->
                                            palSel = p
                                            ThemeConfig.set(ctx, p)
                                        }
                                    },
                                )
                            }
                            // v3.3 液态玻璃三档：完整（最通透，Miuix Backdrop 折射）/ 简约（仅外壳玻璃、卡片更实更省电）/ 关闭（实色）
                            SegmentSetting(
                                title = "液态玻璃",
                                subtitle = when {
                                    cap.sdkInt >= Build.VERSION_CODES.TIRAMISU ->
                                        "完整 = 全表面液态玻璃（实时折射 + 高光）；简约 = 仅外壳玻璃、卡片更实更省电；关闭 = 实色"
                                    cap.sdkInt >= Build.VERSION_CODES.S ->
                                        "完整 = 全表面实时模糊；简约 = 仅外壳模糊、卡片更实更省电；关闭 = 实色"
                                    else -> "当前系统不支持实时模糊，玻璃为半透明材质；关闭 = 实色"
                                },
                                labels = GLASS_LABELS,
                                selectedIndex = GLASS_LEVELS.indexOf(glassSel).coerceAtLeast(0),
                                onSelect = { idx ->
                                    glassSel = GLASS_LEVELS[idx]
                                    GlassConfig.set(ctx, GLASS_LEVELS[idx])
                                },
                            )
                            // v3.6 全局玻璃透明度四档：只缩放玻璃填充 alpha，不改模糊/折射/可读层
                            SegmentSetting(
                                title = "玻璃透明度",
                                subtitle = "低 = OriginOS 高级材质参考（强模糊 + 透彩色）；中 / 高 / 全透逐级更透，全透不带 tint；" +
                                    "只缩放玻璃填充浓度，可读层与模糊/折射不变；液态玻璃关闭时不生效",
                                labels = GLASS_TRANSPARENCY_LABELS,
                                selectedIndex = GLASS_TRANSPARENCY_LEVELS
                                    .indexOf(transparencySel).coerceAtLeast(0),
                                onSelect = { idx ->
                                    transparencySel = GLASS_TRANSPARENCY_LEVELS[idx]
                                    GlassTransparencyConfig.set(
                                        ctx,
                                        GLASS_TRANSPARENCY_LEVELS[idx],
                                    )
                                },
                            )                            // 只读自检行：一眼确认当前实际走的渲染引擎（排查"看起来不像玻璃"最有效）
                            MioSettingRow(
                                title = "玻璃引擎",
                                value = liquidEngineLabel(
                                    cap,
                                    bottomBarBiliPai = barStyle == BottomBarStyle.BILIPAI,
                                ),
                            )
                            // v3.4：底部导航栏实现 —— 默认直接使用 BiliPai 原版 FloatingBottomBar
                            SegmentSetting(
                                title = "底部导航栏",
                                subtitle = "BiliPai 原版 = 直接使用 BiliPai 开源项目的 FloatingBottomBar（实时折射、拖动选择、重力高光；需 Android 13+）；Mio 简版 = Haze 玻璃 dock",
                                labels = BOTTOM_BAR_LABELS,
                                selectedIndex = BOTTOM_BAR_STYLES.indexOf(barStyle).coerceAtLeast(0),
                                onSelect = { idx ->
                                    barStyle = BOTTOM_BAR_STYLES[idx]
                                    BottomBarStyleConfig.set(ctx, BOTTOM_BAR_STYLES[idx])
                                },
                            )
                            SegmentSetting(
                                title = "触感强度",
                                subtitle = "按住「轻 / 中 / 重」任意一键可持续震动（松手即停），轻点选中该档",
                                labels = HAPTIC_LABELS,
                                selectedIndex = HAPTIC_LEVELS.indexOf(hapticSel).coerceAtLeast(0),
                                onSelect = { idx ->
                                    hapticSel = HAPTIC_LEVELS[idx]
                                    MioHaptics.set(ctx, HAPTIC_LEVELS[idx])
                                    haptics.press()
                                },
                                onPressStart = { idx -> MioHaptics.startHold(ctx, HAPTIC_LEVELS[idx]) },
                                onPressEnd = { MioHaptics.stopHold(ctx) },
                            )
                            MioSettingRow(
                                title = "界面动效",
                                value = when {
                                    animScale <= 0f -> "已关闭"
                                    animScale == 1f -> "标准"
                                    else -> "×%.1f".format(animScale)
                                },
                                subtitle = "跟随系统「动画时长缩放」，不额外提供开关",
                            )
                        }
                    }

                    /* ---------------- 提权通道 ---------------- */
                    item {
                        MioSectionHeader(
                            "提权通道",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        MioPreferenceGroup {
                            StatusBadgeRow(
                                title = "Shizuku",
                                subtitle = when {
                                    snap.shizukuActive -> "API 已授权 · 服务 ping 通"
                                    ShizukuBridge.active -> "服务在 · 未授权（点下方一键授权）"
                                    else -> "服务未启动（请先在 Shizuku App 中启动）"
                                },
                                // S6: 三态：已授权(蓝) / 服务在未授权(黄) / 未启动(灰)
                                badgeColor = when {
                                    snap.shizukuActive -> Ink.accent
                                    ShizukuBridge.active -> Ink.warn
                                    else -> Ink.off
                                },
                                badgeText = when {
                                    snap.shizukuActive -> "已激活"
                                    ShizukuBridge.active -> "待授权"
                                    else -> "未启动"
                                },
                            )
                            StatusBadgeRow(
                                title = "Root",
                                subtitle = if (snap.rootAvailable) {
                                    "已检测到 su · Root 优先，R 档项已解锁"
                                } else {
                                    "未检测到 su（无 Root 时用 Shizuku 解锁 S 档项）"
                                },
                                // v20.12(S2-16): Root 徽章用橙色，与顶栏 Root 语义统一
                                badgeColor = if (snap.rootAvailable) Ink.warn else Ink.off,
                                badgeText = if (snap.rootAvailable) "已激活" else "未激活",
                            )
                            Column(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                // v20.14: 一键授权（优先 Root；无 Root 用 Shizuku）——IO 异步，不卡 UI
                                val authScope = rememberCoroutineScope()
                                ProbeButton(
                                    text = "一键授权（优先 Root）",
                                    accent = !(snap.rootAvailable || snap.shizukuActive),
                                    onClick = {
                                        authScope.launch {
                                            val gotRoot = withContext(Dispatchers.IO) {
                                                RootBridge.refresh()
                                                RootBridge.available
                                            }
                                            if (gotRoot) {
                                                withContext(Dispatchers.Main) { vm.refreshShizuku() }
                                                return@launch
                                            }
                                            val active = withContext(Dispatchers.IO) { ShizukuBridge.refresh() }
                                            if (active) {
                                                if (!ShizukuBridge.authorized) ShizukuBridge.requestPermission()
                                                withContext(Dispatchers.Main) { vm.refreshShizuku() }
                                            } else {
                                                Toast.makeText(
                                                    ctx,
                                                    "未检测到 Root / Shizuku 服务，当前为免提权模式",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                // UX(M27): 给新用户一条"获得 Shizuku"的明确路径
                                ProbeButton(
                                    text = "打开 Shizuku App",
                                    onClick = { launchShizuku(ctx) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    /* ---------------- 局域网双模式 ---------------- */
                    item {
                        MioSectionHeader(
                            "局域网双模式",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        MioPreferenceGroup {
                            MioChoiceRow(
                                title = "被监控模式",
                                subtitle = "本机作为服务端广播，允许其他设备查看",
                                value = "进入",
                                onClick = onOpenAgent,
                            )
                            MioChoiceRow(
                                title = "监控其他设备",
                                subtitle = "mDNS 自动发现同网段的被监控设备",
                                value = "进入",
                                onClick = onOpenMonitor,
                            )
                        }
                    }
                    item {
                        Text(
                            "两台手机装同一个 App：一台被监控、一台监控，自动发现、全自动连接。",
                            color = Ink.off, fontSize = 11.sp, lineHeight = 15.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    /* ---------------- 采样 ---------------- */
                    item {
                        MioSectionHeader(
                            "采样",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        MioPreferenceGroup {
                            MioSettingRow(
                                title = "采样周期",
                                value = "1s（固定）",
                                subtitle = "前台实时刷新，后台自动降为 3s",
                            )
                            // v20.17(P1-11): 两个"规划中"占两行是噪音——折叠成一行，一眼扫过
                            MioSettingRow(
                                title = "24h 历史",
                                value = "规划中",
                                subtitle = "本地长周期记录",
                            )
                        }
                    }

                    /* ---------------- 数据 ---------------- */
                    item {
                        MioSectionHeader(
                            "数据",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        MioPreferenceGroup {
                            MioChoiceRow(
                                title = "导出快照 CSV",
                                subtitle = "全部参数（写入 下载/Mio）",
                                value = "导出",
                                onClick = { exportCsv(vm, ctx) },
                            )
                            MioChoiceRow(
                                title = "传感器总览",
                                subtitle = "实时通道与全部传感器列表",
                                value = "查看",
                                onClick = onOpenSensors,
                            )
                            MioChoiceRow(
                                title = "使用引导",
                                subtitle = "重看首次启动的完整功能说明",
                                value = "重看",
                                onClick = onShowOnboarding,
                            )
                        }
                    }

                    /* ---------------- 设备信息 ---------------- */
                    item {
                        MioSectionHeader(
                            "设备信息",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        // v20.21(P2-16): 读 /proc 移出组合——组合每秒重组，不再反复在主线程读文件
                        var soc by remember { mutableStateOf("–") }
                        var kernel by remember { mutableStateOf("–") }
                        var memInfo by remember { mutableStateOf("–") }
                        var storageInfo by remember { mutableStateOf("–") }
                        // v0.23.0: SoC 平台识别结果（PlatformDetector，进程级缓存）
                        var platformLine by remember { mutableStateOf("–") }
                        var boardPlatform by remember { mutableStateOf("–") }
                        LaunchedEffect(Unit) {
                            val r = withContext(Dispatchers.IO) {
                                listOf(readSoc(), readKernel(), readMem(), readStorage())
                            }
                            soc = r[0]; kernel = r[1]; memInfo = r[2]; storageInfo = r[3]
                            // 平台识别在 IO 线程执行（内部 getprop 可能起进程），结果恒定
                            val p = withContext(Dispatchers.IO) {
                                com.a41probe.monitor.data.PlatformDetector.get()
                            }
                            platformLine = p.displayLine
                            boardPlatform = p.boardPlatform ?: "–"
                        }
                        // v20.17(P1-11): 静态信息去绿点——色点语义留给"健康/异常"，设备信息不参与
                        MioCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                            MioInfoRow("品牌", "${Build.BRAND} · ${Build.MANUFACTURER}")
                            MioInfoRow("机型", Build.MODEL)
                            MioInfoRow("SoC", soc)
                            MioInfoRow("平台", platformLine)
                            MioInfoRow("Board Platform", boardPlatform)
                            MioInfoRow("系统", "Android ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}")
                            MioInfoRow("安全补丁", Build.VERSION.SECURITY_PATCH ?: "–")
                            MioInfoRow("内核", kernel)
                            MioInfoRow("内存", memInfo)
                            MioInfoRow("屏幕", readScreen(ctx))
                            MioInfoRow("存储", storageInfo)
                        }
                    }

                    /* ---------------- 实验功能（OriginOS 调校，v3.5） ---------------- */
                    item {
                        MioSectionHeader(
                            "实验功能",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        MioPreferenceGroup {
                            MioChoiceRow(
                                title = "OriginOS 调校",
                                value = "默认关闭",
                                subtitle = "触控拉满 / 强开插帧 / 高刷锁定 / 视效增强；会写入系统设置，支持一键恢复",
                                onClick = onOpenToolbox,
                            )
                        }
                    }

                    /* ---------------- 关于 ---------------- */
                    item {
                        MioSectionHeader(
                            "关于",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        MioCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                            MioInfoRow(
                                "App",
                                "v${BuildConfig.VERSION_NAME} · 监控只读 · 调校默认关闭",
                                stateColor = Ink.accent,
                                mono = false,
                            )
                            // v1.3.0: 当前角色（头像随角色预设变化；素材缺失则不画图只留文字）
                            val curSpec = SpecialConfig.current()
                            val curThumb = curSpec.thumbRes()
                            if (curThumb != 0) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(curThumb),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(34.dp).clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("当前角色", color = Ink.tx3, fontSize = 11.sp)
                                        Text(
                                            curSpec.label,
                                            color = Ink.tx, fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "纯只读 · 缺失项置灰，不显示伪造值",
                            color = Ink.off, fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
    }

    /* ---------------- 明暗模式选择弹层（Miuix 底部弹层 · 液态玻璃） ---------------- */
    if (showModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModeSheet = false },
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.18f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            dragHandle = null,
        ) {
            ModalWindowBlurBehindEffect(enabled = cap.realtimeBlur, radius = 28.dp)
            MioGlassSheetSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 24.dp, bottom = 24.dp),
                ) {
                    Text(
                        "明暗模式",
                        color = Ink.tx, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "跟随系统时自动响应系统的深色模式设置",
                        color = Ink.tx2, fontSize = 12.5.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    ThemeMode.entries.forEach { m ->
                        val selected = m == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    mode = m
                                    ThemeConfig.setMode(ctx, m)
                                    showModeSheet = false
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                m.label,
                                color = if (selected) Ink.accent else Ink.tx,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "已选",
                                    tint = Ink.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================================================
 * 设置页局部组件（Miuix 行语言：16dp 左右内边距 + 标题/副标题两级）
 * ============================================================ */

/** 两行标题 + 右侧状态徽章（Shizuku / Root 行） */
@Composable
private fun StatusBadgeRow(
    title: String,
    subtitle: String,
    badgeColor: Color,
    badgeText: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Ink.tx, fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Ink.tx2, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        DotBadge(badgeColor, badgeText)
    }
}

/** 配色预设：横排色点 + 选中高亮（沿用 ThemeConfig.set 持久化） */
@Composable
private fun PalettePicker(
    selected: Palette,
    onSelect: (Palette) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("配色预设", color = Ink.tx, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "切换全局强调色与页面底色；温度红 / 橙 / 绿等语义色保持不变，避免影响数据判读",
            color = Ink.tx2, fontSize = 12.sp, lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Palettes.ALL.forEach { p ->
                val sel = p.id == selected.id
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Ink.accentSoft else Color.Transparent)
                        .clickable { onSelect(p) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(p.accent)
                            .border(
                                width = if (sel) 2.dp else 1.dp,
                                color = if (sel) Ink.tx else Ink.stroke,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        p.label,
                        color = if (sel) Ink.tx else Ink.tx3,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 角色预设：整组切换立绘与配色（素材缺失的角色不会出现在这里） */
@Composable
private fun CharacterPicker(
    selected: SpecialTheme,
    specials: List<SpecialTheme>,
    onSelect: (SpecialTheme) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("角色预设", color = Ink.tx, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "整组切换立绘与配色，与上面的配色预设相互独立——先挑角色，再用配色微调",
            color = Ink.tx2, fontSize = 12.sp, lineHeight = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            specials.forEach { sp ->
                val sel = sp.id == selected.id
                val tint = sp.palette?.accent ?: Ink.accent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Ink.accentSoft else Ink.panel)
                        .then(
                            if (sel) Modifier.border(1.5.dp, tint, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clickable { onSelect(sp) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val th = sp.thumbRes()
                    if (th != 0) {
                        Image(
                            painter = painterResource(th),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            sp.label, color = Ink.tx, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(sp.subtitle, color = Ink.tx2, fontSize = 11.sp)
                    }
                    if (sel) {
                        Text(
                            "当前", color = tint, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** 三档分段设置行（液态玻璃 / 触感强度共用）：Miuix 行 + 分段胶囊 */
@Composable
private fun SegmentSetting(
    title: String,
    subtitle: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onPressStart: ((Int) -> Unit)? = null,
    onPressEnd: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = Ink.tx, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Ink.tx2, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { idx, label ->
                val sel = idx == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Ink.accent else Ink.panel)
                        .pointerInput(idx) {
                            detectTapGestures(
                                onPress = {
                                    onPressStart?.invoke(idx)
                                    tryAwaitRelease()
                                    onPressEnd?.invoke()
                                },
                                onTap = { onSelect(idx) },
                            )
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (sel) Ink.onAccent else Ink.tx2,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
