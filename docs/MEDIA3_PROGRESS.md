# Media3 冷启动改造进度

本文件是迁移项目唯一动态状态记录。每个迁移代码提交必须在同一提交中同步更新本文件。

## 当前状态

- 分支：`feat/media3-cold-resume`
- 当前方案：方案 A
- 当前阶段：Phase 0 - 基线与依赖审计
- 状态：阻塞（已完成 CI 基线；等待处置重复 Provider owner 与目标 1.9.4 依赖审查）
- Media3 目标版本：1.9.4

固定约束：

- React Native 0.73.11
- AGP 8.6.1
- Gradle 8.8
- Kotlin 1.9.24
- minSdk 23（2026-07-19 经用户批准；停止支持 API 21/22）
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

### 2026-07-20 - Phase 0 本机 SDK 与 API 35 设备复核

- 阶段：Phase 0 - 基线与依赖审计
- 状态：阻塞；仅增加本机与设备取证，没有修改播放器、Manifest、Provider 或 Media3 依赖
- 工具链与构建：在用户明确同意 Android SDK 许可证后，安装 Command-line Tools 22.0、Platform Tools 37.0.0、Platform 35 和 Build Tools 35.0.0。本机 JDK `17.0.19` 运行 `:app:dependencies --configuration debugRuntimeClasspath`、`:app:dependencyInsight --dependency androidx.media3:media3-exoplayer`、`:app:processDebugMainManifest`、`:app:assembleDebug`、`:app:testDebugUnitTest`，`BUILD SUCCESSFUL in 3m`（459 tasks）。最终 Media3 仍为 `1.8.0`；TrackPlayer `1.8.0`、本地元数据 `1.5.1 -> 1.8.0`、Video `1.4.1 -> 1.8.0`
- Manifest/设备证据：merged Manifest 是情况二：`androidx.core.content.FileProvider` 和 `com.RNFetchBlob.Utils.FileProvider` 为不同 class、均使用 `com.lxwalnut.music.mobile.provider`。当前工作区 Debug universal APK 在 `localhost:5555` 的 Samsung SM-S9210（API 35）首次与覆盖安装均成功；`dumpsys package` 同时列出两 class，但 authority 映射只指向项目 Provider。项目 paths 仅 files/cache，rn-fetch-blob 原 paths 包含 external；因此 rn-fetch-blob `actionViewIntent` 的外部文件分享路径存在实际 owner 退化风险
- 运行验证：MainActivity 冷启动成功（6754 ms）且进程存活；Debug 变体无法连接 Metro `localhost:8081`，故没有把 JS/UI/播放、通知、锁屏、蓝牙、Widget 冷启动或外部入口标记为通过。仓库静态源码未调用 rn-fetch-blob `actionViewIntent`，但该库公开 API 仍不能在未确认调用方前直接删除
- 阻塞：1) 目标 Media3 `1.9.4` 尚无原生模块兼容证据；2) Provider authority 不满足单一 owner 验收，必须先决定保留 rn-fetch-blob 外部查看能力时的独立 authority 或明确移除该功能；3) 完整运行基线需要可运行 JS bundle。不得通过强制依赖、隐藏依赖声明或保留重复 authority 绕过
- 下一步：由负责人确认 Provider 策略和 rn-fetch-blob `actionViewIntent` 的产品需求；完成独立 Manifest 变更及文件分享回归后，才重新评估 Phase 1

### 2026-07-19 - Phase 0 CI 基线完成并发现 Manifest owner 冲突

- 阶段：Phase 0 - 基线与依赖审计
- 状态：阻塞；没有引入 Media3 播放实现、Service、Bridge 或 Media3 依赖变更
- 改动范围：`docs/MEDIA3_PHASE0_BASELINE.md` 收敛 CI 依赖图、merged Manifest、Release/R8、设备预检与生命周期证据；移除一次性 `.github/workflows/media3-phase0-audit.yml`，避免后续文档提交重复触发分支专用重型审计。审计运行和 artifact 保留在 GitHub Actions，不发布 APK、不改播放代码
- 依赖变化及证据：[run 29688646006](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29688646006) 在提交 `4933075` 成功运行 `:app:dependencies --configuration debugRuntimeClasspath`、九项 `dependencyInsight`、`:app:processDebugMainManifest`、`:app:assembleDebug` 与 `:app:testDebugUnitTest`。TrackPlayer 请求 `1.8.0`、本地元数据请求 `1.5.1`、react-native-video 请求 `1.4.1`；Gradle 将当前 Debug 图中全部 Media3 模块解析为 `1.8.0`，不是迁移目标 `1.9.4`
- Manifest/模块影响：merged Debug Manifest 仅有一个 TrackPlayer `MusicService`、一个 `MediaButtonReceiver` 和一个 Widget receiver；但应用 `androidx.core.content.FileProvider` 与 `rn-fetch-blob` 的 `com.RNFetchBlob.Utils.FileProvider` 都解析为 authority `com.lxwalnut.music.mobile.provider`。这是两个 owner 的冲突，可能影响安装或文件分享路由，必须先选择单一 owner 方案；不以禁用/强制合并为迁移旁路
- 构建与测试：[run 29688002586](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29688002586) 在提交 `3c06995` 通过签名 `assembleRelease` 与 `minifyReleaseWithR8`（`BUILD SUCCESSFUL in 7m 16s`），五个默认包名 APK 和签名校验通过，artifact `8442770896`。本机已安装 Microsoft OpenJDK `17.0.19.10`，`gradlew.bat -version` 通过；Android SDK Command-line Tools 22.0 已安装，API 35/Build Tools 等待有权人员确认 Google Android SDK 许可证。审计单元任务通过但没有测试报告目录，仓库未发现 Android instrumentation 源码
- 运行验证：`adb connect localhost:5555` 返回端口拒绝，`adb devices -l` 无设备；未安装或覆盖 APK。通知、锁屏、蓝牙、Widget 冷启动、外部文件/Deep Link 与多包名运行矩阵均仍未验证
- 阻塞：1) 当前图没有 1.9.4 的解析/编译/运行兼容证据，不能强制覆盖现有 native forks；2) 重复 FileProvider authority 必须处置并验证可安装性；3) 授权设备未启动。此前 minSdk 21 冲突已由 minSdk 23 决策解除
- 下一步：完成本机 SDK 安装并复跑本地依赖/Debug 检查；由负责人确定 Provider 单一 owner 与 1.9.4 依赖兼容方案后，再执行相应独立变更与完整回归。完成前不得开始 Phase 1

### 2026-07-19 - Phase 0 继续：minSdk 23 决策

- 阶段：Phase 0 - 基线与依赖审计
- 状态：执行中；用户批准将 `minSdkVersion` 从 `21` 提升至 `23`，以满足 Media3 `1.9.4` 的最低 API 要求
- 改动范围：`android/build.gradle`、`docs/MEDIA3_DESIGN.md`、`.github/workflows/media3-phase0-audit.yml` 和本文件；新增的工作流只在本审计分支 push 时生成依赖图、merged manifest、Debug APK 与单元测试证据，不发布、不改依赖。GitHub 仅从默认分支发现 `workflow_dispatch`，故此分支限定触发器用于避免为审计改动默认分支
- 影响：应用停止支持 API 21/22；RN、AGP、Gradle、Kotlin、compileSdk、targetSdk 和当前 Media3 声明均未改动
- 硬约束：设计规范明确 `minSdk 23` 为 Media3 1.9.4 的兼容性边界；恢复 API 21/22 支持必须先修订目标 Media3 版本并重跑 Phase 0
- 验证计划：在本地安装 JDK 后运行依赖树、`dependencyInsight`、merged manifest、Debug、Release/R8、相关测试与授权设备检查
- 审计流水线：run `29688411387` 已成功完成依赖图、merged manifest、Debug 和单元测试 Gradle 任务；失败仅发生在上传前收集不存在的测试报告目录，现已改为条件收集并重跑，不能作为产品构建失败结论
- 阻塞：等待本地 JDK/SDK 就绪以及完整 Gradle 结果；此前 minSdk 冲突已由用户决策解除
- 下一步：配置 JDK、确认 Android SDK，执行 Gradle 解析与构建取证

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

- 当前可解析的 Debug 图是 Media3 `1.8.0`，而不是目标 `1.9.4`；没有 1.9.4 与 TrackPlayer fork、react-native-video、本地媒体元数据和 Kotlin 1.9.24 的完整兼容证据，禁止强制混合版本。
- merged Debug Manifest 的应用 FileProvider 与 rn-fetch-blob FileProvider 共用 `${applicationId}.provider`，违反单一 owner 边界；需要先确定 authority/调用方迁移方案，并验证安装及文件分享。
- Provider authority 在 API 35 的 package dump 中只映射到项目 `androidx.core.content.FileProvider`，而 Manifest 仍保留 rn-fetch-blob 的第二个 Provider class；必须先选择单一 owner 或独立 authority 并验证外部文件分享。
- Debug APK 已可安装和启动，但当前没有 Metro/内嵌 JS bundle，故播放与 JS 驱动运行行为仍待可运行 bundle 验证。
