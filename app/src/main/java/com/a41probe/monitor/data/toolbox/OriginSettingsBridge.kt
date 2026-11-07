package com.a41probe.monitor.data.toolbox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.a41probe.monitor.data.RootBridge
import com.a41probe.monitor.data.ShizukuBridge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 写入通道（按优先级）。 */
enum class ToolboxWriteChannel(val label: String) {
    DIRECT("ADB 授权直写"),
    ROOT("Root"),
    SHIZUKU("Shizuku"),
    NONE("无写入权限"),
}

data class ToolboxChannelStatus(
    val direct: Boolean,
    val root: Boolean,
    val shizuku: Boolean,
) {
    val preferred: ToolboxWriteChannel = when {
        direct -> ToolboxWriteChannel.DIRECT
        root -> ToolboxWriteChannel.ROOT
        shizuku -> ToolboxWriteChannel.SHIZUKU
        else -> ToolboxWriteChannel.NONE
    }

    val available: Boolean get() = preferred != ToolboxWriteChannel.NONE
}

data class WriteOutcome(
    val ok: Boolean,
    val channel: ToolboxWriteChannel,
    val error: String? = null,
)

/**
 * 系统设置读写桥（v3.5）。
 *
 * 读取：Settings API（免权限）→ Root/Shizuku `settings get` 兜底。
 * 写入：ADB 授权直写 > Root > Shizuku shell（`settings delete` + `content insert`）。
 * 所有写入后都回读校验；vivo 的 `settings put` 静默丢弃问题由此暴露。
 */
object OriginSettingsBridge {

    private val writeMutex = Mutex()

    fun channelStatus(ctx: Context): ToolboxChannelStatus {
        val direct = ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        return ToolboxChannelStatus(
            direct = direct,
            root = RootBridge.available,
            shizuku = ShizukuBridge.authorized,
        )
    }

    /** 免权限读取；失败/为空时用特权通道兜底。 */
    fun read(ctx: Context, key: ToolboxKey): String? {
        val direct = readDirect(ctx, key)
        if (direct != null) return direct
        return readPrivileged(key)
    }

    fun readDirect(ctx: Context, key: ToolboxKey): String? = try {
        when (key.namespace) {
            ToolboxNamespace.GLOBAL -> Settings.Global.getString(ctx.contentResolver, key.name)
            ToolboxNamespace.SYSTEM -> Settings.System.getString(ctx.contentResolver, key.name)
        }
    } catch (_: Throwable) {
        null
    }

    private fun readPrivileged(key: ToolboxKey): String? {
        val cmd = "settings get ${key.namespace.ns} ${key.name}"
        val out = when {
            RootBridge.available -> RootBridge.exec(cmd)
            ShizukuBridge.authorized -> ShizukuBridge.exec(cmd)
            else -> null
        }
        return parseSettingsGet(out)
    }

    /** 当前功能状态值（按 feature 的所有 key 读一遍，key 为 namespace.key）。 */
    fun readValues(ctx: Context, feature: ToolboxFeature): Map<String, String?> =
        keysOf(feature).associate { readKeyId(it) to read(ctx, it) }

    /**
     * 写入功能。
     * @param values keys → 目标值；为 null 表示删除该 key。
     * @return 写入结果（已回读校验）。
     */
    suspend fun writeFeature(
        ctx: Context,
        feature: ToolboxFeature,
        values: Map<ToolboxKey, String?>,
    ): WriteOutcome = writeMutex.withLock {
        val channel = channelStatus(ctx).preferred
        if (channel == ToolboxWriteChannel.NONE) {
            return@withLock WriteOutcome(false, channel, "需要 Shizuku / Root（system 类设置无法用 App 的 ADB 直写）")
        }
        // 写前备份原始值（仅第一次）
        values.keys.forEach { key ->
            if (!ToolboxConfig.hasBackup(key)) {
                ToolboxConfig.backupOriginal(key, read(ctx, key))
            }
        }
        // 按可用通道依次尝试；vivo 的直写在 system 命名空间可能只删不写，
        // 因此必须回读校验，失败自动降级到 Root/Shizuku shell 通道。
        val status = channelStatus(ctx)
        val candidates = buildList {
            add(ToolboxWriteChannel.DIRECT)
            if (status.root) add(ToolboxWriteChannel.ROOT)
            if (status.shizuku) add(ToolboxWriteChannel.SHIZUKU)
        }.distinct()
        var lastError: String? = null
        var lastChannel = channel
        for (candidate in candidates) {
            lastChannel = candidate
            val ok = when (candidate) {
                ToolboxWriteChannel.DIRECT -> writeDirect(ctx, values)
                ToolboxWriteChannel.ROOT, ToolboxWriteChannel.SHIZUKU ->
                    writeShell(ctx, candidate, values)
                ToolboxWriteChannel.NONE -> false
            }
            if (!ok) {
                lastError = "写入命令执行失败（无权限或超时）"
                continue
            }
            val mismatch = values.entries.firstOrNull { (key, expected) ->
                val actual = read(ctx, key)
                if (expected == null) actual != null else actual != expected
            }
            if (mismatch == null) {
                return@withLock WriteOutcome(true, candidate)
            }
            lastError = "写入未生效（系统可能拦截）：${mismatch.key.name}"
        }
        WriteOutcome(false, lastChannel, lastError ?: "写入失败")
    }

    private fun writeDirect(ctx: Context, values: Map<ToolboxKey, String?>): Boolean = try {
        values.forEach { (key, value) ->
            when (key.namespace) {
                ToolboxNamespace.GLOBAL ->
                    Settings.Global.putString(ctx.contentResolver, key.name, value)
                ToolboxNamespace.SYSTEM ->
                    Settings.System.putString(ctx.contentResolver, key.name, value)
            }
        }
        true
    } catch (_: Throwable) {
        false
    }

    private fun writeShell(
        ctx: Context,
        channel: ToolboxWriteChannel,
        values: Map<ToolboxKey, String?>,
    ): Boolean {
        val exec: (String) -> String? = when (channel) {
            // v3.14: Root 写路径包含 app_process 冷启动，给 8s 超时（读路径仍 2.5s）
            ToolboxWriteChannel.ROOT -> { cmd -> RootBridge.exec(cmd, timeoutMs = 8_000L) }
            ToolboxWriteChannel.SHIZUKU -> { cmd -> ShizukuBridge.exec(cmd) }
            else -> return false
        }
        // 1) 删除：`settings delete`（真机验证可用；app_process 只负责写）
        val deletes = values.filterValues { it == null }.keys.map { deleteScript(it) }
        if (deletes.isNotEmpty() && exec(deletes.joinToString(" ; ")) == null) return false
        // 2) 写入：app_process 以 shell 身份直调 SettingsProvider，
        //    可写入 content insert 不支持的含冒号值（插帧），同时绕开 vivo 的 settings put 丢弃。
        val apk = ctx.applicationInfo.sourceDir
        val writes = values.filterValues { it != null }.entries
            .groupBy { it.key.namespace }
        for ((namespace, entries) in writes) {
            val cmd = buildString {
                append("app_process -Djava.class.path=").append(apk)
                append(" /system/bin com.a41probe.monitor.data.toolbox.ToolboxShellWriter ")
                append(namespace.ns)
                entries.forEach { (key, value) ->
                    append(' ').append(key.name).append(' ').append(shellQuote(value!!))
                }
            }
            if (exec(cmd) == null) return false
        }
        return true
    }

    /** 调校值只含字母数字和 ._-: ，不需要引号；异常字符用单引号包裹。 */
    private fun shellQuote(value: String): String =
        if (value.all { it.isLetterOrDigit() || it in "._:-" }) {
            value
        } else {
            "'" + value.replace("'", "'\\''") + "'"
        }

    /** 供界面显示的 ADB 授权命令（复制到剪贴板用）。 */
    fun adbGrantCommand(pkg: String): String =
        "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
}
