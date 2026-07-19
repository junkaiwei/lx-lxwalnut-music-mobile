# Media3 Phase 0 基线与依赖审计

审计日期：2026-07-19
分支与当前提交：`feat/media3-cold-resume` / `4933075`
范围：仅调查、验证和记录；没有新增或修改播放器、Service、Bridge 或 Media3 依赖。

## 结论和停止条件

`minSdk 23` 已于 2026-07-19 获产品批准并已提交（`3c06995`），因此 Media3 1.9.4 的 API 21/22 最低 SDK 冲突已解除，应用不再支持 API 21/22。当前旧引擎可以在 CI 的 Debug 与签名 minified Release/R8 变体上构建，且 CI 已保存完整依赖图和 merged Debug Manifest。

**Phase 0 仍不能开始 Phase 1。** 已发现两个必须先处理或明确处置的阻塞项：

1. 当前最终 Media3 图统一解析为 `1.8.0`，不是迁移目标 `1.9.4`。这是 Gradle 正常冲突解析的结果，不是为迁移强制版本：TrackPlayer 请求 `1.8.0`、本地媒体元数据请求 `1.5.1`、react-native-video 请求 `1.4.1`。尚未有 `1.9.4` 的完整依赖图、编译或运行证据；不得将 1.9.4 表述为已兼容，也不得在本阶段用强制版本绕过 fork 的兼容性审查。
2. merged Debug Manifest 中有两个不同的 `FileProvider` owner 使用同一 authority `com.lxwalnut.music.mobile.provider`：应用的 `androidx.core.content.FileProvider` 和 `rn-fetch-blob` 的 `com.RNFetchBlob.Utils.FileProvider`。这违反了唯一 owner 要求；在修复所有权并完成可安装性验证前，不能推进 Manifest/原生 Service 变更。

设备验证亦未完成：`adb connect localhost:5555` 返回连接被拒绝，`adb devices` 没有设备。该项是外部测试环境未启动，不是构建失败；没有执行 APK 覆盖安装。

## 1. 构建依赖链

| 链路项 | 版本/状态 | 定义或证据 |
| --- | --- | --- |
| React Native | `0.73.11` | `package.json`、`node_modules/react-native/package.json` |
| React Native Gradle Plugin | `0.73.5` | `node_modules/@react-native/gradle-plugin/package.json`；`android/settings.gradle` 的 `includeBuild` |
| Android Gradle Plugin | `8.6.1` | `android/build.gradle` |
| Gradle | `8.8` | `android/gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | `1.9.24` | `android/build.gradle` |
| JDK | 本机 Microsoft OpenJDK `17.0.19.10`；远端 Microsoft JDK `21.0.11` | 本机 `java -version` 和 `gradlew.bat -version` 已通过；远端 Actions 日志 |
| Android SDK | `compileSdk 35`；本机 Command-line Tools 22.0 已安装，但 API 35/Build Tools 安装等待 Android SDK 许可证确认；远端 API 35 构建已通过 | `android/build.gradle`；审计 CI；本机 `sdkmanager --licenses` |
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
| 主 FileProvider | `androidx.core.content.FileProvider`，authority `com.lxwalnut.music.mobile.provider` | 与下一行冲突 |
| RNFetchBlob FileProvider | `com.RNFetchBlob.Utils.FileProvider`，authority `com.lxwalnut.music.mobile.provider` | **重复 authority blocker** |
| 前台服务权限 | `FOREGROUND_SERVICE` 与 `FOREGROUND_SERVICE_MICROPHONE` | 未见 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`；目标 targetSdk 29 的运行行为仍待设备验证 |
| 外部文件/Deep Link | `MainActivity` 使用动态 scheme，接收 `file`/`content` 音频、脚本和 JSON/LXMC | 源码路径已追踪，未做设备运行验证 |

重复 authority 的源码归属明确：`android/app/src/main/AndroidManifest.xml` 的 `${appProviderAuthority}` 和 `node_modules/rn-fetch-blob/android/src/main/AndroidManifest.xml` 的 `${applicationId}.provider` 在默认包名下解析为同一值。不得通过隐藏/禁用任一 owner 作为迁移旁路；需要单独确定 authority 与调用方迁移方案，并验证 APK 安装与文件分享行为。

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
| 依赖树和 insight | audit run 29688646006 | **通过**：当前 Debug 图全部 Media3 最终为 1.8.0 |
| merged Debug Manifest | `:app:processDebugMainManifest`，audit run 29688646006 | **任务通过**，但发现重复 FileProvider authority |
| Debug 构建和单元任务 | `:app:assembleDebug :app:testDebugUnitTest`，audit run 29688646006 | **通过**；无可收集的单元测试报告目录，未发现 Android instrumentation 源码 |
| 签名 minified Release/R8 | [run 29688002586](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29688002586) | **通过**：提交 `3c06995`，`minifyReleaseWithR8` 完成、`BUILD SUCCESSFUL in 7m 16s`；五个 APK 默认包名和签名校验通过，artifact `8442770896` |
| 设备连接 | `adb connect localhost:5555`；`adb devices -l` | **未执行运行测试**：端口拒绝连接、设备列表为空；未安装 APK |
| 运行行为 | 播放、通知、锁屏、蓝牙、Widget 冷启动、外部入口等 | **未验证**：设备未连接，且重复 Provider owner 必须先处置 |
| 多包名构建 | 默认包名、`com.tencent.qqmusic`、额外自定义包名 | **未完成**：当前 Release 仅验证默认包名；不能因 Debug/Release 通过推断多包名正确 |

## 7. 影响、回滚与下一步

- 已确定影响：提高 minSdk 至 23 会使 API 21/22 无法安装；本分支没有更改播放实现、通知、Widget、QuickJS、网络或缓存。
- 已发现风险：Provider authority 重复会影响 APK 可安装性或 Provider 路由；当前旧引擎依赖图为 1.8.0，目标 1.9.4 尚未证明可兼容。
- 回滚：若产品撤销 API 23 决策，回滚 `3c06995` 并先修订唯一规范的 Media3 目标版本；不得保留 API 21/22 与 1.9.4 的强制混合图。
- 下一步：先由负责人选定并实现 Provider authority 的单一 owner 方案，验证安装/文件分享；随后在独立依赖变更审查中解析并验证 1.9.4（无强制混合版本），重跑 dependencyInsight、merged Manifest、Debug、签名 Release/R8、多包名和设备矩阵。若需本机执行，先由有权人员确认 Android SDK 许可证。完成前不得开始 Phase 1。
