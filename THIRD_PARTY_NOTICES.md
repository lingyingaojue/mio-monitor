# 第三方开源声明（THIRD_PARTY_NOTICES）

Mio 3.0 使用了以下第三方开源组件。完整许可文本可通过列出的坐标/仓库获取。

## 直接搬运源码（GPL-3.0）

| 组件 | 版本/来源 | 许可 | 说明 |
|---|---|---|---|
| BiliPai design-tokens / design-system | jay3-yy/BiliPai v0.7.8（551），2026-11 快照 | GPL-3.0 | 见 `design-system/VENDORED.md`；文件头保留原版权与 SPDX |
| BiliPai 原版底部导航栏（FloatingBottomBar 及其依赖闭包） | jay3-yy/BiliPai v0.7.8（551），2026-11 快照 | GPL-3.0 | Mio 3.4 起直接使用 BiliPai 原版底部导航栏；文件清单/抽取/补丁见 `design-system/VENDORED.md` |

## 二进制依赖

| 组件 | 坐标 | 许可 |
|---|---|---|
| Miuix（miuix-ui / preference / blur / shader / squircle / icons） | `top.yukonga.miuix.kmp:*:0.9.4` | Apache-2.0 |
| Haze（core / blur / blur-materials） | `dev.chrisbanes.haze:*:2.0.1` | Apache-2.0 |
| Jetpack Compose（UI / Foundation / Material3 / Animation） | `androidx.compose.*`（Compose BOM beta 2026.09.00；material3 1.5.0-alpha29） | Apache-2.0 |
| AndroidX Activity / Lifecycle / Core / Annotation | `androidx.*` | Apache-2.0 |
| NavigationEvent | `androidx.navigationevent:navigationevent-compose:1.1.2` | Apache-2.0 |
| AndroidX Material Icons Extended | `androidx.compose.material:material-icons-extended` | Apache-2.0 |
| Kotlin / kotlinx.coroutines | `org.jetbrains.kotlin*`、`org.jetbrains.kotlinx:kotlinx-coroutines-android` | Apache-2.0 |
| Shizuku API / Provider | `dev.rikka.shizuku:api:12.2.0`、`dev.rikka.shizuku:provider:12.2.0` | Apache-2.0 |

## 素材

- 角色「澪」及特殊预设（如砂狼シロコ）的立绘、表情图片版权归各原作者所有，
  不随本仓库分发；仅在本地放入同名资源时由应用加载。
