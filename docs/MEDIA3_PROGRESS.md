# Media3 冷启动改造进度

本文件是迁移项目唯一动态状态记录。每个迁移代码提交必须在同一提交中同步更新本文件。

## 当前状态

- 分支：`feat/media3-cold-resume`
- 当前方案：方案 A
- 当前阶段：Phase 0 - 基线与依赖审计
- 状态：阻塞（Media3 1.9.4 minSdk 与构建环境）
- Media3 目标版本：1.9.4

固定约束：

- React Native 0.73.11
- AGP 8.6.1
- Gradle 8.8
- Kotlin 1.9.24
- compileSdk 35
- 不在本分支升级 Android 构建链

## 当前阶段目标

Phase 0 必须输出：

- RN、RN Gradle Plugin、AGP、Gradle、Kotlin、JDK、Android SDK 依赖链
- TrackPlayer、react-native-video、本地媒体元数据模块与应用直接依赖的 Media3 来源
- `dependencyInsight`、完整依赖树与最终解析版本
- merged manifest 与媒体 Service、Receiver、通知、Widget 所有权
- 当前播放创建、连接、销毁、后台、进程死亡和恢复链路
- 旧引擎 Debug、minified Release 与关键运行行为基线
- 方案 A 冲突检查结论

通过 Phase 0 前不得提交 Media3 服务实现。

## 提交记录格式

每个迁移代码提交追加一条记录，并填写：

- 日期与提交
- 阶段与状态
- 改动范围和文件
- 依赖变化及证据
- 关联模块和可能影响
- 代码检查结果
- 测试命令、环境和结果
- 阻塞、冲突或未验证项
- 下一步

禁止使用“已测试”“兼容”或“通过”等结论而不附具体命令或运行证据。

## 变更记录

### 2026-07-19 - Phase 0 基线与依赖审计（阻塞）

- 阶段：Phase 0 - 基线与依赖审计
- 状态：阻塞；没有引入 Media3 播放实现、Service、Bridge 或依赖变更
- 改动范围：新增 `docs/MEDIA3_PHASE0_BASELINE.md`，本文件更新为实际审计状态
- 依赖发现：TrackPlayer fork 请求 Media3 `1.8.0`，本地媒体元数据模块请求 `1.5.1`，react-native-video 6.17.0 请求 `1.4.1`（包括 `common`、`exoplayer`、`session` 和其余播放器模块）。应用没有直接声明 Media3。由于 Gradle 无法启动，未取得此分支的 `dependencyInsight` 与最终解析路径，不能声称已统一版本。
- 工具链与 AAR：RN `0.73.11`、RN Gradle Plugin `0.73.5`、AGP `8.6.1`、Gradle `8.8`、Kotlin `1.9.24`、compileSdk `35`、targetSdk `29`、minSdk `21`。内存读取的 Media3 `1.9.4` AAR 均要求 `minCompileSdk=35`，与 compileSdk 一致；官方 Media3 1.9.0 发布说明将 minSdk 提升至 `23`，故 `1.9.4` 与当前 minSdk `21` 冲突。检查的 AAR 未含 Kotlin module metadata，未发现该 AAR 层 Kotlin metadata 冲突；最终依赖图验证仍未执行。远端 Release 工作流使用 Microsoft JDK `21.0.11` 并成功构建旧引擎，证明该流水线组合可构建当前依赖，但不消除 minSdk 冲突。
- 关联模块和可能影响：`react-native-track-player` 的 `MusicService`、legacy `MediaSessionCompat`、`MediaButtonReceiver`、ExoPlayer、react-native-video、本地媒体元数据、通知、锁屏、蓝牙、Widget、外部文件、Deep Link 和动态包名。若把 minSdk 提升至 23，API 21/22 用户将无法安装；若强制混合版本，可能破坏现有 native module 二进制行为。
- Manifest/生命周期检查：静态源码仅见 TrackPlayer 的一个 `MusicService` 和一个 `MediaButtonReceiver`；应用 Widget 使用动态 BuildConfig action，外部文件/Deep Link 由 MainActivity 接收。由于 merged manifest 不能生成，最终 APK 是否存在合并重复不能确认。现有 Widget 通过 RN event emitter 控制，RN 不运行时不能保证可控；Service `START_NOT_STICKY` 且无队列/位置快照，进程死亡/重启恢复未实现。
- 测试与结果：本地 `cd android; .\gradlew.bat -version` 失败，输出 `JAVA_HOME is not set and no 'java' command could be found in your PATH.`；`java -version` 失败；`ANDROID_HOME`/`ANDROID_SDK_ROOT` 未设置且常用本机 SDK 路径不存在。远端 [GitHub Actions run 29683410711](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29683410711) 在提交 `99019f4` 使用 Microsoft JDK `21.0.11` 成功运行签名 `assembleRelease`：`app:minifyReleaseWithR8` 完成、`BUILD SUCCESSFUL in 7m 38s`、五个 APK 的默认包名与证书 SHA-256 校验通过，artifact ID `8441382627` 已上传。未执行 dependencyInsight、merged manifest、Debug、单元、instrumentation 或 `localhost:5555` 设备测试，不能将其标记为通过。
- 阻塞：1) Media3 `1.9.4` 要求 minSdk 23，项目固定 minSdk 21；2) 当前会话没有 JDK/Android SDK，无法启动 Gradle。两项均禁止开始 Phase 1。
- 下一步：由产品选择“提升 minSdk 至 23”或“保持 minSdk 21 并修订目标 Media3 系列”；随后提供 JDK 17 和 Android SDK 35，重新从 Phase 0 运行完整依赖、merged manifest、构建和设备验证。禁止强制版本、抑制错误或并存两个播放 owner。

### 2026-07-19 - 文档收敛

- 阶段：规划
- 状态：完成
- 改动范围：收敛 Agent 入口、Media3 唯一规范和进度记录
- 依赖变化：无
- 关联模块：仅文档
- 可能影响：后续 Agent 以 `MEDIA3_DESIGN.md` 为唯一规范，不再从多份重复文档自行组合方案
- 测试：文档结构检查；未执行 Gradle 或运行测试
- 阻塞：无
- 下一步：执行 Phase 0 基线与依赖审计

## 当前阻塞

- **Media3 1.9.4 minSdk 冲突**：Media3 1.9.0+ 最低 API 为 23，而项目 `minSdkVersion` 为 21；直接迁移会停止 API 21/22 安装支持。
- **本地构建环境缺失**：当前会话没有 `JAVA_HOME`/`java`，也没有可发现的 Android SDK 环境。本地 Gradle 不能启动，故最终依赖图、merged manifest、Debug 和测试均未验证；远端签名 minified Release/R8 已通过，但不替代这些检查。
