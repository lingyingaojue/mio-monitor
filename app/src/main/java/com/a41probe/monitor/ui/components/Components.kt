package com.a41probe.monitor.ui.components

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.ui.glass.LocalMioChromeInsets
import com.a41probe.monitor.ui.glass.MioChromeInsets
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.MioGlassSurface
import com.a41probe.monitor.ui.glass.TopBarHeight
import com.a41probe.monitor.ui.glass.mioGlassGloss
import com.a41probe.monitor.ui.theme.Ink
import com.a41probe.monitor.ui.theme.Priv
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPrimaryButton
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

private val CardShape = RoundedCornerShape(18.dp)
private val TileShape = RoundedCornerShape(12.dp)

/* ============================================================
 * 组件层（v3.0）：保留 v2 的全部公开 API（屏幕代码无需改签名），
 * 内部视觉换成 BiliPai / Miuix 风格 —— 卡片走 design-system AppCard，
 * 设置/开关走 AppPreference / AppSwitchPreference，按钮走 AppButton。
 * ============================================================ */

/**
 * 卡片容器（BiliPai 风格）：16dp 大圆角、卡片表面色、Miuix 按压反馈。
 * 保留旧签名：ProbeCard(modifier, onClick, content)。
 */
@Composable
fun ProbeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppCardShape.Semantic(ContainerLevel.Card),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

/**
 * SafeCard 作用域：content 内可调用 safe{} 包裹危险计算，
 * 捕获异常后降级为占位卡，避免单卡异常导致整页崩溃。
 */
class SafeCardScope internal constructor(
    private val onError: (String) -> Unit,
) {
    fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        onError(t.message ?: "模块读取异常")
        null
    }
}

/**
 * 卡片级错误边界：包装 ProbeCard，维护错误状态。
 */
@Composable
fun SafeCard(
    modifier: Modifier = Modifier,
    fallbackTitle: String = "模块",
    onClick: (() -> Unit)? = null,
    content: @Composable SafeCardScope.() -> Unit,
) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = remember { SafeCardScope { msg -> error = msg } }
    if (error != null) {
        ProbeCard(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.warn))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("$fallbackTitle 读取异常", color = Ink.tx, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(error ?: "该模块数据暂时不可用", color = Ink.tx3, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
        return
    }
    ProbeCard(modifier, onClick = onClick) { scope.content() }
}

/** 卡片内嵌块（表面深一档、12dp 圆角）：细分指标分组 */
@Composable
fun NestedTile(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(Ink.surfaceSunken)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = { content() },
    )
}

/** 卡片标题行：组标题 + 提权小标 + 右侧标注 */
@Composable
fun CardTitle(
    label: String,
    priv: Priv? = null,
    rootActive: Boolean = false,
    right: @Composable (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppText(
            text = label,
            color = Ink.tx,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (priv != null && priv != Priv.FREE) {
            Spacer(Modifier.width(6.dp))
            PrivBadge(if (priv == Priv.SHIZUKU && rootActive) Priv.ROOT else priv)
        }
        if (right != null) {
            Spacer(Modifier.width(8.dp))
            right()
        }
    }
}

/** 提权小标：浅底圆角 chip */
@Composable
fun PrivBadge(priv: Priv, text: String? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(priv.color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text ?: priv.tag,
            color = priv.color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 状态徽章：小圆点 + 文字（BiliPai 胶囊 chip） */
@Composable
fun DotBadge(dotColor: Color, text: String, onBlue: Boolean = false, alpha: Float = 1f) {
    val pill = if (onBlue) {
        Color.White.copy(alpha = if (Ink.onAccent.luminance() > 0.5f) 0.2f else 0.34f)
    } else {
        Ink.panel
    }
    Row(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(9.dp))
            .background(pill)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(5.dp))
        Text(text, color = if (onBlue) Ink.onAccent else Ink.tx2, fontSize = 10.5.sp)
    }
}

/** 数值展示：大数字（主色）+ 单位 + 副说明 */
@Composable
fun StatValue(
    value: String,
    unit: String = "",
    color: Color = Ink.accent,
    sizeSp: Int = 28,
    sub: String? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value, color = color, fontSize = sizeSp.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = Ink.tx2, fontSize = 12.sp)
            }
        }
        if (sub != null) {
            Spacer(Modifier.height(3.dp))
            Text(sub, color = Ink.tx3, fontSize = 12.sp)
        }
    }
}

/** 信息行：左标签 / 右数值（灰单位） / 状态点 */
@Composable
fun StatRow(
    name: String,
    value: String,
    unit: String = "",
    stateColor: Color? = null,
    mono: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stateColor != null) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(stateColor))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            name, color = Ink.tx, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = if (mono) Ink.accent else Ink.tx2,
            fontSize = if (mono) 14.sp else 14.sp,
            fontFamily = if (mono) FontFamily.Monospace else null,
            fontWeight = if (mono) FontWeight.Medium else FontWeight.Normal,
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(3.dp))
            Text(unit, color = Ink.off, fontSize = 11.sp)
        }
    }
}

/** 横条行（温度/占比）：名称 + 归一化条 + 值 */
@Composable
fun BarRow(
    name: String,
    frac: Float,
    value: String,
    barColor: Color,
    nameWidth: Dp = 96.dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name, color = Ink.tx, fontSize = 12.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(nameWidth),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Ink.panel),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value, color = Ink.tx, fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
        )
    }
}

/** 温度色阶：≥42 红 / ≥36 橙 / 其余绿 / 缺失灰 */
fun tempColor(c: Double?): Color {
    if (c == null) return Ink.off
    return when {
        c >= 42 -> Ink.danger
        c >= 36 -> Ink.warn
        else -> Ink.ok
    }
}

fun tempFrac(c: Double?): Float {
    if (c == null) return 0f
    return ((c - 20f) / 30f).toFloat().coerceIn(0f, 1f)
}

/** 传感器卡片（图标圆 + 状态点 + 名称/说明 + 数值多轴横排） */
@Composable
fun SensorTile(
    name: String,
    desc: String,
    valueLines: List<Pair<String, String>>,
    iconBg: Color,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    MioGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        role = MioGlassRole.CARD,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Ink.tx, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, color = Ink.tx2, fontSize = 11.5.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    valueLines.forEach { (v, u) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                            Text(
                                v, color = Ink.accent, fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                            )
                            if (u.isNotEmpty()) {
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    u, color = Ink.off, fontSize = 9.5.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 设置行（旧 API，保留）：左标题/副标题 + 右侧控件 */
@Composable
fun SettingRow(
    title: String,
    sub: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.tx, fontSize = 16.sp)
            if (sub != null) Text(sub, color = Ink.tx2, fontSize = 12.5.sp)
        }
        trailing()
    }
}

/** 按钮（旧 API，保留）：主色实底 / 普通浅底 */
@Composable
fun ProbeButton(
    text: String,
    accent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accent) {
        AppPrimaryButton(text = text, onClick = onClick, modifier = modifier)
    } else {
        AppButton(
            onClick = onClick,
            modifier = modifier,
            shape = AppShapes.container(ContainerLevel.Pill),
            containerColor = Ink.panel,
            contentColor = Ink.accent,
        ) {
            Text(
                text,
                color = Ink.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/* ============================================================
 * v3.0 新增组件：供数据屏/功能页使用（BiliPai 版式的基础积木）
 * ============================================================ */

/** 页面大标题（BiliPai 大标题 + 副标题）；横向留白与卡片 14dp 栅格对齐 */
@Composable
fun MioPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 6.dp),
    ) {
        AppText(
            text = title,
            color = Ink.tx,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
        )
        if (!subtitle.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Ink.tx2, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/** 分组小标题（Miuix SmallTitle 风格） */
@Composable
fun MioSectionHeader(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = title,
            color = Ink.tx2,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 内容卡片（v3.1 液态玻璃）：背景实时模糊 + 半透明材质 + 高光描边。 */
@Composable
fun MioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    MioGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        role = MioGlassRole.CARD,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/** 大号指标块：标签 + 大数字 + 单位（卡片内两列均分用） */
@Composable
fun ColumnScope.MioMetric(
    label: String,
    value: String,
    unit: String = "",
    color: Color = Ink.accent,
    animated: Float? = null,
    format: (Float) -> String = { "%.0f".format(it) },
    valueSizeSp: Int = 30,
    mono: Boolean = true,
    sub: String? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Ink.tx2, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (animated != null) {
                AnimatedNum(
                    target = animated,
                    format = format,
                    color = color,
                    sizeSp = valueSizeSp,
                    mono = mono,
                    weight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    value, color = color, fontSize = valueSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                )
            }
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = Ink.off, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (sub != null) {
            Spacer(Modifier.height(3.dp))
            Text(sub, color = Ink.off, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 胶囊 chip（提权/状态/单位标签） */
@Composable
fun MioChip(
    text: String,
    color: Color = Ink.accent,
    containerColor: Color = color.copy(alpha = 0.14f),
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(containerColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontFamily = if (mono) FontFamily.Monospace else null,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 信息行（新 API）：名称 / 数值 / 单位 / 状态点 */
@Composable
fun MioInfoRow(
    name: String,
    value: String,
    unit: String = "",
    stateColor: Color? = null,
    mono: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stateColor != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(stateColor))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            name, color = Ink.tx, fontSize = 14.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            value, color = Ink.tx2,
            fontSize = 14.5.sp,
            fontFamily = if (mono) FontFamily.Monospace else null,
            fontWeight = FontWeight.Medium,
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(3.dp))
            Text(unit, color = Ink.off, fontSize = 11.sp)
        }
    }
}

/** 进度条行（新 API） */
@Composable
fun MioBarRow(
    name: String,
    frac: Float,
    value: String,
    barColor: Color,
    nameWidth: Dp = 88.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name, color = Ink.tx, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(nameWidth),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Ink.panel),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            value, color = Ink.tx2, fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
        )
    }
}

/** 开关设置行（Miuix 风格） */
@Composable
fun MioSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        AppSwitchPreference(
            title = title,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** 选择/导航设置行（Miuix 风格，右侧值 + chevron） */
@Composable
fun MioChoiceRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        AppPreference(
            title = title,
            subtitle = subtitle,
            value = value,
            onClick = onClick,
            showChevron = onClick != null,
        )
    }
}

/** 信息展示设置行（无 chevron） */
@Composable
fun MioSettingRow(
    title: String,
    value: String,
    subtitle: String? = null,
) {
    AppPreference(
        title = title,
        subtitle = subtitle,
        value = value,
        onClick = null,
        showChevron = false,
    )
}

/** 分组卡片容器（设置页，v3.1 液态玻璃） */
@Composable
fun MioPreferenceGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    MioGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        role = MioGlassRole.CARD,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
    }
}

/**
 * 页面返回条（v3.1 液态玻璃）：整条玻璃悬浮在内容之上，内容从它下方滚过。
 * 调用方请用 [MioPageScaffold]（自动叠放 + 提供内容 insets），不要放进 Column 顶部。
 */
@Composable
fun MioPageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    MioGlassSurface(
        modifier = modifier.fillMaxWidth().height(statusTop + TopBarHeight),
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        role = MioGlassRole.CHROME,
        progress = 1f,
    ) {
        Box(Modifier.fillMaxSize().padding(top = statusTop, start = 10.dp, end = 10.dp)) {
            // 48dp 触控目标内放 38dp 玻璃圆钮
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Ink.glassFill.copy(alpha = 0.34f))
                        .mioGlassGloss(CircleShape, lightAlpha = 0.5f, sheenAlpha = 0.10f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Ink.tx,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                title,
                color = Ink.tx,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
            if (action != null) {
                Box(Modifier.align(Alignment.CenterEnd)) { action() }
            }
        }
    }
}

/**
 * 整页脚手架（v3.1）：内容铺满全屏（从玻璃返回条下方穿过），返回条以玻璃叠层悬浮，
 * 并通过 [LocalMioChromeInsets] 把「返回条 + 底部安全区」的占位下发给内容。
 */
@Composable
fun MioPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val insets = remember(statusTop, navBottom) {
        MioChromeInsets(
            top = statusTop + TopBarHeight + 4.dp,
            bottom = navBottom + 24.dp,
        )
    }
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalMioChromeInsets provides insets) {
            content()
        }
        MioPageTopBar(
            title = title,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** 分割线 */
@Composable
fun MioDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Ink.divider),
    )
}

/**
 * v20.6：iOS 动效对齐——统一时长/弹簧/呼吸参数。
 * v3.0：保留手感，数值与 BiliPai 的 Miuix 动效基线对齐。
 */
object Motion {
    /** 统一缓动曲线 */
    val easing = FastOutSlowInEasing

    /** 柔和回弹（松开恢复 / 指示器落位 / overscroll 归位） */
    fun <T> softSpring(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 320f)

    const val pressMs = 130
    const val releaseMs = 240
    const val numMs = 320
    val barSpring = spring<Float>(dampingRatio = 1f, stiffness = 520f)
    const val tabMs = 150
    const val gaugeMs = 300
    const val pulseMs = 340
    const val numSmallMs = 220
    const val indicatorMs = 280
    const val sheenMs = 1600
}

/**
 * Reduce Motion 无障碍——读取系统动画缩放（ANIMATOR_DURATION_SCALE）。
 * 缩放=0 时返回 0，调用方应 snapTo 直切而非动画。
 */
@Composable
fun rememberAnimatorScale(): Float {
    val ctx = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    DisposableEffect(Unit) {
        val resolver: ContentResolver = ctx.contentResolver
        fun read() {
            scale = Settings.Global.getFloat(
                resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            )
        }
        read()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}

/**
 * 数字平滑滚动（iOS 计数器感）；首次直接定位。
 */
@Composable
fun AnimatedNum(
    target: Float?,
    format: (Float) -> String,
    color: Color,
    sizeSp: Int = 26,
    weight: FontWeight = FontWeight.SemiBold,
    mono: Boolean = true,
    rollFromZero: Boolean = false,
    onRolled: () -> Unit = {},
) {
    if (target == null) {
        Text(
            "–", color = color, fontSize = sizeSp.sp,
            fontWeight = weight, fontFamily = if (mono) FontFamily.Monospace else null,
        )
        return
    }
    val anim = remember { Animatable(target) }
    val animScale = rememberAnimatorScale()
    val scrolling = LocalScrolling.current
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target, rollFromZero, scrolling) {
        if (scrolling) {
            anim.snapTo(target)
            return@LaunchedEffect
        }
        if (rollFromZero && animScale > 0f) {
            anim.snapTo(0f)
            anim.animateTo(target, tween(durationMillis = 420, easing = FastOutSlowInEasing))
            onRolled()
        } else if (first) {
            first = false
            anim.snapTo(target)
        } else if (animScale <= 0f) {
            anim.snapTo(target)
        } else {
            val diff = kotlin.math.abs(target - anim.value)
            if (diff <= 0.03f) {
                anim.snapTo(target)
            } else if (diff <= 0.5f) {
                anim.animateTo(target, tween(durationMillis = Motion.numSmallMs, easing = FastOutSlowInEasing))
            } else {
                anim.animateTo(target, tween(durationMillis = Motion.numMs, easing = FastOutSlowInEasing))
            }
        }
    }
    Text(
        format(anim.value), color = color, fontSize = sizeSp.sp,
        fontWeight = weight, fontFamily = if (mono) FontFamily.Monospace else null,
    )
}

/** 按压反馈：按下轻微收缩 + 明暗下沉，抬起平滑恢复 */
fun Modifier.pressClick(onClick: () -> Unit): Modifier = pressClickStyled(onClick = onClick)

fun Modifier.pressClickStyled(
    scale: Float = 0.975f,
    dim: Float = 0.94f,
    haptic: Boolean = false,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = rememberAnimatorScale()
    val animate = pressScale > 0f
    val ms = if (pressed) Motion.pressMs else Motion.releaseMs
    val s by animateFloatAsState(
        if (pressed && animate) scale else 1f,
        tween(durationMillis = ms, easing = Motion.easing), label = "pressScale",
    )
    val a by animateFloatAsState(
        if (pressed && animate) dim else 1f,
        tween(durationMillis = ms, easing = Motion.easing), label = "pressDim",
    )
    val haptics = rememberHaptics()
    this
        .graphicsLayer { scaleX = s; scaleY = s; alpha = a }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
        ) {
            if (haptic) haptics.press()
            onClick()
        }
}

/**
 * 液态玻璃外壳（旧 API 保留；新外壳页可用 MioGlass 系列）。
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.White,
    alpha: Float = 0.66f,
    shadow: Dp = 4.dp,
): Modifier = composed {
    val top = tint.copy(alpha = (alpha + 0.16f).coerceAtMost(0.94f))
    val bottom = tint.copy(alpha = (alpha - 0.18f).coerceAtLeast(0.22f))
    this
        .shadow(shadow, shape, clip = false)
        .background(Brush.verticalGradient(0f to top, 1f to bottom), shape)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.28f)),
            ),
            shape = shape,
        )
}

/** 进度条平滑过渡（频率条/内存条/温度条共用） */
@Composable
fun AnimatedFracBar(
    frac: Float,
    color: Color,
    height: Dp,
    corner: Dp,
    trackColor: Color = Ink.panel,
) {
    val target = frac.coerceIn(0f, 1f)
    val a = remember { Animatable(target) }
    val animScale = rememberAnimatorScale()
    val scrolling = LocalScrolling.current
    LaunchedEffect(target, scrolling) {
        if (scrolling || animScale <= 0f) a.snapTo(target) else a.animateTo(target, Motion.barSpring)
    }
    Box(
        Modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(corner)).background(trackColor),
    ) {
        Box(
            Modifier.fillMaxWidth(a.value).height(height)
                .clip(RoundedCornerShape(corner)).background(color),
        )
    }
}
