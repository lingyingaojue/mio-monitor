package com.a41probe.monitor.data.toolbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后若守护开启且仍有启用项，重新拉起守护服务（v3.5）。 */
class ToolboxBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ToolboxConfig.ensureInit(context)
        if (ToolboxConfig.guardEnabled() && ToolboxConfig.enabledFeatures().isNotEmpty()) {
            ToolboxGuardService.start(context)
        }
    }
}
