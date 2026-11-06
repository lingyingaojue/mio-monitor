package com.a41probe.monitor.data.toolbox

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * 调校配置与原始值备份（v3.5）。
 *
 * SharedPreferences `mio_toolbox`：
 *  - acknowledged            用户已确认实验功能风险
 *  - guard_enabled           是否开启前台守护
 *  - enabled_<feature>       功能是否处于"应生效"状态（系统重置后据此自动重写）
 *  - interp_from/to          插帧预设
 *  - refresh_target          高刷锁定目标（120/144）
 *  - backup:<ns>.<key>       首次写入前的原始值；`__absent__` 表示原本不存在
 *
 * 该对象会被 BroadcastReceiver / 前台服务在进程冷启动时直接访问，
 * 因此 prefs 使用可空延迟初始化：未 init 时读取返回安全默认值，绝不抛异常。
 */
object ToolboxConfig {

    private const val PREFS = "mio_toolbox"
    private const val KEY_ACK = "acknowledged"
    private const val KEY_GUARD = "guard_enabled"
    private const val KEY_INTERP_FROM = "interp_from"
    private const val KEY_INTERP_TO = "interp_to"
    private const val KEY_REFRESH = "refresh_target"
    private const val PREFIX_ENABLED = "enabled_"
    private const val PREFIX_BACKUP = "backup:"
    private const val ABSENT = "__absent__"

    private var prefsRef: SharedPreferences? = null
    private val ackState = mutableStateOf(false)

    fun init(ctx: Context) {
        prefsRef = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ackState.value = prefsRef?.getBoolean(KEY_ACK, false) ?: false
    }

    /** 冷启动的 receiver/service 入口调用：未初始化时补一次 init。 */
    fun ensureInit(ctx: Context) {
        if (prefsRef == null) init(ctx)
    }

    fun acknowledged(): Boolean = ackState.value

    fun setAcknowledged(value: Boolean) {
        ackState.value = value
        prefsRef?.edit()?.putBoolean(KEY_ACK, value)?.apply()
    }

    fun featureEnabled(feature: ToolboxFeature): Boolean =
        prefsRef?.getBoolean(PREFIX_ENABLED + feature.id, false) ?: false

    fun setFeatureEnabled(feature: ToolboxFeature, enabled: Boolean) {
        prefsRef?.edit()?.putBoolean(PREFIX_ENABLED + feature.id, enabled)?.apply()
    }

    fun enabledFeatures(): List<ToolboxFeature> =
        ToolboxFeature.entries.filter { featureEnabled(it) }

    fun interpolation(): FrameInterpolationValue {
        val p = prefsRef ?: return FrameInterpolationValue(30, 120)
        val from = p.getInt(KEY_INTERP_FROM, 30)
        val to = p.getInt(KEY_INTERP_TO, 120)
        val preset = INTERPOLATION_PRESETS.firstOrNull { it.fromFps == from && it.toFps == to }
            ?: INTERPOLATION_PRESETS.first()
        return FrameInterpolationValue(fromFps = preset.fromFps, toFps = preset.toFps)
    }

    fun setInterpolation(preset: InterpolationPreset) {
        prefsRef?.edit()
            ?.putInt(KEY_INTERP_FROM, preset.fromFps)
            ?.putInt(KEY_INTERP_TO, preset.toFps)
            ?.apply()
    }

    fun refreshTarget(): Int {
        val v = prefsRef?.getInt(KEY_REFRESH, 144) ?: 144
        return if (v in REFRESH_TARGETS) v else REFRESH_TARGETS.last()
    }

    fun setRefreshTarget(hz: Int) {
        if (hz !in REFRESH_TARGETS) return
        prefsRef?.edit()?.putInt(KEY_REFRESH, hz)?.apply()
    }

    fun guardEnabled(): Boolean = prefsRef?.getBoolean(KEY_GUARD, false) ?: false

    fun setGuardEnabled(value: Boolean) {
        prefsRef?.edit()?.putBoolean(KEY_GUARD, value)?.apply()
    }

    /* ---------------- 原始值备份 ---------------- */

    fun hasBackup(key: ToolboxKey): Boolean =
        prefsRef?.contains(PREFIX_BACKUP + readKeyId(key)) ?: false

    fun backupOriginal(key: ToolboxKey, value: String?) {
        if (hasBackup(key)) return
        prefsRef?.edit()?.putString(PREFIX_BACKUP + readKeyId(key), value ?: ABSENT)?.apply()
    }

    /** 返回 (是否原本存在, 原值)；没有备份返回 null。 */
    fun originalValue(key: ToolboxKey): Pair<Boolean, String?>? {
        val raw = prefsRef?.getString(PREFIX_BACKUP + readKeyId(key), null) ?: return null
        return if (raw == ABSENT) false to null else true to raw
    }

    fun clearBackups(feature: ToolboxFeature) {
        val edit = prefsRef?.edit() ?: return
        keysOf(feature).forEach { edit.remove(PREFIX_BACKUP + readKeyId(it)) }
        edit.apply()
    }
}
