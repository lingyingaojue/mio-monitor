package com.a41probe.monitor.data.toolbox

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * OriginOS 调校控制器（v3.5）：读取/写入/守护的唯一入口。
 * UI 订阅 [state]；所有写操作走 [OriginSettingsBridge]（自动选择写入通道 + 回读校验）。
 */
object ToolboxController {

    data class FeatureUi(
        val feature: ToolboxFeature,
        val desired: Boolean,
        val on: Boolean,
        val state: ToolboxFeatureState,
        val rawLine: String,
        val busy: Boolean = false,
        val error: String? = null,
    )

    data class UiState(
        val supported: Boolean = true,
        val channel: ToolboxChannelStatus = ToolboxChannelStatus(false, false, false),
        val features: List<FeatureUi> = ToolboxFeature.entries.map {
            FeatureUi(it, desired = false, on = false, state = ToolboxFeatureState.UNKNOWN, rawLine = "读取中…")
        },
        val currentRefreshHz: Float? = null,
        val interpolation: FrameInterpolationValue = FrameInterpolationValue(30, 120),
        val refreshTarget: Int = 144,
        val refreshTargets: List<Int> = REFRESH_TARGETS,
        val guardEnabled: Boolean = false,
        val acknowledged: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null,
        val lastRefreshAt: Long = 0L,
        val backupsExist: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /* ---------------- 读取 ---------------- */

    fun refresh(ctx: Context) {
        scope.launch { refreshNow(ctx) }
    }

    /** 仅刷新"设备与权限"卡片（机型/系统/实时刷新率/写入通道/高刷锁定外的通道状态），供页面每秒轮询。 */
    fun refreshDeviceInfo(ctx: Context) {
        scope.launch {
            val supported = isOriginTuningSupported()
            val channel = OriginSettingsBridge.channelStatus(ctx)
            _state.value = _state.value.copy(
                supported = supported,
                channel = channel,
                currentRefreshHz = currentRefreshHz(ctx),
                acknowledged = ToolboxConfig.acknowledged(),
            )
        }
    }

    private suspend fun refreshNow(ctx: Context, clearMessage: Boolean = true) {
        val supported = isOriginTuningSupported()
        val channel = OriginSettingsBridge.channelStatus(ctx)
        val features = ToolboxFeature.entries.map { feature ->
            val values = if (supported) OriginSettingsBridge.readValues(ctx, feature) else emptyMap()
            buildFeatureUi(ctx, feature, values, busy = false, error = null)
        }
        _state.value = _state.value.copy(
            supported = supported,
            channel = channel,
            features = features,
            currentRefreshHz = currentRefreshHz(ctx),
            interpolation = ToolboxConfig.interpolation(),
            refreshTarget = ToolboxConfig.refreshTarget(),
            guardEnabled = ToolboxConfig.guardEnabled(),
            acknowledged = ToolboxConfig.acknowledged(),
            busy = false,
            message = if (clearMessage) null else _state.value.message,
            lastRefreshAt = System.currentTimeMillis(),
            backupsExist = ToolboxFeature.entries.any { f -> keysOf(f).any { ToolboxConfig.hasBackup(it) } },
        )
    }

    private fun buildFeatureUi(
        ctx: Context,
        feature: ToolboxFeature,
        values: Map<String, String?>,
        busy: Boolean,
        error: String?,
    ): FeatureUi {
        val on = resolveFeatureOn(feature, values)
        val state = when {
            values.isEmpty() -> ToolboxFeatureState.UNKNOWN
            on -> ToolboxFeatureState.ON
            values.values.all { it == null } -> ToolboxFeatureState.ABSENT
            else -> ToolboxFeatureState.OFF
        }
        return FeatureUi(
            feature = feature,
            desired = ToolboxConfig.featureEnabled(feature),
            on = on,
            state = state,
            rawLine = rawSummary(feature, values),
            busy = busy,
            error = error,
        )
    }

    private fun rawSummary(feature: ToolboxFeature, values: Map<String, String?>): String = when (feature) {
        ToolboxFeature.TOUCH_BOOST -> {
            val v = values["global.game_memc_request_touch_rate"]
            "当前值：${v ?: "未设置（默认）"}"
        }
        ToolboxFeature.FRAME_INTERPOLATION -> {
            val raw = values["system.gamecube_frame_interpolation"]
            val parsed = FrameInterpolationValue.parse(raw)
            when {
                raw == null -> "当前值：未设置（关闭）"
                parsed == null -> "当前值：$raw"
                parsed.enabled -> "当前值：${parsed.fromFps}→${parsed.toFps} 帧"
                else -> "当前值：关闭（$raw）"
            }
        }
        ToolboxFeature.REFRESH_LOCK -> {
            val min = values["system.min_refresh_rate"]
            val peak = values["system.peak_refresh_rate"]
            "min=${min ?: "默认"} / peak=${peak ?: "默认"}"
        }
        ToolboxFeature.VISUAL_ENHANCE -> {
            val v = values["system.gamecube_hawkeye_effective"]
            "当前值：${v ?: "未设置（默认）"}"
        }
    }

    /* ---------------- 写操作 ---------------- */

    fun setFeature(ctx: Context, feature: ToolboxFeature, enable: Boolean) {
        scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val values: Map<ToolboxKey, String?> = if (enable) {
                valuesToWrite(feature, ToolboxConfig.interpolation(), ToolboxConfig.refreshTarget())
            } else {
                keysOf(feature).associateWith { null }
            }
            val outcome = OriginSettingsBridge.writeFeature(ctx, feature, values)
            if (outcome.ok) {
                ToolboxConfig.setFeatureEnabled(feature, enable)
            } else {
                ToolboxConfig.setFeatureEnabled(feature, false)
            }
            refreshNow(ctx)
            _state.value = _state.value.copy(
                busy = false,
                message = if (outcome.ok) {
                    "${feature.title}已${if (enable) "开启" else "关闭"}"
                } else {
                    "失败：${outcome.error ?: "未知错误"}"
                },
            )
            syncGuardService(ctx)
            scheduleQuietRefresh(ctx)
        }
    }

    fun setInterpolation(ctx: Context, preset: InterpolationPreset) {
        scope.launch {
            ToolboxConfig.setInterpolation(preset)
            val feature = ToolboxFeature.FRAME_INTERPOLATION
            if (ToolboxConfig.featureEnabled(feature)) {
                setFeatureInternal(ctx, feature, enable = true, successPrefix = "插帧预设已更新为 ${preset.label}")
            } else {
                refreshNow(ctx)
                _state.value = _state.value.copy(message = "插帧预设已保存：${preset.label}（开启插帧后生效）")
            }
        }
    }

    fun setRefreshTarget(ctx: Context, hz: Int) {
        scope.launch {
            ToolboxConfig.setRefreshTarget(hz)
            val feature = ToolboxFeature.REFRESH_LOCK
            if (ToolboxConfig.featureEnabled(feature)) {
                setFeatureInternal(ctx, feature, enable = true, successPrefix = "刷新率目标已更新为 ${hz}Hz")
            } else {
                refreshNow(ctx)
                _state.value = _state.value.copy(message = "刷新率目标已保存：${hz}Hz（开启锁定后生效）")
            }
        }
    }

    private suspend fun setFeatureInternal(
        ctx: Context,
        feature: ToolboxFeature,
        enable: Boolean,
        successPrefix: String,
    ) {
        _state.value = _state.value.copy(busy = true, message = null)
        val values = if (enable) {
            valuesToWrite(feature, ToolboxConfig.interpolation(), ToolboxConfig.refreshTarget())
        } else {
            keysOf(feature).associateWith { null }
        }
        val outcome = OriginSettingsBridge.writeFeature(ctx, feature, values)
        refreshNow(ctx)
        _state.value = _state.value.copy(
            busy = false,
            message = if (outcome.ok) successPrefix else "失败：${outcome.error ?: "未知错误"}",
        )
    }

    /** 全部恢复默认（删除 key = 系统默认）；[toOriginal] 时优先恢复首次写入前备份的原始值。 */
    fun restoreAll(ctx: Context, toOriginal: Boolean) {
        scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            var ok = true
            var lastError: String? = null
            ToolboxFeature.entries.forEach { feature ->
                val values: Map<ToolboxKey, String?> = if (toOriginal) {
                    keysOf(feature).associateWith { key ->
                        val original = ToolboxConfig.originalValue(key)
                        when {
                            original == null -> null
                            original.first -> original.second
                            else -> null
                        }
                    }
                } else {
                    keysOf(feature).associateWith { null }
                }
                val outcome = OriginSettingsBridge.writeFeature(ctx, feature, values)
                if (!outcome.ok) {
                    ok = false
                    lastError = outcome.error
                }
                if (ok) {
                    ToolboxConfig.setFeatureEnabled(feature, false)
                    if (!toOriginal) ToolboxConfig.clearBackups(feature)
                }
            }
            refreshNow(ctx)
            _state.value = _state.value.copy(
                busy = false,
                message = if (ok) {
                    if (toOriginal) "已恢复首次写入前的原始值" else "已全部恢复系统默认"
                } else {
                    "恢复失败：${lastError ?: "未知错误"}"
                },
            )
            syncGuardService(ctx)
            scheduleQuietRefresh(ctx)
        }
    }

    fun setGuard(ctx: Context, enabled: Boolean) {
        ToolboxConfig.setGuardEnabled(enabled)
        _state.value = _state.value.copy(guardEnabled = enabled)
        syncGuardService(ctx)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** 应用回前台/开机/守护轮询时调用：对"应生效"的功能检查并重写被系统重置的 key。 */
    fun reapplyIfNeeded(ctx: Context) {
        if (ToolboxConfig.enabledFeatures().isEmpty()) return
        scope.launch { applyEnabledFeatures(ctx, notify = false) }
    }

    fun reapplyNow(ctx: Context) {
        scope.launch {
            applyEnabledFeatures(ctx, notify = true)
            _state.value = _state.value.copy(message = "已检查并重写被系统重置的项")
        }
    }

    suspend fun applyEnabledFeatures(ctx: Context, notify: Boolean) {
        val channel = OriginSettingsBridge.channelStatus(ctx)
        if (!channel.available) return
        ToolboxConfig.enabledFeatures().forEach { feature ->
            val values = OriginSettingsBridge.readValues(ctx, feature)
            if (!resolveFeatureOn(feature, values)) {
                val target = valuesToWrite(
                    feature,
                    ToolboxConfig.interpolation(),
                    ToolboxConfig.refreshTarget(),
                )
                OriginSettingsBridge.writeFeature(ctx, feature, target)
            }
        }
        if (notify) refreshNow(ctx)
    }

    /** 系统应用设置有延迟（尤其刷新率切换），写成功后 800ms 再静默刷新一次，保留提示消息。 */
    private fun scheduleQuietRefresh(ctx: Context) {
        scope.launch {
            delay(800)
            refreshNow(ctx, clearMessage = false)
        }
    }

    private fun syncGuardService(ctx: Context) {
        val shouldRun = ToolboxConfig.guardEnabled() && ToolboxConfig.enabledFeatures().isNotEmpty()
        if (shouldRun) ToolboxGuardService.start(ctx) else ToolboxGuardService.stop(ctx)
    }

    /* ---------------- 设备/显示信息 ---------------- */

    fun currentRefreshHz(ctx: Context): Float? = try {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate
    } catch (_: Throwable) {
        null
    }
}
