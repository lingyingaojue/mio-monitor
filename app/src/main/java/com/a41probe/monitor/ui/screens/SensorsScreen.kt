package com.a41probe.monitor.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.data.MonitorViewModel
import com.a41probe.monitor.ui.components.MioCard
import com.a41probe.monitor.ui.components.MioMetric
import com.a41probe.monitor.ui.components.MioPageHeader
import com.a41probe.monitor.ui.components.MioPageScaffold
import com.a41probe.monitor.ui.components.MioSectionHeader
import com.a41probe.monitor.ui.components.ScrollAware
import com.a41probe.monitor.ui.components.SensorTile
import com.a41probe.monitor.ui.glass.LocalMioChromeInsets
import com.a41probe.monitor.ui.theme.Ink
import kotlin.math.roundToInt

private fun f2(v: Float): String = ((v * 100).roundToInt() / 100.0).toString()

data class SensorInfo(val type: Int, val name: String, val vendor: String)

@Composable
fun SensorsScreen(vm: MonitorViewModel, onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val live by vm.sensorsLive.collectAsState()
    val sensors = remember(ctx) { listSensors(ctx) }

    // S4: 仅传感器页可见时注册硬件采样，离开即注销
    DisposableEffect(Unit) {
        vm.registerSensors()
        onDispose { vm.unregisterSensors() }
    }

    val inset = Modifier.padding(horizontal = 14.dp)
    val listState = rememberLazyListState()
    MioPageScaffold(title = "传感器", onBack = onBack) {
        val chrome = LocalMioChromeInsets.current
        Box(Modifier.fillMaxSize()) {
    ScrollAware(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 2.dp + chrome.top,
                bottom = 20.dp + chrome.bottom,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MioPageHeader(
                    "传感器",
                    if (sensors.isEmpty()) "运动 · 环境 · 硬件上报"
                    else "运动 · 环境 · ${sensors.size} 类硬件上报",
                )
            }

            // ① 统计卡：在线硬件数 / 实时采样通道数（大数字指标）
            item {
                MioCard(inset) {
                    Text(
                        "传感器状态", color = Ink.tx, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            MioMetric(
                                label = "在线",
                                value = "${sensors.size}",
                                color = Ink.tx,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            MioMetric(
                                label = "实时采样",
                                value = "${live.size}",
                                unit = "组通道",
                                color = Ink.accent,
                            )
                        }
                    }
                }
            }

            item {
                MioSectionHeader("实时通道", modifier = inset)
            }

            // ② 5 类实时通道：每传感器一张卡片（传感器测试页）
            item {
                SensorTile(
                    name = "加速度传感器", desc = "检测设备加速度",
                    valueLines = live[Sensor.TYPE_ACCELEROMETER]?.let { a ->
                        listOf("X ${f2(a.getOrElse(0) { 0f })}", "Y ${f2(a.getOrElse(1) { 0f })}", "Z ${f2(a.getOrElse(2) { 0f })}")
                    }?.map { it to "m/s²" } ?: listOf("无数据" to ""),
                    iconBg = Ink.iconBgBlue, dotColor = Ink.ok,
                    modifier = inset,
                )
            }
            item {
                SensorTile(
                    name = "陀螺仪", desc = "检测设备旋转",
                    valueLines = live[Sensor.TYPE_GYROSCOPE]?.let { a ->
                        listOf("X ${f2(a.getOrElse(0) { 0f })}", "Y ${f2(a.getOrElse(1) { 0f })}", "Z ${f2(a.getOrElse(2) { 0f })}")
                    }?.map { it to "rad/s" } ?: listOf("无数据" to ""),
                    iconBg = Ink.iconBgPurple, dotColor = Ink.ok,
                    modifier = inset,
                )
            }
            item {
                SensorTile(
                    name = "磁力计", desc = "检测磁场强度",
                    valueLines = live[Sensor.TYPE_MAGNETIC_FIELD]?.let { a ->
                        listOf("X ${f2(a.getOrElse(0) { 0f })}", "Y ${f2(a.getOrElse(1) { 0f })}", "Z ${f2(a.getOrElse(2) { 0f })}")
                    }?.map { it to "μT" } ?: listOf("无数据" to ""),
                    iconBg = Ink.iconBgCyan, dotColor = Ink.ok,
                    modifier = inset,
                )
            }
            item {
                SensorTile(
                    name = "光线传感器", desc = "检测环境光强度",
                    valueLines = live[Sensor.TYPE_LIGHT]?.let { a ->
                        listOf("${f2(a.getOrElse(0) { 0f })}" to "lux")
                    } ?: listOf("无数据" to ""),
                    iconBg = Ink.iconBgYellow, dotColor = Ink.ok,
                    modifier = inset,
                )
            }
            item {
                SensorTile(
                    name = "距离传感器", desc = "检测物体距离",
                    valueLines = live[Sensor.TYPE_PROXIMITY]?.let { a ->
                        listOf("${f2(a.getOrElse(0) { 0f })}" to "cm")
                    } ?: listOf("无数据" to ""),
                    iconBg = Ink.iconBgOrange, dotColor = Ink.ok,
                    modifier = inset,
                )
            }
            item {
                SensorTile(
                    name = "计步 / 活动识别", desc = "未采样 · 仅 5 类实时通道",
                    valueLines = listOf("未采样" to ""),
                    iconBg = Ink.panel, dotColor = Ink.off,
                    modifier = inset,
                )
            }

            // ③ 全部传感器列表（硬件枚举：名称 + type/vendor）
            if (sensors.isNotEmpty()) {
                item {
                    MioSectionHeader("全部 ${sensors.size} 只", modifier = inset)
                }
                item {
                    MioCard(inset) {
                        sensors.chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pair.forEach { s ->
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            s.name, color = Ink.tx, fontSize = 12.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "type ${s.type} · ${s.vendor}", color = Ink.off,
                                            fontSize = 9.5.sp, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
        }
    }
}

private fun listSensors(ctx: Context): List<SensorInfo> {
    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyList()
    return sm.getSensorList(Sensor.TYPE_ALL).map { SensorInfo(it.type, it.name, it.vendor) }
}
