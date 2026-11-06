package com.a41probe.monitor.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.a41probe.monitor.data.toolbox.FrameInterpolationValue
import com.a41probe.monitor.data.toolbox.OriginSettingsBridge
import com.a41probe.monitor.data.toolbox.ToolboxConfig
import com.a41probe.monitor.data.toolbox.ToolboxFeature
import com.a41probe.monitor.data.toolbox.ToolboxGuardService
import com.a41probe.monitor.data.toolbox.keysOf
import com.a41probe.monitor.data.toolbox.valuesToWrite
import kotlinx.coroutines.runBlocking

/**
 * 【仅 debug 包】OriginOS 调校的 ADB 自动化测试钩子（不参与 release 构建）。
 *
 * 用法示例：
 *   adb shell am broadcast -a com.a41probe.monitor.DEBUG_TOOLBOX \
 *     -n com.a41probe.monitor/.debug.ToolboxDebugReceiver \
 *     --es action enable --es feature touch_boost
 *
 * action：enable / disable / restore / guard / setinterp / setrefresh
 * feature：touch_boost / frame_interpolation / refresh_lock / visual_enhance
 */
class ToolboxDebugReceiver : BroadcastReceiver() {

    private val tag = "ToolboxDebug"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        ToolboxConfig.ensureInit(context)
        val pending = goAsync()
        Thread {
            try {
                val result = handle(context, intent)
                Log.i(tag, "result=$result")
            } catch (t: Throwable) {
                Log.e(tag, "failed", t)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(context: Context, intent: Intent): String {
        val action = intent.getStringExtra("action") ?: return "no action"
        return when (action) {
            "enable", "disable" -> {
                val feature = featureOf(intent.getStringExtra("feature")) ?: return "bad feature"
                val enable = action == "enable"
                val values = if (enable) {
                    valuesToWrite(
                        feature,
                        ToolboxConfig.interpolation(),
                        ToolboxConfig.refreshTarget(),
                    )
                } else {
                    keysOf(feature).associateWith { null }
                }
                val outcome = runBlocking { OriginSettingsBridge.writeFeature(context, feature, values) }
                if (outcome.ok) ToolboxConfig.setFeatureEnabled(feature, enable)
                "$action ${feature.id} ok=${outcome.ok} channel=${outcome.channel} err=${outcome.error}"
            }
            "restore" -> {
                val toOriginal = intent.getBooleanExtra("original", false)
                var errors = 0
                ToolboxFeature.entries.forEach { feature ->
                    val values = if (toOriginal) {
                        keysOf(feature).associateWith { key ->
                            val original = ToolboxConfig.originalValue(key)
                            if (original != null && original.first) original.second else null
                        }
                    } else {
                        keysOf(feature).associateWith { null }
                    }
                    val outcome = runBlocking { OriginSettingsBridge.writeFeature(context, feature, values) }
                    if (outcome.ok) ToolboxConfig.setFeatureEnabled(feature, false) else errors++
                }
                ToolboxGuardService.stop(context)
                "restore original=$toOriginal errors=$errors"
            }
            "setinterp" -> {
                val from = intent.getIntExtra("from", 30)
                val to = intent.getIntExtra("to", 120)
                ToolboxConfig.setInterpolation(
                    com.a41probe.monitor.data.toolbox.InterpolationPreset(from, to),
                )
                val feature = ToolboxFeature.FRAME_INTERPOLATION
                if (ToolboxConfig.featureEnabled(feature)) {
                    val values = valuesToWrite(
                        feature,
                        FrameInterpolationValue(from, to),
                        ToolboxConfig.refreshTarget(),
                    )
                    val outcome = runBlocking { OriginSettingsBridge.writeFeature(context, feature, values) }
                    "setinterp ok=${outcome.ok}"
                } else {
                    "setinterp saved (feature off)"
                }
            }
            "setrefresh" -> {
                val hz = intent.getIntExtra("hz", 144)
                ToolboxConfig.setRefreshTarget(hz)
                "setrefresh saved"
            }
            "guard" -> {
                val on = intent.getBooleanExtra("on", false)
                ToolboxConfig.setGuardEnabled(on)
                if (on) ToolboxGuardService.start(context) else ToolboxGuardService.stop(context)
                "guard=$on"
            }
            "directtest" -> {
                val key = intent.getStringExtra("key") ?: "mio_direct_test"
                val value = intent.getStringExtra("value") ?: "abc"
                val cr = context.contentResolver
                val r = try {
                    Settings.System.putString(cr, key, value)
                    "ok"
                } catch (t: Throwable) {
                    "ex:" + t.message
                }
                val read = runCatching { Settings.System.getString(cr, key) }.getOrNull()
                "directtest key=$key put=$r read=$read"
            }
            "state" -> {
                val sb = StringBuilder()
                ToolboxFeature.entries.forEach { feature ->
                    val values = OriginSettingsBridge.readValues(context, feature)
                    sb.append(feature.id).append('=').append(values.values.joinToString("/")).append(';')
                }
                sb.toString()
            }
            else -> "unknown action"
        }
    }

    private fun featureOf(id: String?): ToolboxFeature? =
        ToolboxFeature.entries.firstOrNull { it.id == id }

    companion object {
        const val ACTION = "com.a41probe.monitor.DEBUG_TOOLBOX"
    }
}
