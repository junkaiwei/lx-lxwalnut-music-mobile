# Media3 分步实施

## 阶段 0：冻结基线

- 记录现有前台、后台、通知、蓝牙、Widget、缓存和音源行为。
- 固定 Media3 版本，所有 `androidx.media3:*` 使用同一版本。
- 增加构建开关，但同一 APK 只允许启用一个播放引擎。
- 输出 merged manifest 和依赖树作为基线。

完成条件：旧播放器 Release APK 可重复构建，关键行为有测试记录。

## 阶段 1：Media3 服务骨架

新增 `PlaybackService`、`PlayerFactory`、`PlaybackSessionCallback`。

- Service 内只创建一个 ExoPlayer 和一个 MediaLibrarySession。
- 配置音乐 AudioAttributes、音频焦点、noisy 处理和通知。
- Manifest 注册媒体播放前台服务和 MediaButtonReceiver。
- 暂不接管现有 UI 播放。

完成条件：独立测试命令可连接 Session，APK 中只有计划内的新增组件。

## 阶段 2：原生 Bridge

新增 `Media3PlayerModule`、`Media3PlayerPackage` 和 TS 封装。

接口至少覆盖：setup、play、pause、stop、seek、setQueue、add、remove、上一首、下一首、循环、随机、状态、位置、队列和元数据。

- Controller 未连接时命令按顺序排队。
- Promise 必须超时和明确失败。
- JS 重连后读取完整状态快照。

完成条件：测试页面可完整控制 Media3，进程内没有第二个播放器。

## 阶段 3：迁移播放适配层

修改 `src/plugins/player`、`src/core/init`、`src/core/player` 和 player store。

- 保留现有业务 API，内部改调 Media3 Bridge。
- 建立 TrackPlayer 状态到现有业务事件的固定映射。
- 迁移错误、结束、切歌、进度和通知按钮行为。
- 删除 `registerPlaybackService` 与 `setupPlayer` 调用。

完成条件：前台播放行为与旧版一致，Debug 和 minified Release 均通过。

## 阶段 4：播放快照与恢复

新增版本化快照，保存队列、当前索引、位置、模式、元数据、歌曲身份和 URL 时效。

- 原子写入，损坏时安全丢弃。
- 进度节流保存，暂停、切歌、seek 和退出立即保存。
- `onPlaybackResumption` 只快速返回本地媒体项和位置。

完成条件：普通进程死亡和设备重启后可恢复媒体卡与缓存歌曲。

## 阶段 5：抽取 QuickJS SourceEngine

将 QuickJS 内核从 React Native 事件桥中抽离。

- 使用 Android Context 和统一回调接口。
- QuickJS 在专用 HandlerThread 运行。
- RN UserApiModule 和 PlaybackService 共享同一套 SourceEngine 能力。
- 保留现有音源脚本，不重写解析规则。

完成条件：现有前台音源功能不退化，服务可在无 RN UI 时加载保存的脚本。

## 阶段 6：原生网络桥接

实现与现有音源协议兼容的请求层：method、headers、body、timeout、binary、redirect、cookie、cancel、status 和 response headers。

- QuickJS 与 ExoPlayer 共用必要的 Cookie 和请求头策略。
- 每次解析设置硬超时，只对明确瞬态错误重试一次。
- 日志不得输出完整 URL、Cookie 或 Token。

完成条件：历史音源脚本 golden tests 全部通过。

## 阶段 7：URL 解析与缓存策略

播放优先级：完整本地文件、ExoPlayer 缓存、有效旧 URL、QuickJS 重新解析、明确失败。

- URL 保存 resolvedAt 和 expiresAt。
- 无网络且无缓存时立即失败，不无限等待。
- 新缓存使用版本化目录，旧缓存先只读探测。

完成条件：缓存、有效 URL、403、超时、断网和重连路径均可预测。

## 阶段 8：Widget 与外部入口

Widget 直接通过 MediaController 控制 PlaybackService，不再依赖 RN NativeEventEmitter。

- 使用显式 PendingIntent。
- 状态由 Session 生成。
- Deep Link 和外部音频文件在 Controller 连接后顺序执行。

完成条件：RN 页面未运行时，Widget 与蓝牙控制仍可用。

## 阶段 9：移除 TrackPlayer Android 运行时

- 删除旧 JS Playback Service 注册。
- 删除旧 MusicService、MEDIA_BUTTON Receiver 和通知链。
- Android 不再自动链接或运行 TrackPlayer。
- 若 iOS 仍需 TrackPlayer，仅保留 iOS 依赖和统一 TS 接口。

完成条件：最终 APK 只有一个播放 Service、一个 Session、一个播放器和一条媒体通知。

## 阶段 10：兼容验证与发布准备

执行 Android 版本、厂商、进程、网络、格式、动态包名和 Release/R8 测试。

完成条件：`MEDIA3_ACCEPTANCE.md` 的所有硬门槛通过后方可合并。
