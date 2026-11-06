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
