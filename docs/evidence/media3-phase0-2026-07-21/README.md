# Media3 Phase 0 可复核证据（2026-07-21）

本目录保存 PR #1 二次评审所要求的原始输出，不包含 APK 二进制。APK 由 GitHub Actions artifact 保存；本目录以 SHA-256、`aapt`、`apksigner`、merged Manifest、Gradle 和 ADB 输出锁定被测对象与结果。

后续本机取证统一使用 [`scripts/media3-phase0-capture.ps1`](../../../scripts/media3-phase0-capture.ps1)。它在输出前硬性拒绝脏工作树，并写入 HEAD、工具版本、完整命令和实际构建身份；旧目录保留为当时评审的历史证据。

## 目录和来源

| 目录 | 范围 | 来源 |
| --- | --- | --- |
| `release-default` | 默认包名的 Release classpath、三项 Media3 insight、merged Manifest 与 merger report | 本机 JDK 17.0.19.10 / Android SDK 35 |
| `api23-release-default` | 默认包名签名 Release 的 API 23 设备、标准安装、冷启动、logcat 和 UI dump | Actions run `29688002586`、artifact `8442770896`、commit `3c069951b122cdf44b0bdd14b20f4d65544c19cb` |
| `package-com.tencent.qqmusic-release` | `com.tencent.qqmusic` 的签名 Release、签名、merged Manifest、首次/覆盖安装与 API 23 启动 | Actions run `29795317191`、artifact `8481864959`、commit `fbddbe61bddf2deb6c58167572aee66fcc4b4bb6` |
| `package-com.lxwalnut.music.phase0audit-debug` | 第二个自定义包名的 Debug APK 与 merged Manifest | 本机 JDK 17.0.19.10 / Android SDK 35 |

`api23-release-default/source-diff-3c06995-to-fbddbe6.txt` 证明默认包名 Release artifact 的 source commit 到当时 PR head 之间只有两份审计文档变化，没有运行时代码变化。

## 已验证的边界

| 包名 | 构建 | 最终 APK/Manifest 身份 | API 23 结果 |
| --- | --- | --- | --- |
| `com.lxwalnut.music.mobile` | 签名 minified Release | `*.provider`、`*.widget.*`、`lxmusic` | `pm install -r`（不带 `-t`）成功；冷启动 406 ms，无 `AndroidRuntime` 致命异常，UI dump 已保存 |
| `com.tencent.qqmusic` | 签名 minified Release | `com.tencent.qqmusic.provider`、`com.tencent.qqmusic.widget.*`、`lxmusic` | 标准首次安装和 `pm install -r` 覆盖安装均成功；冷启动 514 ms，无致命异常 |
| `com.lxwalnut.music.phase0audit` | Debug | `com.lxwalnut.music.phase0audit.provider`、`com.lxwalnut.music.phase0audit.widget.*`、`lxphaseaudit` | 本地 x86_64 Debug 构建成功；此项不作为完整应用运行验证 |

各 Manifest 都同时保留 `androidx.core.content.FileProvider` 和 `com.RNFetchBlob.Utils.FileProvider`，且两者使用对应包名的 `.provider`。这再次确认问题是随动态包名变化而保留的双 owner 冲突，而非只发生在默认包名。

## 仍然不通过的门槛

- Debug 与 Release 最终 Media3 仍为 `1.8.0`，并非目标 `1.9.4`。
- 两个不同 FileProvider class 仍共用同一 `<applicationId>.provider`；安装和启动成功不构成 owner 冲突已解决。
- 播放、通知、锁屏、蓝牙、Widget 冷启动、外部文件分享与完整多 API/厂商运行矩阵仍未验证。

因此本证据只补齐 Phase 0 审计可复核性和动态包名构建/安装边界；PR 必须保持 Draft，不能进入 Phase 1。
