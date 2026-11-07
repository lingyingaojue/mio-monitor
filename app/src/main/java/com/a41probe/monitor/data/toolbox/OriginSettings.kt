package com.a41probe.monitor.data.toolbox

import android.os.Build

/* ============================================================
 * OriginOS 实调功能（v3.5）
 *
 * 依据：对 iQOO Neo10 Pro+（V2463A / OriginOS，2026-11）的 ADB 实测：
 *  - vivo 封了 `settings put`（exit=0 但静默丢弃），但 `content insert` 可写；
 *  - `settings delete` 可删（清理实测 "Deleted 1 rows"）；
 *  - `content query` / `settings get` 只读可用。
 *
 * 本文件只放纯逻辑（白名单/命令构造/值格式/状态判定/设备门槛），可在 JVM 单测。
 * 不做文件里的假功能（Monster+/BOOST+/直驱供电/满亮度）。
 * ============================================================ */

/** 设置命名空间（只允许这两个，避免任意 URI）。 */
enum class ToolboxNamespace(val ns: String) {
    GLOBAL("global"),
    SYSTEM("system"),
}

/** 一个要读写的系统设置 key。 */
data class ToolboxKey(val namespace: ToolboxNamespace, val name: String)

/** 四个实测可用的调校功能。 */
enum class ToolboxFeature(
    val id: String,
    val title: String,
    val summary: String,
    val risk: String,
    /** v3.15: Root 专用功能，禁止用 ADB 直写/Shizuku 冒充。 */
    val requiresRoot: Boolean = false,
) {
    TOUCH_BOOST(
        id = "touch_boost",
        title = "触控拉满",
        summary = "提高触控采样率（GameWatch 读取 game_memc_request_touch_rate）",
        risk = "采样率提高，功耗略增；长时间高采样对屏幕有压力，建议需要时开。",
    ),
    FRAME_INTERPOLATION(
        id = "frame_interpolation",
        title = "强开全局插帧",
        summary = "MEMC 插帧，不要求应用在白名单游戏列表（GameCube 读取）",
        risk = "插帧会降低原生分辨率；目标帧率要匹配屏幕刷新率，不是越高越好。",
    ),
    REFRESH_LOCK(
        id = "refresh_lock",
        title = "全局高刷锁定",
        summary = "锁定最小/峰值刷新率（AOSP 标准 key，services.jar 真读）",
        risk = "锁高刷后待机更费电、静态画面不降频；建议需要时开。",
    ),
    VISUAL_ENHANCE(
        id = "visual_enhance",
        title = "全局视效增强",
        summary = "提高画面色彩对比度（GameCube 读取 gamecube_hawkeye_effective）",
        risk = "仅改 hawkeye 这一个 key，不修改色彩模式，避免偏色。",
    ),
    MONSTER_PLUS(
        id = "monster_plus",
        title = "Monster+ 模式",
        summary = "vivo/iQOO 性能调度模式（PEM/GameCube 读取 game_plus_mode_key）",
        risk = "性能调度更激进，发热和耗电明显增加；仅建议游戏时开，Root 专用。",
        requiresRoot = true,
    ),
}

/** 各功能涉及的 settings key（固定白名单）。 */
fun keysOf(feature: ToolboxFeature): List<ToolboxKey> = when (feature) {
    ToolboxFeature.TOUCH_BOOST -> listOf(
        ToolboxKey(ToolboxNamespace.GLOBAL, "game_memc_request_touch_rate"),
    )
    ToolboxFeature.FRAME_INTERPOLATION -> listOf(
        ToolboxKey(ToolboxNamespace.SYSTEM, "gamecube_frame_interpolation"),
    )
    ToolboxFeature.REFRESH_LOCK -> listOf(
        ToolboxKey(ToolboxNamespace.SYSTEM, "min_refresh_rate"),
        ToolboxKey(ToolboxNamespace.SYSTEM, "peak_refresh_rate"),
    )
    ToolboxFeature.VISUAL_ENHANCE -> listOf(
        ToolboxKey(ToolboxNamespace.SYSTEM, "gamecube_hawkeye_effective"),
    )
    ToolboxFeature.MONSTER_PLUS -> listOf(
        ToolboxKey(ToolboxNamespace.SYSTEM, "game_plus_mode_key"),
    )
}

/** 插帧值：字符串格式 `enabled:mode:? :from:to`（实测格式 `1:1:0:30:120`）。 */
data class FrameInterpolationValue(
    val fromFps: Int,
    val toFps: Int,
    val enabled: Boolean = true,
    val mode: Int = 1,
) {
    fun format(): String = "${if (enabled) 1 else 0}:$mode:0:$fromFps:$toFps"

    companion object {
        fun parse(raw: String?): FrameInterpolationValue? {
            if (raw == null) return null
            val parts = raw.trim().split(':')
            if (parts.size != 5) return null
            val enabled = parts[0].toIntOrNull() ?: return null
            val mode = parts[1].toIntOrNull() ?: return null
            val from = parts[3].toIntOrNull() ?: return null
            val to = parts[4].toIntOrNull() ?: return null
            if (enabled != 1) return FrameInterpolationValue(0, 0, enabled = false, mode = mode)
            if (from <= 0 || to <= from) return null
            return FrameInterpolationValue(fromFps = from, toFps = to, enabled = true, mode = mode)
        }

        /** 关闭时系统默认值（部分机型会保留 0:-1:0:0:0）。 */
        fun disabled(): FrameInterpolationValue = FrameInterpolationValue(0, 0, enabled = false)
    }
}

/** 插帧预设（按设备支持的刷新率给出，144/120 为本机实测支持值）。 */
data class InterpolationPreset(val fromFps: Int, val toFps: Int) {
    val label: String get() = "$fromFps → $toFps 帧"
}

val INTERPOLATION_PRESETS: List<InterpolationPreset> = listOf(
    InterpolationPreset(30, 120),
    InterpolationPreset(30, 144),
    InterpolationPreset(60, 120),
    InterpolationPreset(60, 144),
)

/** 高刷锁定目标（本机实测支持 120/144）。 */
val REFRESH_TARGETS: List<Int> = listOf(120, 144)

/** 高刷 key 的值格式：`144.0`。 */
fun refreshValueOf(hz: Int): String = "%.1f".format(hz.toFloat())

/** 当前值解析出「是否开启」；null（key 不存在）视为关闭。 */
fun resolveFeatureOn(feature: ToolboxFeature, values: Map<String, String?>): Boolean = when (feature) {
    ToolboxFeature.TOUCH_BOOST ->
        values[keyName(ToolboxFeature.TOUCH_BOOST)] == "1"
    ToolboxFeature.FRAME_INTERPOLATION ->
        FrameInterpolationValue.parse(values[keyName(ToolboxFeature.FRAME_INTERPOLATION)])
            ?.enabled == true
    ToolboxFeature.REFRESH_LOCK -> {
        val min = values["system.min_refresh_rate"]
        val peak = values["system.peak_refresh_rate"]
        min != null && min == peak
    }
    ToolboxFeature.VISUAL_ENHANCE ->
        values[keyName(ToolboxFeature.VISUAL_ENHANCE)] == "1"
    ToolboxFeature.MONSTER_PLUS ->
        // PEM PsmObserver 用 >0 判断开启；GameCube 面板用 ==1；两者都覆盖
        (values[keyName(ToolboxFeature.MONSTER_PLUS)]?.toIntOrNull() ?: 0) > 0
}

private fun keyName(feature: ToolboxFeature): String {
    val k = keysOf(feature).first()
    return "${k.namespace.ns}.${k.name}"
}

/** 把 values map 的键统一成 `namespace.key`。 */
fun readKeyId(key: ToolboxKey): String = "${key.namespace.ns}.${key.name}"

/** 功能开启时要写入的值（refresh 需返回多个 key）。 */
fun valuesToWrite(feature: ToolboxFeature, interp: FrameInterpolationValue, refreshHz: Int): Map<ToolboxKey, String> = when (feature) {
    ToolboxFeature.TOUCH_BOOST -> mapOf(keysOf(feature).first() to "1")
    ToolboxFeature.FRAME_INTERPOLATION -> mapOf(keysOf(feature).first() to interp.format())
    ToolboxFeature.REFRESH_LOCK -> keysOf(feature).associateWith { refreshValueOf(refreshHz) }
    ToolboxFeature.VISUAL_ENHANCE -> mapOf(keysOf(feature).first() to "1")
    ToolboxFeature.MONSTER_PLUS -> mapOf(keysOf(feature).first() to "1")
}

/* ---------------- shell 命令构造（Shizuku / Root 通道） ---------------- */

/** key 白名单校验，防止任意 shell 注入。 */
fun isValidKeyName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 64 && name.matches(Regex("[a-zA-Z0-9_.-]+"))

/** 值白名单校验（数字/字母/冒号/点/下划线/负号）。 */
fun isValidSettingValue(value: String): Boolean =
    value.isNotEmpty() && value.length <= 128 && value.matches(Regex("[a-zA-Z0-9:._-]+"))

/** 删除一条 key：`settings delete <ns> <key>`（本机实测可用，比 content delete 少一层引号问题）。 */
fun deleteCommand(key: ToolboxKey): String {
    require(isValidKeyName(key.name)) { "invalid key" }
    return "settings delete ${key.namespace.ns} ${key.name}"
}

/** 写入一条 key：必须用 `content insert`（vivo 的 settings put 会静默丢弃）。 */
fun insertCommand(key: ToolboxKey, value: String): String {
    require(isValidKeyName(key.name)) { "invalid key" }
    require(isValidSettingValue(value)) { "invalid value" }
    return "content insert --uri content://settings/${key.namespace.ns}" +
        " --bind name:s:${key.name} --bind value:s:$value"
}

/** 一条写入脚本：先删后插，再回读（`;` 保证前一条失败也继续）。 */
fun writeScript(key: ToolboxKey, value: String): String =
    deleteCommand(key) + " ; " + insertCommand(key, value)

/** 删除脚本（恢复系统默认）。 */
fun deleteScript(key: ToolboxKey): String = deleteCommand(key)

/** 解析 `settings get` 输出：`null` / 空 → null。 */
fun parseSettingsGet(output: String?): String? {
    val v = output?.trim() ?: return null
    if (v.isEmpty() || v == "null") return null
    return v
}

/** 解析 `content query` 的 `name=xxx, value=yyy` 行（备用读取通道）。 */
fun parseContentQueryLine(line: String, key: String): String? {
    // 例：Row: 82 name=game_memc_request_touch_rate, value=0
    if (!line.contains("name=$key")) return null
    val marker = "value="
    val idx = line.indexOf(marker)
    if (idx < 0) return null
    return line.substring(idx + marker.length).trim().takeIf { it.isNotEmpty() && it != "null" }
}

/* ---------------- 设备门槛 ---------------- */

/** 仅在 vivo/iQOO OriginOS 设备上展示可写调校（其它设备保持只读监控）。 */
fun isOriginTuningSupported(
    manufacturer: String = Build.MANUFACTURER ?: "",
    brand: String = Build.BRAND ?: "",
    display: String = Build.DISPLAY ?: "",
): Boolean {
    val m = manufacturer.lowercase()
    val b = brand.lowercase()
    val d = display.lowercase()
    return m.contains("vivo") || b.contains("vivo") || b.contains("iqoo") ||
        d.contains("pd") || d.contains("vivo")
}

/** v3.15: Monster+ 仅 vivo/iQOO OriginOS 机型展示；实际写入前仍需 Root。 */
fun isMonsterPlusSupported(
    manufacturer: String = Build.MANUFACTURER ?: "",
    brand: String = Build.BRAND ?: "",
    display: String = Build.DISPLAY ?: "",
): Boolean = isOriginTuningSupported(manufacturer, brand, display)

/** 功能状态（UI 展示 + 守护判定）。 */
enum class ToolboxFeatureState { ON, OFF, ABSENT, UNKNOWN }
