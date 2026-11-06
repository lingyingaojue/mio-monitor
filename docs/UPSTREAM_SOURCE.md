# 源码基线（UPSTREAM SOURCE）

本项目的代码基线是 **Mio 澪 v2.0 官方 Release 源码包**，在其之上叠加 Mio 3.x 自定义提交。

| 项目 | 值 |
|---|---|
| 上游仓库 | `https://github.com/PRTS168/mio-monitor` |
| Release | `v2.0` · Mio 澪 v2.0 · 温度与配色重做（2026-09-28） |
| Release 源码包 | `https://github.com/PRTS168/mio-monitor/archive/refs/tags/v2.0.zip` |
| Tag 指向提交 | `e35dfd3312b1592e59d1bc2597b9cde43e32e649` |
| 源码包 SHA-256 | `365511B878F294FFEF2DAFF872FC3446A09FE03E026C068D6A3C9D2FB7B5494B` |
| 源码包文件数 | 84（与 tag 树逐文件一致） |
| 本地基线提交 | `e35dfd3`（`v2.0` tag、`origin/master` 同提交） |
| 自定义增量 | `e35dfd3..HEAD` 共 16 个提交（截至 3.13） |

## 校验方式

2026-11-07 已执行完整校验：

1. 下载官方 Release 的 `Source code (zip)`，解压；
2. 用 `git -c core.autocrlf=false archive v2.0` 导出本地 tag 树（关闭 CRLF 转换，保持原始 blob）；
3. 对两棵树 84 个文件逐一比较 SHA-256；
4. 结果：**only-in-release=0，only-in-base=0，content-diff=0**。

也就是说，当前仓库的 v2.0 基线与官方 Release 源码包**逐文件一致**，之前的源码并未下错；GitHub 源码包是 LF 换行，而 Windows 工作区因 `core.autocrlf=true` 可能是 CRLF，内容层面相同。

## 复现

```powershell
# 在仓库根目录执行；默认 Tag=v2.0，自动从 origin 推导仓库地址
powershell -ExecutionPolicy Bypass -File tools/verify-upstream-source.ps1
```

脚本会下载源码包、打印 SHA-256、与本地对应 tag 做逐文件比较；完全一致退出码 0，有差异退出码 1。

## 上游升级流程

当上游发布新的 Release（如 v3.0）：

1. `powershell -ExecutionPolicy Bypass -File tools/verify-upstream-source.ps1 -Tag v3.0` 校验源码包与 tag；
2. 若需要以新版本为基线：从新 tag 建临时分支，将 `e35dfd3..HEAD` 的 Mio 自定义提交 rebase 到新 tag；
3. 解决冲突时保留 Mio 自定义功能，同时接受上游未改过的修复；
4. 重新构建、跑单测与 lint，验证温度/玻璃/触控/搜索等自定义模块；
5. 更新本文件中的 Release、commit、SHA-256 与增量提交数。

## 说明

- 只核对 Mio 本体；`design-system/`、`design-tokens/` 为 BiliPai GPL-3.0 源码搬运，版本与校验见 `design-system/VENDORED.md`。
- 不在本仓库提交 Release 的 APK 资产；`dist/` 为本地构建产物，已被 `.gitignore` 忽略。
