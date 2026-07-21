# Media3 Phase 0 基线与依赖审计

审计日期：2026-07-19 至 2026-07-21
分支：`feat/media3-cold-resume`
范围：仅调查、验证和记录；没有新增或修改播放器、Service、Bridge 或 Media3 依赖。

## 结论和停止条件

`minSdk 23` 已于 2026-07-19 获产品批准并已提交（`3c06995`），因此 Media3 1.9.4 的 API 21/22 最低 SDK 冲突已解除，应用不再支持 API 21/22。当前旧引擎可以在 CI 的 Debug 与签名 minified Release/R8 变体上构建，且 CI 已保存完整依赖图和 merged Debug Manifest。

**Phase 0 仍不能开始 Phase 1。** 已发现两个必须先处理或明确处置的阻塞项：

1. 当前最终 Media3 图统一解析为 `1.8.0`，不是迁移目标 `1.9.4`。这是 Gradle 正常冲突解析的结果，不是为迁移强制版本：TrackPlayer 请求 `1.8.0`、本地媒体元数据请求 `1.5.1`、react-native-video 请求 `1.4.1`。尚未有 `1.9.4` 的完整依赖图、编译或运行证据；不得将 1.9.4 表述为已兼容，也不得在本阶段用强制版本绕过 fork 的兼容性审查。
2. merged Debug Manifest 中有两个不同的 `FileProvider` owner 使用同一 authority `com.lxwalnut.music.mobile.provider`：应用的 `androidx.core.content.FileProvider` 和 `rn-fetch-blob` 的 `com.RNFetchBlob.Utils.FileProvider`。API 35 设备安装成功，但 `dumpsys package` 仅将该 authority 路由到项目 Provider；这使 rn-fetch-blob 的 `actionViewIntent` 无法取得其外部存储 root。它违反唯一 owner 要求；在修复所有权并完成文件分享验证前，不能推进 Manifest/原生 Service 变更。

设备基线包括 `localhost:5555` 的 API 35 Samsung SM-S9210，以及 x86_64 Google APIs API 23 模拟器 `media3-api23`。API 23 上，当前工作区 Debug x86_64 APK 可安装并完成原生 MainActivity 冷启动（953 ms、进程存活）；因 Debug 未内嵌 JS bundle 且没有 Metro，该结果不代表完整 JS 行为。来自 CI 签名 minified Release artifact `8442770896` 的 x86_64 APK 在同一 API 23 模拟器上可安装并完成冷启动（512 ms、进程存活、无 `AndroidRuntime` 致命异常），UI 自动化层级确认内嵌 JS 基础界面已加载。播放及其通知、锁屏、蓝牙、Widget、外部入口行为仍未验证。

## 1. 构建依赖链

| 链路项 | 版本/状态 | 定义或证据 |
| --- | --- | --- |
| React Native | `0.73.11` | `package.json`、`node_modules/react-native/package.json` |
| React Native Gradle Plugin | `0.73.5` | `node_modules/@react-native/gradle-plugin/package.json`；`android/settings.gradle` 的 `includeBuild` |
| Android Gradle Plugin | `8.6.1` | `android/build.gradle` |
| Gradle | `8.8` | `android/gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | `1.9.24` | `android/build.gradle` |
| JDK | 本机 Microsoft OpenJDK `17.0.19.10`；远端 Microsoft JDK `21.0.11` | 本机 `java -version` 和 `gradlew.bat -version` 已通过；远端 Actions 日志 |
| Android SDK | `compileSdk 35`；本机 Command-line Tools 22.0、Platform Tools 37.0.0、Platform 35、Build Tools 35.0.0 | 用户授权后的 `sdkmanager`；远端 API 35 CI |
| target SDK | `29` | `android/build.gradle` |
| min SDK | `23` | `android/build.gradle`；已明确停止支持 API 21/22 |

应用没有直接声明 `androidx.media3:*`。直接应用原生依赖为 React Android、Hermes/JSC（条件选择）和 `wang.harlon.quickjs:wrapper-android:2.4.0`。

## 2. Media3 依赖链和最终解析状态

### 源码声明

| 来源 | 请求版本 | 说明 |
| --- | --- | --- |
| `react-native-track-player` fork（安装包 `2.1.2`） | `media3-exoplayer:1.8.0`；Dash/HLS/SmoothStreaming `1.8.0` compileOnly | `node_modules/react-native-track-player/android/build.gradle` |
| `react-native-video 6.17.0` | `exoplayer`、`session`、`common`、`datasource`、`datasource-okhttp`、`ui` 和启用的 Dash/HLS/SmoothStreaming 都是 `1.4.1` | `node_modules/react-native-video/android/gradle.properties`、`android/build.gradle` |
| `react-native-local-media-metadata` fork | `media3-exoplayer:1.5.1` | `node_modules/react-native-local-media-metadata/android/build.gradle` |
| 应用模块 | 无 | `android/app/build.gradle` |

### CI 最终解析证据

[GitHub Actions audit run 29688646006](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29688646006) 在提交 `4933075f89a6180962e1f441145a749521d9161f` 的 `debugRuntimeClasspath` 运行完整依赖树和以下 `dependencyInsight`：`common`、`exoplayer`、`session`、`datasource`、`datasource-okhttp`、`ui`、`exoplayer-dash`、`exoplayer-hls`、`exoplayer-smoothstreaming`。结果均解析为 **`1.8.0`**，并包含 `container`、`database`、`decoder` 和 `extractor` 等传递模块，未发现同一图中的第二个最终 Media3 版本。

关键选择路径：

```text
react-native-track-player -> media3-exoplayer:1.8.0
react-native-local-media-metadata -> media3-exoplayer:1.5.1 -> 1.8.0
react-native-video -> media3-{common,exoplayer,session,datasource,...}:1.4.1 -> 1.8.0
```

所有 `dependencyInsight` 的选择理由都是约束和/或 `1.8.0` 与 `1.4.1` 的冲突解析。该证据只证明旧引擎当前图的单版本结果；它不验证目标 `1.9.4` 与三个原生模块、Kotlin 或运行时 API 的兼容性。

本机也执行了 `:app:dependencies --configuration releaseRuntimeClasspath`，以及 `releaseRuntimeClasspath` 上 `media3-common`、`media3-exoplayer` 和 `media3-session` 的 `dependencyInsight`。三项均解析为 `1.8.0`，选择路径与 Debug 相同；因此不存在“仅 Debug 是 1.8.0、Release 已解析为目标 1.9.4”的差异。

审计产物名为 `media3-phase0-4933075f89a6180962e1f441145a749521d9161f`，保存了完整 `debugRuntimeClasspath.txt`、每个 insight 输出、merged Manifest 和 Debug APK。

## 3. Media3 1.9.4 工具链兼容性

对 Google Maven 的 `1.9.4` AAR 检查了 `common`、`exoplayer`、`session`、`datasource`、`datasource-okhttp`、`ui`、Dash、HLS 和 SmoothStreaming。各 AAR 的 metadata 均要求：

```text
minCompileSdk=35
minAndroidGradlePluginVersion=1.0.0
coreLibraryDesugaringEnabled=false
```

因此 `compileSdk 35` 和 AGP `8.6.1` 没有 AAR metadata 冲突；检查的 AAR 没有 Kotlin module metadata，亦未发现 AAR 层 Kotlin metadata 冲突。官方 [Media3 1.9.0 发布说明](https://developer.android.com/jetpack/androidx/releases/media3#1.9.0)将最低 SDK 升至 23，现已与项目 `minSdk 23` 对齐。此结论不替代 1.9.4 图解析、编译和运行验证。

## 4. Manifest 所有权和 merged Manifest 结果

CI 通过 `:app:processDebugMainManifest` 生成并检查 merged Debug Manifest；产物中有 2 个 service、3 个 receiver、4 个 provider 和 3 个 activity。

| 项目 | 最终 owner/结果 | 状态 |
| --- | --- | --- |
| Playback Service | `com.guichaguri.trackplayer.service.MusicService`，`enabled=true`、`exported=true`，处理 `MEDIA_BUTTON` | 当前唯一播放 Service |
| MediaSession | TrackPlayer `MetadataManager`/`MusicBinder` 的 legacy `MediaSessionCompat` | 当前唯一已知 Session owner；非 Media3 Session |
| MEDIA_BUTTON receiver | `androidx.media.session.MediaButtonReceiver`，`exported=true` | 当前唯一媒体按键 receiver |
| Widget | `com.lxwalnut.music.mobile.widget.MusicWidgetProvider`，动态 action 已解析为 `com.lxwalnut.music.mobile.widget.*` | 当前唯一 Widget receiver |
| 主 FileProvider | `androidx.core.content.FileProvider`，authority `com.lxwalnut.music.mobile.provider`，`exported=false`、`grantUriPermissions=true`、`@xml/file_paths`（files/cache） | 系统将重复 authority 路由至此节点 |
| RNFetchBlob FileProvider | `com.RNFetchBlob.Utils.FileProvider`，同一 authority，`exported=false`、`grantUriPermissions=true`、`@xml/provider_paths`（external/files/cache） | **重复 authority blocker；其 external root 未成为该 authority 的最终 owner** |
| 前台服务权限 | `FOREGROUND_SERVICE` 与 `FOREGROUND_SERVICE_MICROPHONE` | 未见 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`；目标 targetSdk 29 的运行行为仍待设备验证 |
| 外部文件/Deep Link | `MainActivity` 使用动态 scheme，接收 `file`/`content` 音频、脚本和 JSON/LXMC | 源码路径已追踪，未做设备运行验证 |

重复 authority 的源码归属明确：`android/app/src/main/AndroidManifest.xml` 的 `${appProviderAuthority}` 和 `node_modules/rn-fetch-blob/android/src/main/AndroidManifest.xml` 的 `${applicationId}.provider` 在默认包名下解析为同一值。当前工作区生成的 universal APK 安装到 API 35 后，`dumpsys package com.lxwalnut.music.mobile` 同时列出两个 Provider class，但 authority 映射 `[com.lxwalnut.music.mobile.provider]` 只指向 `androidx.core.content.FileProvider`。本机 `:app:processReleaseMainManifest` 的同名两个不同 class、authority、`exported=false`、`grantUriPermissions=true` 与各自 paths 也和 Debug 一致；这不是情况一的同 class 合并，且不满足“目标 authority 的 Provider 节点数量为 1”的验收条件。

调用链显示项目 `UtilsModule` 用项目 Provider 分享 files/cache；rn-fetch-blob 的 `RNFetchBlob.actionViewIntent()` 也拼接 `${packageName}.provider`，原设计依赖其 `external-path`。项目静态 JS/Java 源码没有调用 `actionViewIntent`，但不能以此推断第三方或未来调用不存在，故不得直接移除依赖声明；需要先确定保留该 API 还是迁移其调用与 authority。

## 5. 当前运行时生命周期（源码追踪）

```text
src/core/init -> registerPlaybackService()
  -> TrackPlayer.registerPlaybackService()
  -> TrackPlayer native bridge -> MusicService / MusicBinder
  -> MusicManager.createLocalPlayback()
  -> 一个 ExoPlayer + legacy MediaSessionCompat + 通知/媒体按键

Widget -> MusicWidgetProvider -> MusicWidgetModule
  -> React Native NativeEventEmitter -> JS player controls
```

- `MusicService.onCreate()` 先以空通知进入前台；`onStartCommand()` 创建 `MusicManager`，`CONNECT_INTENT` 返回 `MusicBinder`。
- `MusicManager` 创建一个 ExoPlayer；`MetadataManager` 创建 `MediaSessionCompat` 并拥有通知与回调。
- Service 和 receiver 都处理媒体按键；Service 返回 `START_NOT_STICKY`，没有版本化队列/位置快照或无 RN 的恢复路径。
- `onDestroy()` 释放 handler、播放器、元数据、wake/wifi lock 与 noisy receiver。进程死亡/重启恢复没有实现证据。
- Widget 仅通过 ReactContext 的 event emitter 驱动，RN 未运行时不能证明可控；这是迁移前风险项。

## 6. 既有行为和测试基线

| 检查 | 证据/命令 | 结果 |
| --- | --- | --- |
| Gradle/JDK 预检 | `cd android; .\\gradlew.bat -version`，本机 JDK 17 | **通过**：Gradle 8.8，JVM 17.0.19 Microsoft |
| 依赖树和 insight | 本机 `:app:dependencies`、`:app:dependencyInsight --dependency androidx.media3:media3-exoplayer`；audit run 29688646006 | **通过**：当前 Debug 图全部 Media3 最终为 1.8.0；本机确认 1.4.1/1.5.1 均解析至 1.8.0 |
| merged Debug Manifest | 本机和 audit run 的 `:app:processDebugMainManifest` | **任务通过**，但两个不同 Provider class 共用 authority；API 35 package dump 确认 authority 只路由项目 Provider |
| Release 依赖树和 insight | 本机 `:app:dependencies --configuration releaseRuntimeClasspath`；`media3-common`、`media3-exoplayer`、`media3-session` 的 Release `dependencyInsight` | **通过**：Release 图也全部解析为 1.8.0，选择路径与 Debug 相同 |
| merged Release Manifest | 本机 `:app:processReleaseMainManifest` | **任务通过但阻塞未解除**：Release 同样有两个不同 Provider class 共用 `com.lxwalnut.music.mobile.provider`，配置与 Debug 的冲突一致 |
| Debug 构建和单元任务 | 本机 `:app:processDebugMainManifest :app:assembleDebug :app:testDebugUnitTest` | **通过**：`BUILD SUCCESSFUL in 3m`，459 个任务；react-native-quick-md5 只有 C/C++ 括号告警。无可收集的单元测试报告目录，未发现 Android instrumentation 源码 |
| 签名 minified Release/R8 | [run 29688002586](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29688002586) | **通过**：提交 `3c06995`，`minifyReleaseWithR8` 完成、`BUILD SUCCESSFUL in 7m 16s`；五个 APK 默认包名和签名校验通过，artifact `8442770896` |
| API 23 Debug 安装与冷启动 | `media3-api23` x86_64 Google APIs 模拟器；`adb push` + `pm install -t`；`am force-stop`、`am start -W` | **通过（原生范围）**：`ro.build.version.sdk=23`，安装成功，冷启动 953 ms、进程存活。Debug 没有内嵌 JS bundle 且未启动 Metro，故不标记 UI/播放通过 |
| API 23 签名 Release 安装、冷启动与基础 UI | artifact `8442770896` 的 x86_64 APK；标准 `pm install -r`（不带 `-t`）；`am force-stop`、`am start -W`、`logcat`、`uiautomator dump` | **通过（基础范围）**：标准安装成功，冷启动 406 ms、进程存活、Activity 位于前台、`AndroidRuntime` 无致命异常；内嵌 JS UI 层级已出现。未测试播放及外部入口 |
| 设备连接与安装 | `adb connect localhost:5555`；`adb install -r` 当前工作区 Debug universal APK | **通过**：Samsung SM-S9210、API 35；首次和覆盖安装均成功，最终 package 为 minSdk 23 / targetSdk 29 |
| MainActivity 冷启动 | `adb shell am force-stop`、`am start -W -n com.lxwalnut.music.mobile/.MainActivity` | **通过**：cold launch 6754 ms，进程存活；Debug 无 Metro `localhost:8081`，故 JS/UI/播放基线未完成 |
| 运行行为 | 播放、通知、锁屏、蓝牙、Widget 冷启动、外部入口等 | **未验证**：需要可运行 JS bundle，且重复 Provider owner 必须先处置 |
| `com.tencent.qqmusic` 签名 Release 与覆盖安装 | [run 29795317191](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29795317191)，artifact `8481864959`；API 23 标准 `pm install` 后 `pm install -r` | **通过（身份/安装范围）**：签名验证、APK package、首次和覆盖安装、冷启动均通过。Release merged Manifest 的 applicationId 是 `com.tencent.qqmusic`，项目/RNFetchBlob Provider 都是 `com.tencent.qqmusic.provider`，Widget action 是 `com.tencent.qqmusic.widget.*`，Deep Link scheme 为显式配置的 `lxmusic`；重复 Provider owner 仍存在 |
| 第二个自定义包名 Debug | 本机 `:app:assembleDebug :app:processDebugMainManifest -PAPP_PACKAGE_NAME=com.lxwalnut.music.phase0audit -PAPP_DEEP_LINK_SCHEME=lxphaseaudit` | **通过（构建/Manifest 范围）**：x86_64 Debug APK package 是 `com.lxwalnut.music.phase0audit`；Provider 为 `com.lxwalnut.music.phase0audit.provider`，Widget action 为 `com.lxwalnut.music.phase0audit.widget.*`，Deep Link scheme 为 `lxphaseaudit`；重复 Provider owner 仍存在 |
| 可复核原始证据 | [`docs/evidence/media3-phase0-2026-07-21`](evidence/media3-phase0-2026-07-21/README.md) | **已保存**：Release classpath/insight/Manifest，API 23 设备与安装/启动/logcat/UI，APK hash/ABI/version/source，以及动态包名的签名与覆盖安装输出 |

## 7. 影响、回滚与下一步

- 已确定影响：提高 minSdk 至 23 会使 API 21/22 无法安装；本分支没有更改播放实现、通知、Widget、QuickJS、网络或缓存。
- 已发现风险：Provider authority 重复不阻止默认、`com.tencent.qqmusic` 或第二个自定义包名的构建、API 23 安装和基础启动，但两种 Provider class 仍共用各自 `<applicationId>.provider`；系统的 owner 路由使 rn-fetch-blob external path 不具备 owner。当前旧引擎的 Debug/Release 依赖图均为 1.8.0，目标 1.9.4 尚未证明可兼容。
- 回滚：若产品撤销 API 23 决策，回滚 `3c06995` 并先修订唯一规范的 Media3 目标版本；不得保留 API 21/22 与 1.9.4 的强制混合图。
- 下一步：先由负责人选定并实现 Provider authority 的单一 owner 方案，验证安装/文件分享；随后在独立依赖变更审查中解析并验证 1.9.4（无强制混合版本），重跑 dependencyInsight、merged Manifest、Debug、签名 Release/R8、多包名和设备矩阵。完成前不得开始 Phase 1。
