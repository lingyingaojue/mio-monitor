# 搬运说明（VENDORED）

## 来源

- 仓库：`jay3-yy/BiliPai`（原生、纯净、可扩展的第三方 BiliBili Android 客户端）
- 版本：v0.7.8（versionCode 551），2026-11-04 快照
- 协议：GNU General Public License v3.0（GPL-3.0）
- 搬运范围：
  - `design-tokens/`：BiliPai `design-tokens` 模块全部源码（7 个 Kotlin 文件）+ 构建脚本
  - `design-system/`：BiliPai `design-system` 模块 `src/main/**` 全部文件（201 个 Kotlin 文件、
    `res/values*`、`assets/licenses`）+ 构建脚本
- 未搬运：`design-system/src/test`（测试，省略以减小构建面）、BiliPai 其余业务模块。

## 本地改动（相对原仓库）

1. `design-tokens` / `design-system` 构建脚本：`compileSdk` 保持 37、Java 21、minSdk 26 不变；
   移除 `cupertino` 依赖（模块内无任何 import，仅为 BiliPai app 层需要）。
2. `design-system` 构建脚本：移除未搬运测试的 JUnit5 声明；其余依赖与版本保持原样
   （Compose BOM `2026.09.00-beta`、material3 `1.5.0-alpha29`、Miuix、haze、navigationevent 等）。
3. 版本目录 `gradle/libs.versions.toml`：`miuix` 由 BiliPai 私有快照
   `0.9.4-153920c7-SNAPSHOT`（GitHub Packages，需凭据）改为 Maven Central 公开稳定版
   `0.9.4`；其 `Colors` API 与快照为同一份文件，`design-system` 使用的颜色角色均存在。
4. 源码文件本身未做逻辑修改；文件头版权/SPDX 声明保持原样。唯一例外见下条。
5. `core/ui/components/AppHorizontalPager.kt`：Miuix `pagerGestureOverride` 在稳定的 0.9.4 中
   没有 `flingBehavior` 参数（BiliPai 私有快照才有），已删除该实参；甩动行为仍由
   `HorizontalPager(flingBehavior = resolvedFling)` 提供，视觉与交互一致。

## 许可

搬运代码为 GPL-3.0；Mio 自 3.0.0 起整体按 GPL-3.0 分发（见仓库根 `LICENSE` / `NOTICE` /
`THIRD_PARTY_NOTICES.md`）。
## 追加搬运：BiliPai 原版底部导航栏（Mio 3.4）

来源与版本同 design-system（`jay3-yy/BiliPai` v0.7.8，2026-11-04 快照）。
目的：Mio 3.4 起底部导航栏**直接使用 BiliPai 原版 `FloatingBottomBar`**，不再自研视觉。
落点：app 模块 `app/src/main/java/com/android/purebilibili/...`（保留原包名）。

### 原样搬运（未改动逻辑）

| 位置 | 文件 |
|---|---|
| `feature/home/components/` | `FloatingBottomBar.kt`、`FloatingBottomBarGeometry.kt`、`FloatingDockChrome.kt`、`GlassGravitySensor.kt`、`LiquidDockHapticPolicy.kt`、`LiquidGlassTuning.kt`、`LiquidGlassAdaptiveReadability.kt`、`LiquidGlassReadabilitySampling.kt`、`SideBarMotionSpec.kt` |
| `feature/home/components/liquid/` | `CombinedBackdrop.kt`、`InnerShadow.kt`、`Lens.kt`、`StableBackdrop.kt`、`Vibrancy.kt` |
| `feature/home/components/miuix/` | `DampedDragAnimation.kt`、`DragGestureInspector.kt`、`InteractiveHighlight.kt`、`InteractiveHighlightMotionSpec.kt`、`InteractiveHighlightPalette.kt` |
| `core/ui/` | `DecorativeAnimationGate.kt` |
| `core/store/` | `LiquidGlassReadabilityMode.kt` |

### 抽取式搬运（只保留 BiliPai 原声明/函数体，供 vendored 文件使用）

| 文件 | 来源 | 说明 |
|---|---|---|
| `feature/home/components/BiliPaiFloatingBottomBarGlue.kt` | 从 `BottomBar.kt`、`BottomBarLiquidSegmentedControl.kt`、`ProgressiveTopChrome.kt` 抽取 | `resolveSharedBottomBarCapsuleShape`、`resolveBottomBarDarkTheme`、`resolveSegmentedControlIndicatorHeightDp`、`BILIPAI_PROGRESSIVE_TOP_BLUR_START_FRACTION`；函数体未改 |
| `core/store/LiquidGlassTypes.kt` | 从 `SettingsManager.kt` 抽取 | `LiquidGlassStyle/Mode/AdvancedSettings` + normalize/resolve 纯函数；避免拖入 9000+ 行设置管理 |
| `core/util/Haptics.kt` | 从 `ModifierExt.kt` 抽取 | `HapticType` + `rememberHapticFeedback`；函数体未改 |

### 本地必要补丁（与 BiliPai 原码的唯一差异）

1. `core/ui/blur/UnifiedBlur.kt`：BiliPai 的 `LocalRuntimeVisualGuard`（运行时掉帧守卫）依赖 app 层
   性能追踪链（`RuntimeVisualGuardTracker` 等未搬运），补丁为直接使用调用点传入的
   `motionTier`/`forceLowBudget`（等价于守卫未触发）。
2. `core/ui/blur/RecoverableVisualEffects.kt`（新增精简文件）：只保留 FloatingBottomBar 闭包用到的
   `recoverableBlurEnabled`（固定 true，与 BiliPai 未注册 gate 时行为一致）与
   `shouldAllowRenderEffectBackedHazeEffect`（SDK>=31，逐字保留）。
3. `feature/home/components/FloatingBottomBar.kt`（v3.11）：把内部 `rememberHapticFeedback(NAVIGATION)`
   替换为 Mio 的 `rememberHaptics()`，让设置页「触感强度」对 dock 的点击/拖拽刻度生效；
   其余 FloatingBottomBar 逻辑未改，BiliPai 原 `core/util/Haptics.kt` 保留但不再被 dock 使用。

### 许可

以上文件均为 GPL-3.0，Mio 整体 GPL-3.0 分发（见 `LICENSE` / `NOTICE` / `THIRD_PARTY_NOTICES.md`）。
