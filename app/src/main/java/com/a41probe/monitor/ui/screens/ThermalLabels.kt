package com.a41probe.monitor.ui.screens

/**
 * v3.13：温度页标签中文化（纯函数，可单测）。
 *
 * 只翻译能可靠对应到中文含义的前缀/类型；型号号、未知专有名词保留原文，避免伪造。
 */
internal fun zoneNameZh(raw: String): String {
    val n = raw.trim()
    val (zh, rest) = when {
        n.startsWith("cpuss") -> "CPU" to n.removePrefix("cpuss")
        n.startsWith("cpu") -> "CPU" to n.removePrefix("cpu")
        n.startsWith("gpuss") -> "GPU" to n.removePrefix("gpuss")
        n.startsWith("gpu") -> "GPU" to n.removePrefix("gpu")
        n.startsWith("nsphvx") -> "NPU" to n.removePrefix("nsphvx")
        n.startsWith("nsphmx") -> "NPU" to n.removePrefix("nsphmx")
        n.startsWith("nsp") -> "NPU" to n.removePrefix("nsp")
        n.startsWith("ddr") -> "内存" to n.removePrefix("ddr")
        n.startsWith("video") -> "多媒体" to n.removePrefix("video")
        n.startsWith("aoss") -> "应用子系统" to n.removePrefix("aoss")
        n.startsWith("mdmss") -> "基带" to n.removePrefix("mdmss")
        n.startsWith("modem") -> "基带" to n.removePrefix("modem")
        n.startsWith("camera") -> "相机" to n.removePrefix("camera")
        n.startsWith("quiet") -> "静默温区" to n.removePrefix("quiet")
        n.startsWith("tz_game_shell") -> "游戏外壳" to n.removePrefix("tz_game_shell")
        n.startsWith("tz_shell") -> "外壳" to n.removePrefix("tz_shell")
        n.startsWith("skin") -> "外壳" to n.removePrefix("skin")
        n.startsWith("sub_board") -> "副板" to n.removePrefix("sub_board")
        n.startsWith("usb") -> "充电口" to n.removePrefix("usb")
        n.startsWith("tof") -> "ToF 相机" to n.removePrefix("tof")
        n.startsWith("wlan") -> "Wi-Fi" to n.removePrefix("wlan")
        n.startsWith("ltepa") -> "4G 射频" to n.removePrefix("ltepa")
        n.startsWith("nrpa") -> "5G 射频" to n.removePrefix("nrpa")
        n.startsWith("pa") -> "射频" to n.removePrefix("pa")
        n.startsWith("mmw") -> "毫米波" to n.removePrefix("mmw")
        n.startsWith("sdr") -> "软件无线电" to n.removePrefix("sdr")
        n.startsWith("bq_bms") -> "电量计" to n.removePrefix("bq_bms")
        n.startsWith("fg_") -> "电量计" to n.removePrefix("fg_")
        n.startsWith("chg_ic") -> "充电 IC" to n.removePrefix("chg_ic")
        n.startsWith("bat_btb") -> "电池 BTB" to n.removePrefix("bat_btb")
        n.startsWith("battery") -> "电池" to n.removePrefix("battery")
        n.startsWith("wireless") -> "无线充电" to n.removePrefix("wireless")
        n.startsWith("vir_tbatwls") -> "无线充电电池" to n.removePrefix("vir_tbatwls")
        n.startsWith("vir_tbat") -> "虚拟电池" to n.removePrefix("vir_tbat")
        n.startsWith("vir_amb") -> "虚拟环境" to n.removePrefix("vir_amb")
        n.startsWith("pm") -> "电源芯片" to n.removePrefix("pm")
        else -> return raw
    }
    val suffix = rest
        .replaceFirst("-", " ")
        .replace("_", " ")
        .replace(Regex("(?i)(therm|temperature|sensor|ntc|tz|temp)"), "")
        .trim()
    return if (suffix.isBlank()) zh else "$zh $suffix"
}

/** v3.13：冷却设备类型 → 中文描述。 */
internal fun coolingZh(type: String): String = when {
    type.startsWith("cpufreq-cpu") -> "CPU ${type.removePrefix("cpufreq-cpu")} 限频"
    type.startsWith("cpu-hotplug") -> "CPU ${type.removePrefix("cpu-hotplug")} 热插拔"
    type.startsWith("pause-cpu") -> "暂停 CPU ${type.removePrefix("pause-cpu")}"
    type.startsWith("thermal-pause-") -> "热暂停 ${type.removePrefix("thermal-pause-")}"
    type.startsWith("cpu-cluster") -> "CPU 簇 ${type.removePrefix("cpu-cluster")} 限频"
    type == "gpu" || type == "kgsl" || type == "gpu-dump-skip-cdev" -> "GPU 限频"
    type == "ddr-cdev" -> "内存降速"
    type == "display-fps" -> "屏幕降帧"
    type == "panel0-backlight" -> "屏幕降亮"
    type == "ufs" -> "存储限速"
    type == "battery" -> "充电限流"
    type == "cdsp_hw" -> "NPU 限频"
    type == "cdsp" -> "DSP 限频"
    type == "wlan" -> "Wi-Fi 限速"
    type.startsWith("modem") -> "基带降速"
    type.startsWith("pa") || type.contains("_pa_") -> "射频降速"
    type.startsWith("mmw") -> "毫米波降速"
    type.startsWith("thermal-devfreq") -> "总线限频"
    else -> type
}

/** HAL 官方温度行/小节文案。 */
internal fun halTempLabel(name: String): String = "官方 $name"

internal fun halSectionTitle(count: Int): String = "官方温度 · $count 项"
