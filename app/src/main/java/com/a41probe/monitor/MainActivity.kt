package com.a41probe.monitor

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.a41probe.monitor.data.MonitorViewModel
import com.a41probe.monitor.ui.components.MioArtBackground
import com.a41probe.monitor.ui.components.MioBottomDock
import com.a41probe.monitor.ui.components.MioChip
import com.a41probe.monitor.ui.components.MioTabItem
import com.a41probe.monitor.ui.components.MioTopBar
import com.a41probe.monitor.ui.components.OnboardingOverlay
import com.a41probe.monitor.ui.components.ProbeButton
import com.a41probe.monitor.ui.components.SpecialFace
import com.a41probe.monitor.ui.components.KEY_ONBOARD_DONE
import com.a41probe.monitor.ui.components.MIO_PREFS
import com.a41probe.monitor.ui.components.mioOnboardingPages
import com.a41probe.monitor.ui.components.rememberAnimatorScale
import com.a41probe.monitor.ui.screens.AgentScreen
import com.a41probe.monitor.ui.screens.BatteryScreen
import com.a41probe.monitor.ui.screens.CpuScreen
import com.a41probe.monitor.ui.screens.DashboardScreen
import com.a41probe.monitor.ui.screens.GpuScreen
import com.a41probe.monitor.ui.screens.MonitorScreen
import com.a41probe.monitor.ui.screens.RemoteDetailScreen
import com.a41probe.monitor.ui.screens.SensorsScreen
import com.a41probe.monitor.ui.screens.SettingsScreen
import com.a41probe.monitor.ui.screens.ThermalScreen
import com.a41probe.monitor.ui.theme.Ink
import com.a41probe.monitor.ui.theme.MioTheme
import com.a41probe.monitor.ui.theme.SpecialConfig
import com.a41probe.monitor.ui.theme.ThemeConfig
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Videocam
import kotlinx.coroutines.delay

enum class Screen(val label: String) {
    DASH("仪表盘"), CPU("CPU"), GPU("GPU"), BAT("电池"), THERM("温度"),
    SENS("传感器"), SET("设置"),
    AGENT("被监控"), MONITOR("监控"), RDETAIL("设备详情")
}

/** 5 个主 tab（BiliPai 底栏设计：5 项 + 悬浮 dock） */
private val TAB_SCREENS = listOf(Screen.DASH, Screen.CPU, Screen.GPU, Screen.BAT, Screen.THERM)

/** 带底部导航的本机监控页集合；双模式与设置页为全屏页，不显示 dock。 */
private val MAIN_TABS = TAB_SCREENS.toSet()

/** 底部 dock 的 5 项（图标 + 文案） */
private fun tabItems(): List<MioTabItem> = listOf(
    MioTabItem("仪表盘", Icons.Rounded.Dashboard),
    MioTabItem("CPU", Icons.Rounded.Memory),
    MioTabItem("GPU", Icons.Rounded.Videocam),
    MioTabItem("电池", Icons.Rounded.BatteryFull),
    MioTabItem("温度", Icons.Rounded.Thermostat),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashCatcher.install(this)
        // 沉浸式：状态栏/导航栏透明；图标明暗由 MioTheme 按当前主题切换
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            MioTheme(
                palette = ThemeConfig.palette(),
                darkTheme = ThemeConfig.isDark(),
            ) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MonitorViewModel = viewModel()) {
    var screen by rememberSaveable(stateSaver = Saver<Screen, String>(
        save = { it.name },
        restore = { runCatching { Screen.valueOf(it) }.getOrDefault(Screen.DASH) },
    )) { mutableStateOf(Screen.DASH) }
    // 记录从哪个 tab 进入设置，返回时回到原 tab
    var settingsReturn by rememberSaveable(stateSaver = Saver<Screen, String>(
        save = { it.name },
        restore = { runCatching { Screen.valueOf(it) }.getOrDefault(Screen.DASH) },
    )) { mutableStateOf(Screen.DASH) }
    var showShizuku by remember { mutableStateOf(false) }
    var remoteKey by rememberSaveable { mutableStateOf("") }

    val appCtx = LocalContext.current
    val mioPrefs = remember {
        appCtx.getSharedPreferences(MIO_PREFS, Context.MODE_PRIVATE)
    }
    var onboardingPending by rememberSaveable {
        mutableStateOf(!mioPrefs.getBoolean(KEY_ONBOARD_DONE, false))
    }
    var showOnboarding by remember { mutableStateOf(false) }
    val onboardPages = remember { mioOnboardingPages() }

    val isTab = screen in MAIN_TABS
    BackHandler(enabled = !isTab) {
        screen = when (screen) {
            Screen.RDETAIL -> Screen.MONITOR
            Screen.SENS, Screen.AGENT, Screen.MONITOR -> Screen.SET
            Screen.SET -> settingsReturn
            else -> Screen.DASH
        }
    }

    // 开屏品牌画面
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        val shownAt = System.currentTimeMillis()
        if (splash) {
            while (System.currentTimeMillis() - shownAt < 900) {
                delay(40)
            }
            splash = false
            vm.armHeroRoll()
        }
        if (onboardingPending) showOnboarding = true
    }

    val priv by vm.privFlow.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.STARTED,
    )
    val lastAt by vm.lastSuccessAt.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    vm.refreshShizuku()
                    vm.quickRefresh(); vm.setBackground(false); vm.start()
                }
                Lifecycle.Event.ON_STOP -> {
                    vm.setBackground(true)
                    vm.unregisterSensors()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    vm.stop()
                    vm.unregisterSensors()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val special = SpecialConfig.current()
    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        Column(Modifier.fillMaxSize()) {
            if (isTab) {
                MioTopBar(
                    model = Build.MODEL,
                    authorized = priv.shizukuActive,
                    serviceUp = priv.shizukuServiceUp,
                    rootAvailable = priv.rootAvailable,
                    lastSuccessAt = lastAt,
                    onStatusClick = { showShizuku = true },
                    onSettingsClick = {
                        settingsReturn = screen
                        screen = Screen.SET
                    },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (isTab && ThemeConfig.showArtwork()) {
                    MioArtBackground(special.artFor(artKeyFor(screen)))
                }
                val tabScale = rememberAnimatorScale()
                val tabDur = if (tabScale > 0f) 150 else 0
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        fadeIn(tween(tabDur)).togetherWith(fadeOut(tween(tabDur)))
                    },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.DASH -> DashboardScreen(
                            vm,
                            onGoSettings = { settingsReturn = Screen.DASH; screen = Screen.SET },
                            onGoBattery = { screen = Screen.BAT },
                        )
                        Screen.CPU -> CpuScreen(vm)
                        Screen.GPU -> GpuScreen(vm)
                        Screen.BAT -> BatteryScreen(vm)
                        Screen.THERM -> ThermalScreen(vm)
                        Screen.SENS -> SensorsScreen(vm, onBack = { screen = Screen.SET })
                        Screen.SET -> SettingsScreen(
                            vm,
                            onOpenSensors = { screen = Screen.SENS },
                            onOpenAgent = { screen = Screen.AGENT },
                            onOpenMonitor = { screen = Screen.MONITOR },
                            onShowOnboarding = { showOnboarding = true },
                        )
                        Screen.AGENT -> AgentScreen(vm, onBack = { screen = Screen.SET })
                        Screen.MONITOR -> MonitorScreen(
                            onOpenDevice = { k -> remoteKey = k; screen = Screen.RDETAIL },
                            onBack = { screen = Screen.SET },
                        )
                        Screen.RDETAIL -> RemoteDetailScreen(
                            deviceKey = remoteKey,
                            onBack = { screen = Screen.MONITOR },
                        )
                    }
                }
            }
            if (isTab) {
                MioBottomDock(
                    items = tabItems(),
                    selectedIndex = TAB_SCREENS.indexOf(screen).coerceAtLeast(0),
                    onSelect = { idx -> screen = TAB_SCREENS.getOrElse(idx) { Screen.DASH } },
                )
            }
        }

        if (showShizuku) {
            ModalBottomSheet(
                onDismissRequest = { showShizuku = false },
                containerColor = Ink.card,
                shape = AppShapes.container(ContainerLevel.Sheet),
            ) {
                PrivGuideSheet(
                    serviceUp = priv.shizukuServiceUp,
                    authorized = priv.shizukuActive,
                    rootAvailable = priv.rootAvailable,
                    onGoSettings = {
                        settingsReturn = if (screen in MAIN_TABS) screen else Screen.DASH
                        screen = Screen.SET
                        showShizuku = false
                    },
                )
            }
        }

        // 开屏品牌画面
        AnimatedVisibility(
            visible = splash,
            exit = fadeOut(tween(250)),
        ) {
            val ctx = LocalContext.current
            val ver = remember(ctx) {
                try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
            val reduce = rememberAnimatorScale() <= 0f
            val appear = remember { Animatable(if (reduce) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (!reduce) appear.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Ink.accent, Ink.accent2)),
                ),
            ) {
                Image(
                    painter = painterResource(special.artFor("dashboard")),
                    contentDescription = null,
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(0.78f)
                        .graphicsLayer {
                            alpha = appear.value
                            translationY = (1f - appear.value) * 28.dp.toPx()
                        },
                )
                val wordA = ((appear.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                        .graphicsLayer { alpha = wordA },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Mio 澪", color = Ink.onAccent, fontSize = 30.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "参数监控 · 纯只读", color = Ink.onAccent.copy(alpha = 0.85f),
                        fontSize = 13.sp, letterSpacing = 1.5.sp,
                    )
                    if (ver.isNotEmpty()) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            "v$ver · ${Build.MODEL}",
                            color = Ink.onAccent.copy(alpha = 0.6f), fontSize = 10.5.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }
        }

        if (showOnboarding) {
            OnboardingOverlay(
                pages = onboardPages,
                onFinish = {
                    mioPrefs.edit().putBoolean(KEY_ONBOARD_DONE, true).apply()
                    showOnboarding = false
                },
            )
        }
    }
}

/** 提权说明：解释顶栏徽章与 S / R 标记（BiliPai 弹层风格） */
@Composable
private fun PrivGuideSheet(
    serviceUp: Boolean,
    authorized: Boolean,
    onGoSettings: () -> Unit,
    rootAvailable: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("权限", color = Ink.tx2, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "澪来解释三档权限", color = Ink.tx, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SpecialFace("think_head", Modifier.size(84.dp))
        }
        Spacer(Modifier.height(18.dp))
        PrivGuideRow("—", "免提权", "电量 · CPU 频率 · 传感器等基础参数")
        Spacer(Modifier.height(12.dp))
        PrivGuideRow("S", "Shizuku", "温度热区 · 负载 · GPU 温度（无 Root 时的替代档）")
        Spacer(Modifier.height(12.dp))
        PrivGuideRow("R", "仅 Root", "GPU 频率 · cpu0 上限 · 含 Shizuku 全部能力")
        Spacer(Modifier.height(16.dp))
        Text(
            "卡片上的 S / R 标记表示该项需要的提权档位；未授权时对应数值置灰。",
            color = Ink.off, fontSize = 12.sp, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(20.dp))
        val btn = when {
            rootAvailable -> "Root 已激活 · 全参数可读"
            authorized -> "Shizuku 已激活 · 前往设置查看"
            serviceUp -> "服务运行中 · 立即去授权"
            else -> "前往设置页发起授权"
        }
        ProbeButton(
            text = btn,
            accent = !(rootAvailable || authorized),
            onClick = onGoSettings,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PrivGuideRow(tag: String, name: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MioChip(tag, color = Ink.accent, mono = true)
        Spacer(Modifier.width(10.dp))
        Text(name, color = Ink.tx, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(
            desc, color = Ink.tx2, fontSize = 12.5.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** 当前页面 → 立绘 key（特殊预设与澪默认共用同一套 key） */
private fun artKeyFor(s: Screen): String = when (s) {
    Screen.DASH -> "dashboard"
    Screen.CPU -> "cpu"
    Screen.GPU -> "gpu"
    Screen.BAT -> "battery"
    Screen.THERM -> "thermal"
    Screen.SENS -> "sensor"
    Screen.SET -> "settings"
    else -> ""
}
