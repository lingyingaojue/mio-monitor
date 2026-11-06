package com.a41probe.monitor.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a41probe.monitor.ui.glass.MioGlassRole
import com.a41probe.monitor.ui.glass.MioGlassSampling
import com.a41probe.monitor.ui.glass.MioGlassSurface
import com.a41probe.monitor.ui.glass.scaledGlassFillAlpha
import com.a41probe.monitor.ui.theme.Ink
import com.a41probe.monitor.ui.theme.SpecialConfig
import com.a41probe.monitor.ui.theme.artKeyResolve

/** 引导单页：立绘/表情 + 标题 + 分条说明 */
data class OnboardingPage(val artKey: String, val title: String, val bullets: List<String>)

/** 引导持久化：SharedPreferences 名与"已完成引导"键 */
const val MIO_PREFS = "mio_prefs"
const val KEY_ONBOARD_DONE = "onboarding_done"

/**
 * 首次启动使用引导覆盖层（Miuix / BiliPai 版）：
 * Ink.bg 底 + 一张 28dp 大圆角**液态玻璃**分页卡片（立绘 / 标题 / 分条说明），
 * 玻璃采样背景层（环境光 + 角色立绘），卡片下透出模糊后的页面底色；
 * 顶部左侧是品牌、右侧是「跳过」，底部是居中的进度指示与整行主按钮。
 * 文案与 onFinish 行为与旧版完全一致；系统返回键 = 回上一页（首页返回 = 跳过结束）。
 */
@Composable
fun OnboardingOverlay(pages: List<OnboardingPage>, onFinish: () -> Unit) {
    var idx by rememberSaveable { mutableIntStateOf(0) }
    val isLast = idx == pages.lastIndex
    // v0.26.1: 系统返回键 = 回上一页（首页返回=跳过结束），符合移动端导航直觉
    BackHandler { if (idx > 0) idx-- else onFinish() }
    val dur = if (rememberAnimatorScale() > 0f) 280 else 0
    val dotDur = if (dur > 0) 220 else 0
    val cardShape = RoundedCornerShape(28.dp)
    val artShape = RoundedCornerShape(22.dp)

    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 顶部：品牌（左） + 跳过（右）——Miuix 大留白，只保留必要操作
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Mio 澪", color = Ink.tx, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "跳过",
                    color = Ink.tx3, fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onFinish)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            AnimatedContent(
                targetState = idx,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    // Reduce Motion：dur=0 时纯直切；否则淡入 + 极轻横向位移
                    (fadeIn(tween(dur)) +
                        slideInHorizontally(tween(dur)) { it / 10 })
                        .togetherWith(
                            fadeOut(tween((dur * 0.7f).toInt().coerceAtLeast(1)))
                        )
                },
                label = "onboard",
            ) { i ->
                val p = pages[i]
                // v3.1 液态玻璃：分页卡改为玻璃表面（CARD 角色 + 采样内容层）。
                // 引导层是覆盖层，它下方是内容层（chromeState），因此显式采样 CHROME，
                // 卡片下透出被模糊的仪表盘 UI；引导层自身不再叠第二层模糊，避免嵌套 haze。
                MioGlassSurface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    shape = cardShape,
                    role = MioGlassRole.CARD,
                    sampling = MioGlassSampling.CHROME,
                ) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        // 立绘 / 表情：玻璃舞台上的一层薄底，透明底素材也不显单薄
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(206.dp)
                                .clip(artShape)
                                .background(Ink.glassFill.copy(alpha = scaledGlassFillAlpha(0.12f))),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 素材缺失（角色图不随仓库分发）时跳过绘制，避免 painterResource(0) 崩溃
                            val artRes = artKeyResolve(p.artKey)
                            if (artRes != 0) {
                                Image(
                                    painter = painterResource(artRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(10.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            p.title,
                            color = Ink.tx, fontSize = 26.sp, lineHeight = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(
                            Modifier.weight(1f).fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            p.bullets.forEach { b ->
                                Row(Modifier.fillMaxWidth()) {
                                    Box(
                                        Modifier.padding(top = 7.dp).size(5.dp)
                                            .clip(CircleShape).background(Ink.accent),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        b, color = Ink.tx2, fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            // 底部：进度指示（居中）+ 主按钮（整行）——Miuix 的"一屏一个主动作"
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pages.indices.forEach { i ->
                        val active = i == idx
                        val w by animateDpAsState(
                            targetValue = if (active) 20.dp else 6.dp,
                            animationSpec = tween(dotDur),
                            label = "onboardDot",
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (active) Ink.accent else Ink.stroke)
                                .size(width = w, height = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ProbeButton(
                    text = if (isLast) "开始使用" else "下一步",
                    accent = true,
                    onClick = { if (isLast) onFinish() else idx++ },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Mio 澪 使用引导内容（10 页，覆盖全部页面与卡片）。 */
fun mioOnboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        "expr:hi_head",
        "你好，我是${SpecialConfig.current().label}",
        listOf(
            "Mio 澪是一款纯只读的硬件参数监控工具",
            "实时展示 CPU、GPU、电池、温度、传感器等全部可读参数",
            "纯只读设计：不修改任何系统设置，数据不联网、不上传",
            "接下来用一分钟，带你认识每个页面",
        ),
    ),
    OnboardingPage(
        "expr:think_head",
        "三档权限，按需选择",
        listOf(
            "免提权（—）：电量、CPU 频率、内存、传感器，开箱即用",
            "Shizuku（S）：解锁 CPU 占用、全部温度区、热缓解、GPU 温度",
            "Root（R）：再解锁 GPU 频率、充电器输入侧真实功率",
            "卡片标题旁的 S / R 标记表示所需档位，未授权显示“–”，不放假数据",
            "授权入口：设置页 → 提权通道 → 一键授权（优先 Root）",
        ),
    ),
    OnboardingPage(
        "art:dashboard",
        "仪表盘 · 一眼总览",
        listOf(
            "总览：电量 / CPU 占用 / 内存已用 / 最高结温 四项并排；免提权可读电量、内存、电池温度",
            "CPU 占用曲线：近 60 秒总占用变化",
            "电池概览：状态、电压电流、功率一行显示，点击进入电池页查看健康度与循环",
            "GPU 占用、系统负载与内存详情依次排列",
        ),
    ),
    OnboardingPage(
        "art:cpu",
        "CPU · 占用 / 频率 / 档位 / 调度",
        listOf(
            "总占用：大数字与 60 秒曲线（S）",
            "核心频率：每核独立大数字与迷你频率曲线",
            "分核占用：8 个核心各自的实时占用（S）",
            "频率停留：柱高为各频率的停留时间占比，当前频率蓝色高亮",
            "调度与限频：governor 策略、各簇频率上限",
            "同簇核心共享频率域，频率始终一致；空闲停在最低频率属正常",
        ),
    ),
    OnboardingPage(
        "art:gpu",
        "GPU · 图形核心",
        listOf(
            "占用：使用率大数字与 0–100% 固定量程曲线",
            "频率：当前工作频率（R，部分机型可免提权直读）",
            "GPU 核心 0 / 1：两个 gpuss 温区的实时温度（S）",
            "温度曲线：核心 0 近 60 秒温度变化",
        ),
    ),
    OnboardingPage(
        "art:battery",
        "电池 · 容量 / 功率 / 健康",
        listOf(
            "状态大卡：电量环、充放电状态、功率大字",
            "功率优先取充电器输入侧真实值（需提权），否则回退电池侧 V×I",
            "充电器协商：系统上报的协商电压 / 电流 / 上限，充电时显示",
            "标称 66W 为峰值，实际随电量、温度、协议浮动",
            "功率 / 电压 / 电流 / 温度四条曲线，详情含健康度与循环",
            "24h 容量趋势本地记录，每 60 秒一点",
        ),
    ),
    OnboardingPage(
        "art:thermal",
        "温度 · 结温 / 外壳 / 全部热区",
        listOf(
            "顶部：最高结温、全温区均值、外壳 skin 温度",
            "结温是芯片内部温度，外壳是背板手感参考，结温通常高 15–25°C",
            "距温度红线：各模块当前温度与官方降频阈值之比",
            "热缓解动作：系统限频 / 限流等级，可展开全部",
            "分组热区：CPU、GPU·NPU·内存、射频·充电·外壳",
            "全部热区网格：每格一个传感器，灰格为未启用",
        ),
    ),
    OnboardingPage(
        "art:sensor",
        "传感器 · 运动与环境",
        listOf(
            "加速度 X/Y/Z、陀螺仪、磁力计、光线、距离五类实时通道",
            "每类独立卡片，数值带真实单位",
            "全部传感器列表：名称、类型编号、厂商",
            "仅页面可见时采样，离开即注销，不持续耗电",
        ),
    ),
    OnboardingPage(
        "expr:wow_head",
        "局域网双模式 · 两台手机联动",
        listOf(
            "同一个 App，两种角色：被监控端与监控端",
            "被监控模式：前台服务广播本机，三档授权采集，可随时终止",
            "防杀后台：常驻通知、忽略电池优化、锁屏保持、厂商保活引导",
            "监控模式：mDNS 自动发现设备并全自动连接，也可手动添加 IP",
            "设备卡展示全部关键参数，点卡片进入详情看历史曲线",
            "前提：两台手机连接同一局域网",
        ),
    ),
    OnboardingPage(
        "expr:go_head",
        "一切就绪",
        listOf(
            "设置页：双模式入口、提权通道、采样与导出",
            "导出 CSV：含原始值与界面显示值，保存到 下载/Mio",
            "设备信息全部运行时真读，不写死机型",
            "想再看本引导：设置页 → 使用引导",
            "开始使用吧",
        ),
    ),
)
