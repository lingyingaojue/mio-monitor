package com.a41probe.monitor.data.toolbox

import android.content.ContentValues
import android.net.Uri
import android.os.IBinder

/**
 * 【Shizuku / Root shell 通道的直写器】（v3.5 真机适配）
 *
 * 背景（iQOO Neo10 Pro+ / Android 17 实测）：
 *  - vivo 的 `settings put` 会静默丢弃（exit=0）；
 *  - Android 17 的 `content insert --bind` 不接受含冒号的值（插帧 `1:1:0:30:120` 报 Binding not well formed）；
 *  - App 进程只有 WRITE_SECURE_SETTINGS，直写 system 命名空间会被 SettingsProvider 拒绝
 *    （"You cannot keep your settings in the secure settings."）。
 *
 * 而 shell uid 持有 WRITE_SECURE_SETTINGS。用 `app_process` 以 shell 身份运行本类，
 * 通过 `IContentProvider.insert()` 直接写 SettingsProvider，即可绕过上述三处限制。
 *
 * 用法：app_process -Djava.class.path=<base.apk> /system/bin \
 *   com.a41probe.monitor.data.toolbox.ToolboxShellWriter <namespace> <key> <value> [<key> <value> ...]
 *
 * 仅由本 App 的 Shizuku/Root 通道调用，参数只来自调校功能的固定白名单。
 */
object ToolboxShellWriter {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3 || args.size % 2 == 0) {
            System.err.println("usage: <namespace> <key> <value> [<key> <value> ...]")
            return
        }
        val namespace = args[0]
        try {
            val provider = obtainProvider("settings")
            val uri = Uri.parse("content://settings/$namespace")
            var written = 0
            var i = 1
            while (i + 1 < args.size) {
                val key = args[i]
                val value = args[i + 1]
                i += 2
                if (value == "__DELETE__") continue
                val values = ContentValues().apply {
                    put("name", key)
                    put("value", value)
                }
                invokeContentProvider(
                    provider, "insert",
                    arrayOf(String::class.java, Uri::class.java, ContentValues::class.java),
                    // 以 shell uid 运行，包名必须匹配调用 uid，否则 SettingsProvider 会抛
                    // "Package com.a41probe.monitor does not belong to 2000"
                    "com.android.shell", uri, values,
                )
                written++
            }
            println("OK written=$written")
        } catch (t: Throwable) {
            t.printStackTrace()
            System.err.println("FAILED: ${t.message}")
            kotlin.system.exitProcess(2)
        }
    }

    private fun obtainProvider(authority: String): Any {
        val activity = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "activity") as IBinder
        val iAm = Class.forName("android.app.IActivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, activity)!!
        val holder = iAm.javaClass
            .getMethod(
                "getContentProviderExternal",
                String::class.java, Int::class.javaPrimitiveType, IBinder::class.java, String::class.java,
            )
            .invoke(iAm, authority, 0, null, null)!!
        return holder.javaClass.getField("provider").get(holder)!!
    }

    private fun invokeContentProvider(
        provider: Any,
        method: String,
        signature: Array<Class<*>>,
        vararg args: Any?,
    ): Any? = Class.forName("android.content.IContentProvider")
        .getMethod(method, *signature)
        .invoke(provider, *args)
}
