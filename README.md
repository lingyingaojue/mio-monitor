<div align="center">

# Mio 澪

**实时读懂手机的 CPU、GPU、电池与温度。**

纯只读 · 不写系统节点 · 免 root 可用 · 读不到的数据如实显示

<br/>

![Version](https://img.shields.io/badge/version-v3.15-007AFF)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Miuix-4285F4?logo=jetpackcompose&logoColor=white)
![Readonly](https://img.shields.io/badge/%E5%8F%AA%E8%AF%BB-%E4%B8%8D%E5%86%99%E7%B3%BB%E7%BB%9F%E8%8A%82%E7%82%B9-orange)
![License](https://img.shields.io/badge/License-GPL--3.0-blue)

<img src="docs/screenshots/01-dashboard.png" width="320"/>

<sub>真机实拍 · 中兴天机 A41 Ultra（骁龙 8 Gen1 / Adreno 730）· 截图未作修饰</sub>

</div>

---

Mio 澪 是一款安卓硬件参数与性能监控应用。它把设备能够读到的硬件信息，尽可能完整、准确地呈现出来：CPU 每核频率与占用、GPU 负载与频率、电池电压电流功率与健康度、几十路热区温度与散热动作、内存与传感器。

**v3.15 Monster+（Root 专用）**：在实验功能新增 Monster+ 模式开关，Root 写入 `Settings.System.game_plus_mode_key`，回读校验、可恢复；仅 vivo/iQOO 支持，ADB 直写 / Shizuku 不会冒充。

**v3.14 Root 授权与读写完善**：自动识别 KernelSU Next / KernelSU / Magisk / APatch，支持多路径 su 探测与显式授权等待；设置页可一键检测 Root、打开 Root 管理器；Root 激活后所有读取优先走 Root，Toolbox 写入使用 Root 长超时并回读校验。**v3.13 温度页中文化 + 无数据隐藏**：热区/冷却设备/HAL 标签能翻则翻（CPU/GPU/NPU/ToF/Wi-Fi 等通用缩写保留），型号名保留原文；读不到的温度（null/0/负值/NaN）、无 cur/max 的冷却设备、无效 HAL 行、空外壳侧、空分组/网格全部自动隐藏，只留真实可读数据。**v3.12 温度页 ADB 全量热数据**：动态读取全部热区（本机 95 个 zone，过滤后约 80+ 个有效温度）、全部 64 个 cooling_device、每区 trip 阈值与 Thermal HAL 官方温度；CPU / GPU 卡片改成与「热缓解动作」同款折叠菜单，默认 Top-5、展开看全部（含 HAL 温度与降频阈值）；热缓解动作展开可看全部冷却设备；底部网格不再固定 84 格。**v3.11 触感强度修复 + 立绘四档**：触感改为「波形节奏 + 时长 + 振幅」三重区分（轻/中/重按住与点按都能明显区分，不再依赖 ROM 是否尊重振幅）；「显示角色立绘」开关改为与「界面质感」同款四选一——**关闭 / 小 / 中 / 大**（300/420/540dp，默认中=旧观感），旧开关 true→中、false→关闭自动迁移。**v3.10 设置收敛为「界面质感」**：把「液态玻璃 / 玻璃透明度 / 玻璃引擎 / 底部导航栏」四行合并为一个三选一——**关闭**（Mio 最初样式：实色卡片 + Mio 简版底栏）、**Mio 简版**（半透卡片 + Mio 简版底栏）、**全透**（全透玻璃卡片 + BiliPai 液态底栏）；旧四档/旧底栏设置启动时自动迁移，重启保持。**v3.9 全透卡片玻璃与弹窗可读性**：卡片改用 PrismalAGSL 的 `drawPrismalGlass` 直接绘制——去掉白色 specular/提亮，只保留真实 lens 折射（24/64dp + 55% RGB 色散）、黑色悬浮投影和 1px 白边；第四档透明度由「极高」改为「全透」（卡片 tint=0、blur=0，立绘直接透出）；删除人工红蓝描边；弹窗固定 0.92 不透明底色 + 窗口模糊，底部弹层保留 ≥0.72 填充，解决文字看不清。**v3.8 卡片玻璃接入 [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL)**：卡片改用库的 backdrop 液态玻璃；顶栏/dock/选中胶囊同步开启色散；API<33/LITE 保留 Haze 降级。**v3.7 玻璃质感对齐 OriginOS「高级材质」**：全面下调玻璃填充浓度、卡片模糊提到 32dp，并给 Haze 卡片加饱和度增艳（透彩色），四档透明度以「低」为 OriginOS 参考档、中/高/极高逐级更透；顶栏、卡片、dock、弹层同步生效。**v3.6 全局玻璃透明度**：外观新增低 / 中 / 高 / 极高四档，统一缩放顶栏、返回条、卡片、底部 dock（BiliPai 与 Mio 简版两条路径）与弹层的玻璃填充浓度，默认「中」与 v3.5 外观一致。**v3.5 OriginOS 实验调校**：把 ADB 实测可用的 4 项系统设置做成开关（触控拉满 / 强开全局插帧 / 全局高刷锁定 / 全局视效增强），默认全关、首次进入需确认风险，支持一键恢复默认与恢复原始值；写入通道自动选择（ADB 授权 / Root / Shizuku），每次都回读校验；system 类 key 需 Shizuku / Root。**v3.4 底部导航栏直接使用 BiliPai 原版组件**（vendor BiliPai v0.7.8 的 `FloatingBottomBar` 及依赖闭包，Mio 只做 tab 状态映射）。**v3.3** 顶栏/返回条接入 Miuix Backdrop 光学链（背板录制 + 增艳 + 实时模糊 + 边缘折射/色散 + Bloom 高光 + 悬浮投影）；**v3.2** 下调玻璃填充不透明度；**v3.1** 接入 Haze 实时背景模糊。BiliPai 源码搬运清单见 [design-system/VENDORED.md](design-system/VENDORED.md)。

**v3.0 界面体系重做**：整套 UI 换成 BiliPai 的 Miuix 视觉语言——5 个主 tab 的悬浮玻璃底栏、滚动感知的玻璃顶栏、大圆角分组卡片、Miuix 设置页，并新增**浅色 / 深色 / 跟随系统**三种主题。

---

## 三条基本原则

**只读监控。** 监控功能不写任何系统节点、不修改任何设置、不申请与监控无关的权限。另有 **OriginOS 实验调校**（v3.5，默认全部关闭）：只有你手动开启才写入固定白名单的少量系统设置，可随时关闭并一键恢复默认/原始值。

**免 root 可用。** 安装后即可读取电量、内存、传感器以及多数机型的 CPU 频率与占用。安装 Shizuku（非 root，一次性授权）后，热区温度、CPU 占用、充电器输入侧电压电流进一步开放。

**读不到的如实显示。** 无法读取的项显示「–」并说明原因与授权入口，不使用 0 代替。监控工具的价值建立在数据可信之上，这一条不作妥协。

---

## 角色「澪」

澪是应用的角色形象，作为人格化点缀融入界面，而非信息主体：7 张分页立绘固定立于内容区右侧，卡片为半透明材质，角色从卡片下自然透出；另有 5 种表情差分用于弹窗与空态，10 页新手引导由她依次说明权限边界与各页面内容，部分说明文字亦以她的口吻撰写。立绘均为透明底 PNG，并处理过边缘白边与裁切问题。

设置页把外观分成两组**相互独立**的选项：**配色**只换颜色，**角色**整组更换（立绘 + 表情 + 配色）。先挑角色、再用配色微调，两者不会互相覆盖；新手引导整篇也会跟随当前角色与配色变化。

> 角色美术素材属于第三方版权物，不随本仓库分发。自行准备同名图片放入 `app/src/main/res/drawable-nodpi/` 即可启用对应角色；素材缺失时该角色不会出现在设置里，已选中的也会自动回落默认角色。

---

## 界面

<div align="center">

| CPU | GPU |
|:---:|:---:|
| <img src="docs/screenshots/02-cpu.png" width="300"/> | <img src="docs/screenshots/03-gpu.png" width="300"/> |

| 电池 | 温度与散热 |
|:---:|:---:|
| <img src="docs/screenshots/04-battery.png" width="300"/> | <img src="docs/screenshots/05-thermal.png" width="300"/> |

| 设置 | |
|:---:|:---:|
| <img src="docs/screenshots/06-settings.png" width="300"/> | <sub>以上截图为 v2.0（旧 iOS 风格）留档；v3.0 Miuix 界面截图待真机更新</sub> |

</div>

---

## 数据覆盖

### CPU
每核当前频率与占用；依据 `cpufreq` 动态分簇并区分超大核、性能核、能效核（各有专属标识色）；总占用曲线；频率档位驻留（`time_in_state`，反映一段时间内 CPU 在各频率上的停留比例）；governor 与各簇频率上限；`/proc/loadavg`。

### GPU
占用曲线、频率（MHz）与核心温度。自动识别高通 Adreno、ARM Mali、联发科 GED 三类节点并逐级回退，适配不同平台时无需修改代码。

### 电池
电量、充放电状态、电压、电流、功率；充电器协商上限；健康度、设计容量与学习容量、循环次数、开路电压、截止电压、充入电量、充电限流档位；24 小时容量趋势；按当前功率估算的充满与续航时间。

功率口径经过多轮真机校准：**充电时优先采用适配器输入侧的 V×I**，而非电池侧，避免同一时刻两处数值不一致；充电类型判定不依赖单一标志位。

### 温度与散热
分别给出最高结温、平均温度与外壳（手感）温度——三者口径不同，结温必然高于外壳，混合统计会得到错误结论。此外提供距温度红线的余量、当前生效的热缓解动作（展开可看全部冷却设备）、按 CPU / GPU / NPU·内存·多媒体 / 射频·充电·外壳 / 其他分组的热区列表，每组默认 Top-5、点击展开看全部（CPU/GPU 展开后含 HAL 官方温度与 trip 降频阈值），以及随设备变化的全部热区网格。

部分机型的 thermalservice 中存在恒定值、哨兵值或根本不是温度的"僵尸热区"，它们会占据列表、个别还会把最高结温顶高。应用按非温度节点、哨兵值（内核以 −273 °C 表示传感器不存在）、以及**连续 45 次采样波动不超过 0.3 °C**（按显示精度判定，而非"逐位相同"）三类规则剔除，并在统计中一并排除。在测试机上显示热区由 81 个降至 37 个，留下的是真正在变化的读数。

### 内存、传感器与 Thermal HAL
内存与 Swap 明细；加速度、陀螺仪、磁力计、光线、距离等真实物理量；并可直接读取系统的 Thermal HAL，获得分类温度（CPU / GPU / 电池 / 外壳 / 电压 / 电流 / 电量 / NPU）与热状态等级。

---

## 局域网双模式

两部安卓设备安装同一应用，即可分别担任被监控端与监控端。

- **被监控端**：前台服务每秒采集数据，通过 mDNS 广播，在局域网内推送
- **监控端**：自动发现并连接，支持同时观察多台设备；每台设备展示 CPU 各核频率、结温、电量、HAL 分类温度、内存 / GPU / 充电等信息，可下钻至单设备详情页查看历史曲线
- **传输范围**：仅限局域网点对点，不经过任何外部服务器，无需账号

被监控端内置常驻通知、开机自启与 JobScheduler 兜底，并针对 OPPO、vivo、小米、华为、三星等品牌提供逐项的后台保活设置引导。

---

## 权限

| 档位 | 可读取范围 |
|---|---|
| **免提权**（默认） | 电量、电池温度与电压、内存、传感器、部分 GPU 占用、多数机型的 CPU 频率 |
| **Shizuku**（推荐，无需 root） | 热区温度、散热动作、CPU 占用、充电器输入侧电压电流、更多 sysfs 节点 |
| **Root** | 个别被 SELinux 限制的节点（例如部分机型的 Adreno GPU 频率） |

未授权时相关卡片显示空态并提供授权入口，不影响其余功能。

---

## 显示与交互

- **两种主题模式**：浅色 / 深色 / 跟随系统，设置页即时切换；状态栏与导航栏图标自动跟随明暗。
- **配色预设**：内置 6 套（晴空蓝 / 薄荷青 / 雾天青 / 暖奶米 / 浅草 / 紫藤）并各自派生深色版本，设置页即时切换。每套都按对比度取值——强调色压得住按钮文字，卡片与页面底保持可分辨的亮度差，次级文字不低于 4.5:1。
- **角色立绘四档（v3.11）**：设置 → 角色立绘，**关闭 / 小 / 中 / 大**（高度 300 / 420 / 540dp，默认「中」=旧 420dp 观感）；深色模式下自动降低不透明度并叠加页面底色渐变，避免影响卡片可读性。
- **液态玻璃（v3.4 双引擎）**：Android 13（API 33）及以上使用 **BiliPai 原版 FloatingBottomBar + Miuix Backdrop 液态玻璃**：底部导航栏直接复用 BiliPai 组件（实时折射 + 高光 + 拖动选择 + 重力高光），顶栏/整页返回条使用 Miuix Backdrop 光学链（背板录制 + 增艳 + 实时模糊 + 边缘折射/色散 + Bloom 高光 + 悬浮投影）；Android 12 / 11 使用 Haze 实时模糊，Android 8–11 自动降级为半透明 + 高光材质，不崩溃、布局一致。
  - **底部导航栏 = BiliPai 原码**：`FloatingBottomBar` 及其几何/拖拽/高光/触感依赖原样搬运（清单与唯一补丁见 [design-system/VENDORED.md](design-system/VENDORED.md)），Mio 只做 tab 状态映射；可左右拖动选中、按压回弹、倾斜手机时高光移动。
  - 覆盖全场景：底部 dock、顶栏、整页返回条、全部内容卡片、设置分组、图表卡、引导页卡片、弹层（提权说明 / 明暗模式 / 添加设备）。
  - 玻璃底下真的有内容：页面内容从顶栏/dock 下方实时穿过并被折射；外缘有 bloom 亮边与悬浮阴影。
  - 设置页三档：**完整**（最通透，全表面液态玻璃）/ **简约**（仅外壳玻璃、卡片更实更省电）/ **关闭**（实色）。默认完整。
  - **界面质感三档（v3.10）**：设置 → 界面质感，**关闭 / Mio 简版 / 全透**；关闭 = 实色卡片 + Mio 简版底栏，Mio 简版 = 半透卡片 + Mio 简版底栏，全透 = 全透玻璃卡片（tint=0、blur=0，保留 lens 折射边）+ BiliPai 液态底栏；旧「液态玻璃 / 玻璃透明度 / 底部导航栏」细项已移除，旧设置自动迁移。
  - 设置页「外观 → 玻璃引擎」如实显示当前渲染路径；「底部导航栏」可在 **BiliPai 原版 / Mio 简版** 之间切换（默认 BiliPai 原版）。
- **导航结构**：5 个主 tab（仪表盘 / CPU / GPU / 电池 / 温度）+ 顶栏齿轮进入设置；传感器、被监控、监控与设备详情为整页二级页，带玻璃返回条。
- **触感反馈（v3.11）**：底部 dock 切换、卡片与设置项按下均有震动，强度分**轻 / 中 / 重**三档；三档用波形节奏/时长/振幅三重区分，设置页按住按键可持续震动、松手即停，便于直观比较。
- **动效规范**：全局采用单一缓动曲线与统一时长（按下 130ms、松开 240ms、导航指示器 280ms、数值 320ms）。导航指示器平滑移动；卡片按下轻微收缩、阴影与明度同步下沉，抬起平滑恢复。
- **数值过渡**：CPU 占用、温度、内存等高频变化数值采用插值滚动，避免逐秒跳变。
- **无障碍**：跟随系统「关闭动画」设置，开启时所有动效直接切换。

---

## OriginOS 实验调校（v3.5，默认关闭）

设置 → **实验功能 → OriginOS 调校**。只针对 vivo / iQOO OriginOS 做了实测，其它设备自动保持只读。

| 开关 | 写入的 key（命名空间） | 说明 / 风险 |
|---|---|---|
| 触控拉满 | `game_memc_request_touch_rate`（global） | 提高触控采样率；功耗略增、长时间高采样对屏幕有压力 |
| 强开全局插帧 | `gamecube_frame_interpolation`（system） | 值格式 `1:1:0:<from>:<to>`；会牺牲原生分辨率，`to` 需匹配屏幕刷新率 |
| 全局高刷锁定 | `min_refresh_rate` + `peak_refresh_rate`（system） | 锁 120/144Hz；待机更费电、静态画面不降频 |
| 全局视效增强 | `gamecube_hawkeye_effective`（system） | 只改这一个 key，不动色彩模式 |

- **默认全部关闭**；首次进入必须确认风险。每项都有二次确认与风险文案。
- **写入通道**自动选择并回读校验：
  - **触控拉满**（global）可用 ADB 授权直写：`adb shell pm grant com.a41probe.monitor android.permission.WRITE_SECURE_SETTINGS`；
  - **插帧 / 高刷锁定 / 视觉增强**（system）在 Android 17 上会被 App 直写权限拒绝，需要授权 **Shizuku** 或 **Root**（页面内有一键请求 Shizuku）；
  - vivo 的 `settings put` 会静默丢弃；`content insert` 又不接受插帧这种带冒号的值。因此 Shizuku/Root 通道改用 `app_process` 以 shell 身份直调 SettingsProvider（`ToolboxShellWriter`），三类值都能写，写完仍回读校验。
- 页面不再展示已排除的 Monster+ / BOOST+ / 直驱供电 / 满亮度等无效或高风险项。
- **可回滚**：页面提供「全部恢复系统默认」（删除 key）与「恢复首次写入前的原始值」（有备份时）。
- **可选守护**：OriginOS 会定期清理这些 key；开启「保持生效」后前台服务每 5 秒检查并重写（常驻通知可见，可随时关闭）。默认关闭。
- 已实测不可用/不建议的功能（Monster+、BOOST+、强开直驱供电、满亮度）不提供，页面内写明原因。
- ⚠️ **卸载前请先「全部恢复默认」**，否则已写入的 key 会残留；也可用 adb 手动删除：`adb shell settings delete <global|system> <key>`。

---

## 数据导出

设置页可导出 CSV，分三段：**META**（时刻、机型、版本）、**RAW**（内核原始单位、零换算、附来源节点路径）、**RENDERED**（与界面一致的换算值及所属页面）。UTF-8 BOM 编码，全字段引号转义，写入设备「下载/Mio」目录并保留最近 5 份。

保留 RAW 段的目的，是便于他人核对口径而非仅接受结论。

---

## 安装

1. 从 [Releases](../../releases) 下载最新 APK；
2. 允许「安装未知来源应用」后直接安装，支持 **Android 8.0 及以上**（液态玻璃的实时模糊需 Android 12+；边缘折射/色散需 Android 13+，更低版本自动降级为半透明材质）；
3. 如需完整数据，可安装 [Shizuku](https://shizuku.rikka.app/) 并在应用内一键授权——并非必需。

首次启动提供 10 页使用引导。

---

## 构建

```bash
# 需 JDK 21 与 Android SDK（platform 37 / build-tools 37）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 源码基线

本项目基线为 **Mio 官方 Releases v2.0 源码包**（tag `v2.0` / `e35dfd3`），Mio 3.x 自定义提交叠加在其上。已逐文件校验源码包与 tag 树一致（84 个文件，0 差异）：

```powershell
powershell -ExecutionPolicy Bypass -File tools/verify-upstream-source.ps1
```

详见 [docs/UPSTREAM_SOURCE.md](docs/UPSTREAM_SOURCE.md)。

---

## 技术说明

| | |
|---|---|
| 语言 / UI | Kotlin 2.4.0 · Jetpack Compose（Miuix 0.9.4 + Material 3） |
| 构建 | AGP 9.3.2 · Gradle 9.5.0 · JDK 21 |
| 支持范围 | minSdk 26（Android 8.0）· targetSdk 37 |
| 设计系统 | BiliPai design-tokens / design-system（GPL-3.0，源码搬运，见 `design-system/VENDORED.md`） |
| 架构 | 单 Activity + Compose；ViewModel 配合细粒度 StateFlow；硬件读取采用分层回退的 Reader |
| 网络 | 仅局域网：mDNS（NSD）服务发现 + TCP 长连接 |

**实现要点**

- **统一采集口径**：本机界面与被监控端调用同一个 `SnapshotCollector`，两端数据与换算完全一致。
- **批量提权读取**：单条 shell 命令取回热区、频率、loadavg、散热状态等，每帧跨进程往返由 11 次以上降至 1 次。
- **不写死机型**：SoC、GPU、热区、电源节点均按平台探测并逐级回退。
- **细粒度订阅**：各卡片仅订阅自身所需数据，数值未变不触发重组；滚动上下文使用动态 `compositionLocal`，避免滚动时整页重组。
- **启动与回前台**：先输出电量与内存的轻快照，再补充需授权的数据；回前台只合并不回退，不会短暂显示「需要授权」。

---

## 已知限制

- **只读**：需要写权限才能获取的数据无法读取。
- **GPU 频率**：个别机型（如中兴天机 A41 Ultra）对应用与 shell 均拒绝该节点访问，需 Root。
- **触感强度**：少数机型的厂商 HAL 不提供振幅接口；v3.11 起三档始终通过时长、脉冲数与波形占空比区分，不依赖振幅是否被 ROM 尊重。
- **后台保活**：被监控端已内置多重保活与厂商设置引导，但仍需在系统层面放行。
- **语言**：目前仅提供简体中文，文案尚未抽取为字符串资源。

---

## 隐私

本机监控完全离线。双模式仅在局域网内向用户自有设备传输数据，不经过服务器，不采集也不上传任何个人信息。CSV 仅写入本机存储。

---

## 许可

[GPL-3.0](LICENSE)。Mio 2.0 及更早版本以 MIT 发布；v3.0 起因集成搬运自
[BiliPai](https://github.com/jay3-yy/BiliPai)（GPL-3.0）的设计系统，整体以 GPL-3.0 分发。
原 MIT 声明、上游致谢与第三方组件许可见 [NOTICE](NOTICE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

<div align="center"><sub>Mio 澪 · 只读硬件监控</sub></div>
