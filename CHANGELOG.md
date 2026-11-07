# 更新日志

## v3.15.0

- 新增 **Monster+ 模式（Root 专用）**：写入 `Settings.System.game_plus_mode_key`，写入后回读校验，可关闭并恢复默认；
- Root 专用写入通道：ADB 直写 / Shizuku 不会冒充 Root 执行 Monster+；
- 实验功能页增加 Root 状态提示与风险确认；
- 修复 Monster+ 卡片状态文案重复；
- README 重做，增加下载、截图、权限、构建与 Release 说明。

## v3.14.0

- RootBridge 支持 **KernelSU Next / KernelSU / Magisk / APatch** 管理器检测；
- 支持 `su`、`/system/bin/su`、`/debug_ramdisk/su`、`/data/adb/ksu/bin/su` 等多路径探测；
- 设置页支持“检测并授权 Root”、打开 Root 管理器，区分已激活 / 待授权 / 已拒绝 / 未检测到；
- Root 写入使用 8s 超时（覆盖 `app_process` 冷启动），写入后回读校验；
- 修复 Root 管理器包可见性（Manifest `<queries>`）。

## v3.13.x

- 温度页标签中文化：CPU / GPU / NPU / 外壳 / 冷却设备 / HAL 等；
- 无数据自动隐藏：null / 0 / 负值 / NaN 温度、无效冷却设备、空分组与空网格不再占位；
- 热区与冷却设备分组显示，CPU / GPU 卡片改为折叠菜单。

## v3.12

- 温度页读取全部热区、全部冷却设备、trip 阈值与 Thermal HAL 数据；
- CPU / GPU 温度卡改为与「热缓解动作」同款折叠菜单；
- 底部网格按可读热区动态展示。

## v3.11

- 触感反馈改为波形 + 时长 + 振幅三重区分；
- 「显示角色立绘」改为关闭 / 小 / 中 / 大四档，并自动迁移旧设置。

## v3.10

- 设置页把液态玻璃、玻璃透明度、玻璃引擎、底部导航栏收敛为「界面质感」三档；
- 旧四档设置自动迁移，重启保持。

## v3.5 – v3.9

- OriginOS 实验调校：触控拉满、强开全局插帧、全局高刷锁定、全局视效增强；
- 写入通道自动选择 ADB 授权 / Root / Shizuku，写后回读校验；
- 接入 Haze / PrismalAGSL 液态玻璃，卡片支持全透模式与弹窗可读性优化。

## v3.0 – v3.4

- 基于 BiliPai / Miuix 全量重做 UI：五 tab 玻璃底栏、玻璃顶栏、分组卡片、Miuix 设置页；
- 接入 Miuix Backdrop 光学链与 BiliPai 原版 `FloatingBottomBar`；
- 支持浅色 / 深色 / 跟随系统主题、角色与配色独立设置。

更早版本请查看仓库提交历史与 [开发文档](docs/开发文档.md)。
