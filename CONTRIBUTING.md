# 贡献指南

感谢你愿意为 Mio 澪（个人改造版）提交 Issue 或代码。

## 项目定位

这是基于上游 [Mio v2.0](https://github.com/PRTS168/mio-monitor) 的个人改造分支，重点是 Miuix / BiliPai 界面、Root 与 Shizuku 支持，以及 OriginOS 实验功能。它不是原作者官方仓库，也不是完整的商业产品维护流程。

## 提交 Issue 前

1. 先搜索已有 Issue，避免重复；
2. 阅读 [README](README.md) 与 [开发文档](docs/开发文档.md)；
3. 确认问题在最新 [Release](https://github.com/lingyingaojue/mio-monitor/releases/latest) 上仍可复现；
4. 涉及 Root / Monster+ / OriginOS 写入时，请确认你已理解风险并备份数据。

## 提交 Issue 时请提供

- 手机品牌与具体型号；
- Android / OriginOS 版本；
- Mio 版本号（设置页最底部）；
- 提权方式：免提权 / Shizuku / Root（Root 请说明 KernelSU Next、KernelSU、Magisk 或 APatch）；
- 复现步骤、实际结果、期望结果；
- 相关日志、截图或录屏（避免包含个人隐私）。

## 提交 PR

- 从 `master` 切出分支，修改范围尽量单一；
- 提交前运行：

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
```

- 涉及 UI 的改动请附前后截图或录屏；
- 涉及 Root / 系统设置的写入必须说明写入的 key、回读校验与恢复方式；
- 不要在代码或日志中提交 token、密钥、个人设备信息；
- PR 描述使用 `.github/PULL_REQUEST_TEMPLATE.md` 的清单。

## 代码约定

- Kotlin / Jetpack Compose，遵循仓库现有结构；
- 新的设置 key 必须加入 `data/toolbox/OriginSettings.kt` 的固定白名单，不允许任意 key；
- 所有系统写入都必须有备份、回读校验和失败提示；
- 涉及监控计算逻辑的改动需要补充 JVM 单元测试。

## 翻译与文档

- README 提供简体中文 / English / 日本語 三种版本：
  `README.md`、`README.en.md`、`README.ja.md`；
- 修改功能或安装方式时请同步更新三份 README；
- 允许直接提交翻译修正 PR，标题可加 `translation` 标签。

## 行为准则

请保持技术讨论就事论事，尊重不同机型、系统版本和 Root 方案的使用者。发布攻击性、隐私泄露或破解他人设备的内容会被拒绝。
