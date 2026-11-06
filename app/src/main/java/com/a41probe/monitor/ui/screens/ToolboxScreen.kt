package com.a41probe.monitor.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.a41probe.monitor.data.ShizukuBridge
import com.a41probe.monitor.data.toolbox.INTERPOLATION_PRESETS
import com.a41probe.monitor.data.toolbox.OriginSettingsBridge
import com.a41probe.monitor.data.toolbox.ToolboxConfig
import com.a41probe.monitor.data.toolbox.ToolboxController
import com.a41probe.monitor.data.toolbox.ToolboxFeature
import com.a41probe.monitor.data.toolbox.ToolboxFeatureState
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioChoiceRow
import com.a41probe.monitor.ui.components.MioInfoRow
import com.a41probe.monitor.ui.components.MioPageScaffold
import com.a41probe.monitor.ui.components.MioPreferenceGroup
import com.a41probe.monitor.ui.components.MioSectionHeader
import com.a41probe.monitor.ui.components.MioSwitchRow
import com.a41probe.monitor.ui.components.ProbeButton
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.glass.LocalMioChromeInsets
import com.a41probe.monitor.ui.glass.scaledGlassFillAlpha
import com.a41probe.monitor.ui.theme.Ink

/**
 * OriginOS 实验调校页（v3.5）：
 * 把 ADB 实测可用的 4 项系统设置做成开关；默认全关、首次进入需确认风险。
 * 写入通道自动选择（ADB 直写 / Root / Shizuku），每次都回读校验。
 */
@Composable
fun ToolboxScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val state by ToolboxController.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    var showAck by remember { mutableStateOf(false) }
    var pendingToggle by remember { mutableStateOf<ToolboxFeature?>(null) }
    var showInterpDialog by remember { mutableStateOf(false) }
    var showRefreshDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreToOriginal by remember { mutableStateOf(false) }
    // 取消"开启"确认时重建开关，消除 Miuix 液态开关先动画、后回滚导致的视觉残留
    var switchResetTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        ToolboxConfig.ensureInit(ctx)
        ToolboxController.refresh(ctx)
        if (!ToolboxConfig.acknowledged()) showAck = true
    }
    // 从 Shizuku 授权页 / 系统设置返回时刷新设备与权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    val resumeScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ToolboxController.refresh(ctx)
                // MainActivity 的 Shizuku 探测在 ON_START 触发，稍后再读一次避免读到旧状态
                resumeScope.launch {
                    delay(700)
                    ToolboxController.refresh(ctx)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 设备与权限卡片每秒刷新（实时刷新率会随系统调度变化）
    LaunchedEffect(Unit) {
        while (true) {
            ToolboxController.refreshDeviceInfo(ctx)
            delay(1_000)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            ToolboxController.clearMessage()
        }
    }

    val supported = state.supported
    val writable = supported && state.channel.available
    // system 命名空间的写入无法用 App 的 ADB 直写权限完成，必须走 Shizuku / Root。
    val systemWritable = state.channel.root || state.channel.shizuku

    MioPageScaffold {
        val chrome = LocalMioChromeInsets.current
        ScrollAware(listState) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 2.dp + chrome.top,
                    bottom = 20.dp + chrome.bottom,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    MioSectionHeader("设备与权限")
                    MioPreferenceGroup {
                        MioInfoRow("机型", "${Build.MANUFACTURER} ${Build.MODEL}")
                        MioInfoRow("系统", "Android ${Build.VERSION.RELEASE} · ${Build.DISPLAY}")
                        MioInfoRow(
                            "实时刷新率",
                            state.currentRefreshHz?.let { "%.0f Hz（系统动态调度）".format(it) } ?: "未知",
                        )

                        MioInfoRow(
                            "写入通道",
                            if (state.channel.available) state.channel.preferred.label else "无（只读）",
                        )
                        MioInfoRow(
                            "system 设置",
                            if (state.channel.root || state.channel.shizuku) "Shizuku / Root 可用" else "不可用（仅触控可写）",
                        )
                        if (!state.channel.shizuku) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Text(
                                    "触控拉满可走 ADB 授权直写；插帧 / 高刷锁定 / 视觉增强属 system 设置，" +
                                        "Android 17 上 App 直写会被系统拒绝，需要授权 Shizuku 或 Root。",
                                    color = Ink.tx2, fontSize = 12.sp, lineHeight = 17.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                val adbCmd = OriginSettingsBridge.adbGrantCommand(ctx.packageName)
                                ProbeButton(
                                    text = "复制 ADB 授权命令",
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        clipboard.setText(AnnotatedString(adbCmd))
                                        Toast.makeText(ctx, "已复制：$adbCmd", Toast.LENGTH_LONG).show()
                                    },
                                )
                                Spacer(Modifier.height(6.dp))
                                ProbeButton(
                                    text = "请求 Shizuku 授权",
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        ShizukuBridge.refresh()
                                        ShizukuBridge.requestPermission()
                                        ToolboxController.refresh(ctx)
                                    },
                                )
                            }
                        }
                    }
                }

                if (!supported) {
                    item {
                        MioSectionHeader("实验功能")
                        MioCard {
                            Text(
                                "当前设备不是 vivo / iQOO OriginOS，这些功能未验证，已保持只读。\n" +
                                    "调校页仅在 OriginOS 设备上开放写入。",
                                color = Ink.tx2, fontSize = 13.sp, lineHeight = 19.sp,
                            )
                        }
                    }
                }
                if (supported) {
                    item {
                        MioSectionHeader("实验功能（默认关闭）")
                    }
                ToolboxFeature.entries.forEach { feature ->
                    item {
                        FeatureGroup(
                            state = state,
                            feature = feature,
                            enabled = writable && !state.busy &&
                                (feature == ToolboxFeature.TOUCH_BOOST || systemWritable),
                            onToggle = { on ->
                                if (on) pendingToggle = feature
                                else ToolboxController.setFeature(ctx, feature, false)
                            },
                            onPickInterpolation = { showInterpDialog = true },
                            onPickRefresh = { showRefreshDialog = true },
                            resetTick = switchResetTick,
                        )
                    }
                }

                }

                item {
                    MioSectionHeader("功能守护")
                    MioPreferenceGroup {
                        MioSwitchRow(
                            title = "保持生效（前台守护）",
                            checked = state.guardEnabled,
                            enabled = supported && state.channel.available,
                            subtitle = "OriginOS 会定期清理这些 key。开启后前台服务每 5 秒检查并重写；" +
                                "可能被系统省电策略杀掉，打开 App 时会自动恢复。",
                            onCheckedChange = { ToolboxController.setGuard(ctx, it) },
                        )
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                            ProbeButton(
                                text = "立即检查并重写",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { ToolboxController.reapplyNow(ctx) },
                            )
                        }
                    }
                }

                item {
                    MioSectionHeader("恢复")
                    MioPreferenceGroup {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            ProbeButton(
                                text = "全部恢复系统默认",
                                accent = true,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    restoreToOriginal = false
                                    showRestoreDialog = true
                                },
                            )
                            if (state.backupsExist) {
                                Spacer(Modifier.height(8.dp))
                                ProbeButton(
                                    text = "恢复首次写入前的原始值",
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        restoreToOriginal = true
                                        showRestoreDialog = true
                                    },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "卸载 App 前请先全部恢复默认，否则写入的 key 可能残留。" +
                                    "恢复默认 = 删除对应 key，由系统回到自身策略。",
                                color = Ink.off, fontSize = 11.5.sp, lineHeight = 16.sp,
                            )
                        }
                    }
                }

            }
        }
    }

    if (showAck) {
        GlassDialog(
            title = "实验功能说明",
            confirmText = "我已知晓，继续",
            onConfirm = {
                ToolboxConfig.setAcknowledged(true)
                showAck = false
            },
            onDismiss = {
                showAck = false
                onBack()
            },
        ) {
            Text(
                "此页功能会写入系统设置（global/system），修改触控采样、插帧、刷新率与画面增强。\n\n" +
                    "· 默认全部关闭，只有你手动开启才写入；\n" +
                    "· 每项均可关闭或一键恢复默认；\n" +
                    "· 插帧可能降低原生分辨率，锁高刷会增加耗电；\n" +
                    "· 仅在本机生效，不会同步或隐藏写入；\n" +
                    "· 卸载前请先恢复默认。",
                color = Ink.tx2, fontSize = 13.sp, lineHeight = 20.sp,
            )
        }
    }

    pendingToggle?.let { feature ->
        GlassDialog(
            title = "开启「${feature.title}」？",
            confirmText = "开启",
            onConfirm = {
                pendingToggle = null
                ToolboxController.setFeature(ctx, feature, true)
            },
            onDismiss = {
                pendingToggle = null
                switchResetTick++
            },
        ) {
            Text(feature.risk, color = Ink.tx2, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }

    if (showInterpDialog) {
        GlassDialog(
            title = "插帧预设",
            confirmText = "关闭",
            onConfirm = { showInterpDialog = false },
            onDismiss = { showInterpDialog = false },
        ) {
            Column {
                INTERPOLATION_PRESETS.forEach { preset ->
                    PresetRow(
                        label = preset.label,
                        selected = state.interpolation.fromFps == preset.fromFps &&
                            state.interpolation.toFps == preset.toFps,
                        onClick = {
                            showInterpDialog = false
                            ToolboxController.setInterpolation(ctx, preset)
                        },
                    )
                }
            }
        }
    }

    if (showRefreshDialog) {
        GlassDialog(
            title = "刷新率目标",
            confirmText = "关闭",
            onConfirm = { showRefreshDialog = false },
            onDismiss = { showRefreshDialog = false },
        ) {
            Column {
                state.refreshTargets.forEach { hz ->
                    PresetRow(
                        label = "$hz Hz",
                        selected = state.refreshTarget == hz,
                        onClick = {
                            showRefreshDialog = false
                            ToolboxController.setRefreshTarget(ctx, hz)
                        },
                    )
                }
            }
        }
    }

    if (showRestoreDialog) {
        GlassDialog(
            title = if (restoreToOriginal) "恢复原始值？" else "恢复系统默认？",
            confirmText = "确认恢复",
            onConfirm = {
                showRestoreDialog = false
                ToolboxController.restoreAll(ctx, toOriginal = restoreToOriginal)
            },
            onDismiss = { showRestoreDialog = false },
        ) {
            Text(
                if (restoreToOriginal) {
                    "会把四项设置恢复到本工具第一次写入前的值（有备份的恢复原值，没有备份的删除）。"
                } else {
                    "会删除四项功能写入的所有 key，让系统回到自身默认策略；已开启的守护会同步关闭。"
                },
                color = Ink.tx2, fontSize = 13.sp, lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun FeatureGroup(
    state: ToolboxController.UiState,
    feature: ToolboxFeature,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickInterpolation: () -> Unit,
    onPickRefresh: () -> Unit,
    resetTick: Int,
) {
    val ui = state.features.firstOrNull { it.feature == feature }
    val statusText = when (ui?.state) {
        ToolboxFeatureState.ON -> "已生效"
        ToolboxFeatureState.OFF -> "未开启"
        ToolboxFeatureState.ABSENT -> "未写入"
        else -> "读取中"
    }
    MioPreferenceGroup {
        // 取消确认后重建开关，避免液态开关停留在"开"的视觉状态
        key(resetTick) {
            MioSwitchRow(
                title = feature.title,
                checked = ui?.on == true,
                enabled = enabled && ui?.busy != true,
                subtitle = "${feature.summary}\n${ui?.rawLine ?: ""} · $statusText",
                onCheckedChange = { onToggle(it) },
            )
        }
        when (feature) {
            ToolboxFeature.FRAME_INTERPOLATION -> {
                MioChoiceRow(
                    title = "插帧预设",
                    value = "${state.interpolation.fromFps} → ${state.interpolation.toFps} 帧",
                    subtitle = "目标帧率不是越高越好：要与屏幕刷新率匹配",
                    onClick = onPickInterpolation,
                )
            }
            ToolboxFeature.REFRESH_LOCK -> {
                MioChoiceRow(
                    title = "刷新率目标",
                    value = "${state.refreshTarget} Hz",
                    subtitle = "写入 min_refresh_rate / peak_refresh_rate",
                    onClick = onPickRefresh,
                )
            }
            else -> Unit
        }
    }
}


@Composable
private fun GlassDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Ink.glassFill.copy(alpha = scaledGlassFillAlpha(0.80f)),
        titleContentColor = Ink.tx,
        textContentColor = Ink.tx2,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink.tx) },
        text = body,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = Ink.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Ink.tx2) }
        },
    )
}

@Composable
private fun PresetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) Ink.accent else Ink.tx,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("✓", color = Ink.accent, fontSize = 15.sp)
    }
}
