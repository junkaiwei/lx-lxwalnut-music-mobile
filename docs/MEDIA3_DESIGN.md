# Media3 冷启动播放迁移规范

本文档是 Media3 迁移的唯一规范来源。阶段、依赖、影响、测试与验收要求均以本文为准。

## 1. 目标

在不升级 React Native 与 Android 构建工具链的前提下，将 Android TrackPlayer 运行时迁移为官方 Media3 播放核心，同时保留现有 UI、业务状态、动态包名和 QuickJS 音源脚本。

固定基线：

- React Native 0.73.11
- AGP 8.6.1
- Gradle 8.8
- Kotlin 1.9.24
- minSdk 23
- compileSdk 35
- targetSdk 29
- Media3 目标版本 1.9.4

Media3 1.9.4 在完成依赖、编译与运行验证前属于迁移目标，不得表述为已验证基线。

不在本次范围：升级 RN/AGP/Gradle/Kotlin、迁移 iOS、重做 UI、重写音源脚本、绕过 Android force-stop、蓝牙连接即自动播放。

`minSdk 23` 是本次 Media3 1.9.4 迁移的硬兼容性边界。不得在同一迁移中保留 API 21/22 支持或通过依赖强制绕过 Media3 的最低 SDK 要求；恢复 API 21/22 支持时必须先修订本规范中的 Media3 目标版本并重新执行 Phase 0。

## 2. 最终架构与功能边界

最终 Android 运行时必须只有：

- 一个 `MediaLibraryService`
- 一个 `MediaLibrarySession`
- 一个 ExoPlayer
- 一条媒体通知
- 一个媒体按键入口

React Native 通过原生 Bridge 控制既有 Session；QuickJS 继续解析音源 URL；缓存可用时不启动 RN 即可恢复；URL 失效时由可复用 SourceEngine 重新解析。

必须保持的业务能力：播放、暂停、停止、seek、队列增删改、上一首、下一首、循环、随机、状态、位置、元数据、错误与播放结束事件、通知、锁屏、蓝牙、Widget、外部文件及 Deep Link。

## 3. 强制变更流程

修改依赖、构建工具、Manifest 组件、原生模块、播放生命周期、QuickJS、网络、缓存、Widget 或外部入口前，必须先完成：

1. 完整依赖链追踪。
2. 传递依赖来源与最终解析版本确认。
3. Manifest 所有权和合并结果检查。
4. 运行时创建、连接、销毁和恢复链路分析。
5. 受影响模块与用户行为清单。
6. 代码检查和测试计划。
7. 回滚点与停止条件。

发现版本冲突、最低 SDK 冲突、Kotlin/AGP/RN 插件不兼容、双 Service、双 Session、双播放器、双通知或无法解释的生命周期所有权时，立即停止并在进度文档记录证据、影响和候选方案。禁止强制混合版本或绕过失败检查。

## 4. 依赖关系

必须追踪以下链路：

- RN -> RN Gradle Plugin -> AGP -> Gradle -> Kotlin -> JDK -> Android SDK
- JS player API -> player adapter -> TrackPlayer native module -> MusicService -> ExoPlayer/MediaSession
- TrackPlayer、react-native-video、本地媒体元数据模块及应用直接依赖 -> `androidx.media3:*`

所有 `androidx.media3:*` 必须解析到同一版本。Phase 0 必须保存 `dependencyInsight`、完整依赖树和 AAR metadata 证据。若 1.9.4 与现有 TrackPlayer fork、Kotlin 1.9.24 或其他原生模块冲突，停止实施，不得在本分支顺带升级工具链。

## 5. 实施路线

### Phase 0：基线与依赖审计

只调查和记录，不接入新播放器。输出依赖树、Media3 来源、merged manifest、现有播放器生命周期、通知/媒体键/Widget 所有权、基线构建和运行记录。

通过条件：现有旧引擎 Debug 与 minified Release 可重复构建；依赖、组件与行为基线明确；方案 A 无未处理硬冲突。

### Phase 1：Media3 服务骨架

新增 PlaybackService、PlayerFactory 和 Session Callback，服务内只创建一个 Player 与一个 Session。暂不接管业务播放。

通过条件：独立测试可连接 Session；最终 Manifest 仅包含计划内组件；旧引擎与新服务不能在同一构建变体同时拥有播放权限。

### Phase 2：原生 Bridge

新增 Media3 原生模块与 TS 封装，覆盖完整播放控制和状态快照。Controller 未连接时命令有序排队，Promise 必须超时并明确失败，重连后拉取完整状态。

### Phase 3：播放业务迁移

保持现有业务 API，迁移 player adapter、初始化、store 和事件映射。以 Session 快照为权威状态，验证错误、结束、切歌、进度、循环、随机和通知按钮顺序。

### Phase 4：快照与冷恢复

保存版本化队列、索引、位置、模式、元数据、歌曲身份和 URL 时效。原子写入、节流保存、损坏安全丢弃。缓存可用时无 RN 恢复。

### Phase 5：QuickJS SourceEngine 与网络

从 RN 事件桥抽离可复用 SourceEngine，保留现有脚本。专用线程、单 Runtime owner、硬超时、取消和安全日志。原生请求层兼容 headers、body、timeout、binary、redirect、cookie、cancel、status、response headers 与 Range。

### Phase 6：缓存、Widget 与外部入口

播放优先级为完整本地文件、Media3 缓存、有效旧 URL、QuickJS 重解析、明确失败。Widget 通过 MediaController 控制服务；外部文件与 Deep Link 在 Controller 连接后顺序执行。

### Phase 7：移除 TrackPlayer Android 运行时

仅在前述阶段全部通过后删除旧 JS Playback Service 注册、MusicService、旧媒体按键和通知链。iOS 所需依赖单独处理。

### Phase 8：发布验证

完成 Android 版本、厂商、进程、网络、格式、动态包名、覆盖安装、Release/R8、灰度与回滚验证。

## 6. 代码检查边界

每个阶段必须审查：

- 是否出现新的全局 owner 或重复生命周期
- 主线程、HandlerThread 与异步回调是否可终止
- Controller/Service 重连是否幂等
- 队列和状态事件顺序是否稳定
- 快照 schema、原子写和迁移是否安全
- URL、Cookie、Token、Header 是否泄露
- 动态包名是否存在硬编码
- R8 keep 规则是否最小且有证据
- 旧实现是否仍被自动链接或注册

## 7. 测试边界

每阶段至少执行与改动相关的：

- Media3 `dependencyInsight` 与依赖树差异
- merged manifest 与最终 APK 组件清单
- 单元测试、原生 instrumentation 测试和 TS 测试
- Debug 构建
- 签名 minified Release 构建及 R8
- 默认包名、`com.tencent.qqmusic` 和额外自定义包名构建

系统覆盖 API 23、26、29、31、33、34、35；API 36 作为前向兼容观察项，不作为 compileSdk 目标。厂商至少覆盖 AOSP/Pixel、Samsung、小米、OPPO/OnePlus、vivo。

运行场景覆盖：前后台、锁屏、划掉任务、普通进程死亡、设备重启、蓝牙、耳机拔出、电话/导航打断、省电、通知关闭、Widget 冷启动、RN 页面重建、外部文件和 Deep Link。

网络与音源覆盖：完整/部分/无缓存、有效 URL、403、超时、空 URL、脚本异常、断网与重连、Cookie、Referer、重定向、Range、二进制和取消。

格式覆盖：MP3、AAC/M4A、FLAC、OGG/Opus、本地 content URI、长音频 seek。

## 8. 验收标准

以下全部满足方可完成迁移：

- 最终 APK 只有一个播放 Service、一个媒体键 Receiver、一个 Player、一个 Session 和一条通知
- 播放、暂停、切歌、seek、循环、随机和队列行为无退化
- Debug 与签名 minified Release 均通过
- Media3 依赖仅一个版本且来源可解释
- RN 页面重建只连接既有 Session，不创建第二个 Player
- 普通进程死亡和设备重启后可恢复正确队列、歌曲和位置
- 缓存、URL 失效与 QuickJS 重解析路径结果可预测且有超时
- QuickJS 或网络异常不会导致 Service 崩溃、ANR 或无限等待
- Widget、通知、锁屏和蓝牙在 RN UI 不运行时可用
- 动态包名下 Service、Receiver、Provider、SessionToken、Widget action 和 PendingIntent 正确
- 覆盖安装不异常丢失设置、快照或可保留缓存
- 日志不包含完整 URL、Cookie、Token、密钥或敏感 Header

任一硬门槛失败，停止合并并在进度文档记录阻塞。

## 9. 进度与提交规则

每个迁移代码提交必须同步更新 `docs/MEDIA3_PROGRESS.md`，记录范围、依赖、影响、测试、阻塞和下一步。设计决策发生变化时先更新本文，再修改代码。

## 10. 回滚

阶段内使用构建开关确保同一 APK 只有一个引擎。新快照和缓存使用版本化路径，首个稳定版本不删除旧数据。发布回滚通过更高 versionCode 的旧引擎版本完成；禁止依赖远程开关让同一 APK 同时保留两个可运行播放器。
