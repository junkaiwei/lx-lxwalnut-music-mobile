# Media3 Phase 0 基线与依赖审计

审计日期：2026-07-19  
分支与提交：`feat/media3-cold-resume` / `99019f4`  
范围：仅调查、验证和记录；没有新增或修改任何播放器、Service、Bridge 或依赖。

## 结论和停止条件

**Phase 0 被阻塞，不能开始 Phase 1。** 发现两个独立的硬门槛：

1. `androidx.media3:1.9.4` 不兼容当前应用的 `minSdkVersion 21`。[Android 官方 Media3 1.9.0 发布说明](https://developer.android.com/jetpack/androidx/releases/media3#1.9.0)明确将最低 API 提升为 23；`1.9.4` 属于该系列。项目的 `android/build.gradle` 第 6 行固定 `minSdkVersion = 21`。这会使 API 21/22 设备无法安装升级后的应用。
2. 当前**本地**构建会话没有可用 JDK 或 Android SDK 环境：`JAVA_HOME` 未设置，`java` 不在 `PATH`，`ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 未设置，常用本机 JDK/SDK 路径也不存在。因此本地 Gradle 无法启动，无法生成本地 merged manifest、最终解析依赖图、Debug APK 或执行 Gradle 测试。远端流水线已验证旧引擎签名 Release，但不提供缺失的依赖图或 merged manifest 证据。

此外，现有原生模块在源码中请求了三个 Media3 版本（`1.4.1`、`1.5.1` 和 `1.8.0`）。未能运行 `dependencyInsight` 前，不能把 Gradle 的通常“选择最高版本”行为当作此分支的最终解析证据，也不能声称所有模块已统一到一个版本。

受影响模块是 `react-native-track-player`、`react-native-video`、`react-native-local-media-metadata`、应用播放功能，以及 API 21/22 用户。用户可见影响分别是：若直接采用 `1.9.4` 并提高 minSdk，旧设备无法安装；若强制覆盖版本，旧模块可能发生二进制/API 行为回归；在没有可重复构建的情况下，不能验证通知、锁屏、蓝牙、Widget、外部文件或 Deep Link。

可行选项：

1. 产品批准将应用最低 API 提升到 23，再以 JDK 17 和 Android SDK 35 重跑完整 Phase 0；这会停止支持 API 21/22。
2. 保持 `minSdkVersion 21`，先修订 `docs/MEDIA3_DESIGN.md` 的目标版本为仍支持 API 21 的 Media3 系列（需重新确认安全与功能要求），再重新审计。
3. 不采用“强制版本”“排除传递依赖”或同 APK 双播放器的绕过方案；这些做法违反 `MEDIA3_DESIGN.md` 的统一版本和唯一 owner 约束。

建议：**保持现有旧引擎，先由产品决定 API 21/22 支持策略；若继续 1.9.4，批准 minSdk 23 后再配置 JDK 17/SDK 35 并从 Phase 0 重新开始。**

## 1. 构建依赖链

| 链路项 | 版本/状态 | 定义或证据 |
| --- | --- | --- |
| React Native | `0.73.11` | `package.json`、`node_modules/react-native/package.json` |
| React Native Gradle Plugin | `0.73.5` | `node_modules/@react-native/gradle-plugin/package.json`；由 `android/settings.gradle` 第 4 行 `includeBuild` 提供，根构建第 24 行引用 |
| Android Gradle Plugin | `8.6.1` | `android/build.gradle` 第 23 行 |
| Gradle | `8.8` | `android/gradle/wrapper/gradle-wrapper.properties` 第 3 行 |
| Kotlin | `1.9.24` | `android/build.gradle` 第 11、25 行 |
| JDK | 本地 **未检测到**；远端流水线为 Microsoft JDK `21.0.11` | 本地 `java -version` 不可执行；远端 [run 29683410711](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29683410711) 的 `actions/setup-java` 日志 |
| Android SDK | `compileSdk 35`（配置值），本机 SDK **未检测到**；远端 Release APK 以 API 35 构建 | `android/build.gradle` 第 7 行；本地没有 `ANDROID_HOME`/`ANDROID_SDK_ROOT`；远端 `aapt dump badging` 输出 `compileSdkVersion='35'` |
| target SDK | `29` | `android/build.gradle` 第 8 行 |
| min SDK | `21` | `android/build.gradle` 第 6 行 |

应用没有直接声明 `androidx.media3:*` 依赖；直接应用原生依赖为 `com.facebook.react:react-android`、Hermes/JSC（条件选择）和 `wang.harlon.quickjs:wrapper-android:2.4.0`，见 `android/app/build.gradle` 第 118–123 行。

## 2. Media3 依赖链和最终解析状态

### 源码声明

| 来源 | Media3 声明 | 说明 |
| --- | --- | --- |
| `react-native-track-player` fork（package 固定至 `d4a062f`，安装包显示 `2.1.2`） | `media3-exoplayer:1.8.0`；Dash/HLS/SmoothStreaming 为 `1.8.0` compileOnly（默认功能开关均为 false） | `node_modules/react-native-track-player/android/build.gradle` 第 64–87 行 |
| `react-native-video 6.17.0` | `media3-exoplayer`、`session`、`common`、`datasource`、`datasource-okhttp`、`ui` 和启用的 Dash/HLS/SmoothStreaming 均取 `RNVideo_media3Version=1.4.1` | `node_modules/react-native-video/android/gradle.properties` 第 7–15 行及 `android/build.gradle` 第 237–317 行；`buildFromMedia3Source=false` |
| `react-native-local-media-metadata` fork（package 固定至 `f2d0399`） | `media3-exoplayer:1.5.1` | `node_modules/react-native-local-media-metadata/android/build.gradle` 第 89 行 |
| 直接应用依赖 | 无 | `android/app/build.gradle` 依赖块 |

因此当前源码请求的全部 Media3 模块至少包括：`common`、`container`、`database`、`datasource`、`datasource-okhttp`、`decoder`、`exoplayer`、`exoplayer-dash`、`exoplayer-hls`、`exoplayer-smoothstreaming`、`extractor`、`session` 和 `ui`。前三个请求版本不同，不能在未运行解析任务前声称最终为单版本。

### 要求但未能执行的 Gradle 证据

下列命令是本阶段的必需取证命令；由于 JDK 缺失，均没有有效 Gradle 结果：

```powershell
cd android
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
.\gradlew.bat :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.media3:media3-common
.\gradlew.bat :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.media3:media3-exoplayer
.\gradlew.bat :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.media3:media3-session
```

实际执行的预检命令是 `& .\gradlew.bat -version`（工作目录 `android`），输出为：`ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.` 因此不能可靠保存本分支的最终解析版本或路径。

`node_modules/react-native-video/android/buildOutput_*` 中存在旧的 `1.4.1` lint 构件记录，但它不是本次分支执行的 Gradle 解析输出，不能作为当前最终依赖图证据。

## 3. Media3 1.9.4 工具链兼容性

对 Google Maven 下载到内存（未写入工作区）的 `1.9.4` AAR 检查了下列模块：`media3-common`、`media3-exoplayer`、`media3-session`、`media3-datasource`、`media3-datasource-okhttp`、`media3-ui`、`media3-exoplayer-dash`、`media3-exoplayer-hls` 和 `media3-exoplayer-smoothstreaming`。

每个 AAR 的 `META-INF/com/android/build/gradle/aar-metadata.properties` 均为：

```text
aarFormatVersion=1.0
aarMetadataVersion=1.0
minCompileSdk=35
minCompileSdkExtension=0
minAndroidGradlePluginVersion=1.0.0
coreLibraryDesugaringEnabled=false
```

这说明 `compileSdk 35` 和 AGP `8.6.1` 不构成 AAR metadata 冲突。所检查 AAR 没有 `*.kotlin_module` 条目，所以没有来自这些 Media3 AAR 的 Kotlin metadata 版本冲突证据；但项目的最终 Kotlin metadata 验证仍未执行，因为 Gradle 不能启动。`react-native-video` 自身最低 Kotlin 要求是 `1.8.0`，项目 Kotlin 为 `1.9.24`，静态版本比较满足其构建脚本检查。

[Android 官方 Media3 1.9.0 发布说明](https://developer.android.com/jetpack/androidx/releases/media3#1.9.0)记录“Update minSdk to 23”，而项目固定 minSdk 为 21。这是 `1.9.4` 的明确最低 SDK 冲突，不能靠依赖强制或 R8 解决。

## 4. Manifest 所有权（静态；merged manifest 未生成）

| 项目 | 声明 owner 和当前静态注册 | 状态 |
| --- | --- | --- |
| Playback Service | `react-native-track-player` 的 `com.guichaguri.trackplayer.service.MusicService`，`enabled=true`、`exported=true`，处理 `android.intent.action.MEDIA_BUTTON` | 源码中唯一已知播放 Service；未生成 merged manifest，不能确认最终唯一性 |
| MediaSession | TrackPlayer `MetadataManager`/`MusicBinder` 使用 legacy `android.support.v4.media.session.MediaSessionCompat`，不是 Media3 `MediaSession` | 当前唯一已知 session owner；最终合并/运行时未验证 |
| MEDIA_BUTTON receiver | TrackPlayer 的 `androidx.media.session.MediaButtonReceiver`，`exported=true` | 源码中唯一已知 receiver；最终合并未验证 |
| 前台服务权限 | 应用声明 `FOREGROUND_SERVICE` 与 `FOREGROUND_SERVICE_MICROPHONE`；TrackPlayer 再声明 `FOREGROUND_SERVICE` 与 `WAKE_LOCK` | 未见 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`；targetSdk 29 下的实际系统行为未验证 |
| 媒体通知 | TrackPlayer `MusicService.onCreate()` 先以空通知前台启动，`MetadataManager` 负责媒体元数据/通知；应用 Manifest 无通知 owner | 最终通知渠道/组件未验证 |
| Widget | 应用 `MusicWidgetProvider`，动态 action 由 `BuildConfig.APP_WIDGET_ACTION_PREFIX` 生成；`MusicWidgetModule` 动态注册内部广播并发给 RN `NativeEventEmitter` | 无第二个 Widget receiver 的静态证据；Widget 冷启动是否能控制播放器未验证 |
| 外部文件和 Deep Link | `MainActivity` 接受动态 `${appDeepLinkScheme}`、`file`/`content` 音频、脚本与 JSON/LXMC；`FileProvider` 使用 `${appProviderAuthority}` | `onNewIntent` 仅在现有 ReactContext 时发出 `url`；冷启动由 JS `getInitialURL` 的覆盖路径尚未运行验证 |

应用 Manifest 中没有声明第二个播放 Service、Media3 Session 或 MEDIA_BUTTON receiver；`react-native-video` 的新 Manifest 为空，本地元数据模块只声明外部存储权限。因为 `processDebugMainManifest` 未能运行，以上只能证明静态来源，不能当作 merged manifest 或最终 APK 结论。

## 5. 当前运行时生命周期（源码追踪）

```text
src/core/init/index.ts
  -> registerPlaybackService()
  -> TrackPlayer.registerPlaybackService(registerPlaybackService)
src/core/init/player/player.ts
  -> src/plugins/player/index.ts initial()
  -> TrackPlayer.setupPlayer(...)
  -> TrackPlayer Android native bridge
  -> MusicService / MusicBinder
  -> MusicManager.createLocalPlayback()
  -> 一个 ExoPlayer + legacy MediaSessionCompat MetadataManager
  -> MediaStyle notification / media buttons / Bluetooth / lock screen

Widget
  -> MusicWidgetProvider (package-scoped internal broadcast)
  -> MusicWidgetModule
  -> RN NativeEventEmitter
  -> src/plugins/player/service.ts
  -> core player play/pause/previous/next
```

- 创建和连接：`MusicService.onCreate()` 启动临时前台通知；`onStartCommand()` 新建 `MusicManager`；`CONNECT_INTENT` 绑定返回 `MusicBinder`。`setupPlayer()` 每次调用会通过 `switchPlayback()` 销毁旧 `ExoPlayback` 后新建 ExoPlayer。
- 后台与媒体键：Service 和 `MediaButtonReceiver` 都处理 `MEDIA_BUTTON`。如果后台没有 React Activity，服务会短暂以空通知进入前台后 `stopSelf()`。`RemotePlay`、`RemotePause`、`RemoteNext`、`RemotePrevious`、`RemoteSeek` 和 `RemoteStop` 在 JS playback service 映射至既有 core player；`RemoteStop` 会退出应用。
- 任务移除、清理与进程死亡：`MusicService.onTaskRemoved()` 在 `stopWithApp`（或没有 manager）时停止播放器、销毁并停止 Service。`onDestroy()` 释放 handler、播放器、元数据、wake/wifi lock 和 noisy receiver。Service 返回 `START_NOT_STICKY`，代码中没有版本化队列/位置快照或不启动 RN 的恢复路径；普通进程死亡与设备重启恢复未实现/未运行验证。
- 耳机和焦点：播放时注册 `ACTION_AUDIO_BECOMING_NOISY` 并向 JS 发暂停事件；ExoPlayer 以 `handleAudioFocus` 设置音频属性。JS `RemoteDuck` 状态处理目前被注释，因此电话/导航打断后的业务状态同步仅能视为未验证。
- Widget：Provider 将按钮转换为包内广播；Module 仅在 ReactContext 存在时发出 JS event。因此现有实现不具备“RN 未运行仍可通过 MediaController 控制”的能力，Widget 冷启动是迁移前风险项。

## 6. 既有行为基线（源码证据，未运行验收）

| 行为 | 当前源码路径 | 运行验证 |
| --- | --- | --- |
| 播放、暂停、停止、seek | `src/plugins/player/utils.ts` 调用 `TrackPlayer.play/pause/stop/seekTo` | 未运行 |
| 队列、上一首、下一首 | `src/plugins/player/playList.ts` 与 `src/core/player/player.ts`；TrackPlayer `add/remove/skip` | 未运行 |
| 循环、随机 | `utils.ts` 的 `setRepeatMode`；随机由 core playlist 逻辑管理 | 未运行 |
| 完成与错误 | 临时 default track/track-changed 处理播放结束；`PlaybackError` 清除 URL 缓存并发 app error | 未运行 |
| 前后台、通知、锁屏、蓝牙 | TrackPlayer Service + `MediaSessionCompat` + Remote* 事件 | 未运行 |
| 耳机拔出、音频焦点 | native noisy receiver；ExoPlayer audio focus；JS RemoteDuck 已注释 | 未运行 |
| Widget | Provider -> Module -> JS 事件 | 未运行；冷启动不满足目标架构 |
| 外部音频、Deep Link | `MainActivity` intent filters 和 `onNewIntent` | 未运行 |
| 动态包名 | `APP_PACKAGE_NAME` 进入 `applicationId`、Provider authority、Widget action prefix；Java Widget 读取 BuildConfig | 未做默认/QQ 音乐/自定义包 APK 验证 |

## 7. 构建、测试和设备基线

| 必需检查 | 命令/环境 | 结果 |
| --- | --- | --- |
| Gradle/JDK 预检 | `cd android; .\gradlew.bat -version` | **失败**：`JAVA_HOME is not set and no 'java' command could be found in your PATH.` |
| JDK 预检 | `java -version` | **失败**：PowerShell 找不到 `java` |
| Android SDK 预检 | 检查 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 和 `C:\Users\admin\AppData\Local\Android\Sdk` | **失败**：均不存在/未设置 |
| Debug build | `cd android; .\gradlew.bat :app:assembleDebug` | 未执行：本地 JDK 预检阻断；远端既有工作流不包含 Debug 任务 |
| 签名/可复现 minified Release 与 R8 | [GitHub Actions run 29683410711](https://github.com/junkaiwei/lx-lxwalnut-music-mobile/actions/runs/29683410711)，远端工作目录 `android` 执行 `./gradlew assembleRelease -PAPP_PACKAGE_NAME=com.lxwalnut.music.mobile -PAPP_DEEP_LINK_SCHEME=lxmusic` 及密钥参数 | **通过**：提交 `99019f4`，Microsoft JDK `21.0.11`；`> Task :app:minifyReleaseWithR8` 后 `BUILD SUCCESSFUL in 7m 38s`。五个 APK 的 `aapt` 包名均为 `com.lxwalnut.music.mobile`，`apksigner` 证书 SHA-256 与 keystore 一致；artifact `packet-name-com.lxwalnut.music.mobile`（ID `8441382627`）已上传 |
| dependencyInsight 和完整依赖树 | 见第 2 节 | 未执行：被 JDK 预检阻断 |
| merged manifest | `cd android; .\gradlew.bat :app:processDebugMainManifest` | 未执行：被 JDK 预检阻断 |
| 单元/Android instrumentation 测试 | Gradle test/connected task | 未执行：被 JDK 预检阻断 |
| `localhost:5555` 设备测试 | 仅获授权可连接，未操作设备 | 未执行：没有可从此分支构建和安装的 APK |

## 8. Phase 0 下一步和回滚点

不修改现有 TrackPlayer、Manifest 或依赖。当前工作树的回滚点就是提交 `99019f4`（Phase 0 前无实现变更）。

只有在产品选择 minSdk 策略、提供本地 JDK 与 Android SDK 35（或明确指定可复用远端检查）后，才可重新执行本 Phase：先运行依赖树/insight 和 merged manifest，再执行 Debug、签名 minified Release/R8、默认包名/`com.tencent.qqmusic`/自定义包名构建，最后使用授权设备覆盖运行时矩阵。若选择保留 minSdk 21，必须先修订唯一规范中的 Media3 目标版本，再继续。
