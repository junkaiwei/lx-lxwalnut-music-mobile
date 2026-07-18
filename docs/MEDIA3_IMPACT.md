# Media3 关联模块与风险控制

## 1. Gradle 与依赖

关联：`package.json`、`android/build.gradle`、`android/app/build.gradle`。

影响：Media3 版本冲突、最低 Android 版本变化、R8 后崩溃、iOS 仍依赖 TrackPlayer。

预防：所有 Media3 模块统一版本；CI 检查 dependencyInsight；同时测试 Debug 与 minified Release；若 iOS 保留 TrackPlayer，则只禁用 Android 运行链。

解决：出现依赖冲突时先锁定统一版本并移除旧传递依赖；不使用强制混合版本。

## 2. Manifest 与系统组件

关联：应用 Manifest、TrackPlayer 合并 Manifest、动态包名配置。

影响：双 Service、双 Receiver、双通知、错误包名、导出组件被滥用。

预防：构建后检查 merged manifest；只保留一个媒体 Service 和一个 MEDIA_BUTTON Receiver；运行时包名用 `context.packageName`；自定义命令限制可信 Controller。

解决：用 manifest remove 规则移除旧组件；出现媒体键冲突时先核对最终 APK 组件清单。

## 3. PlaybackService 与 Session

关联：新 `player` 原生目录。

影响：重复初始化、并发改队列、释放竞态、暂停后服务被系统回收。

预防：所有 Player 操作放主 Looper；命令串行化；生命周期幂等；明确状态机；第一版不使用独立进程。

解决：服务重建时从快照恢复；任何 Controller 断开都不得销毁仍在播放的唯一 Player。

## 4. React Native Bridge

关联：`MainApplication`、新原生模块、`src/utils/nativeModules`。

影响：Controller 未连接、事件丢失、Promise 卡死、重复监听。

预防：唯一 Controller Future；命令队列；超时；事件带 revision；JS 重连主动拉完整状态。

解决：连接失败明确拒绝 Promise；重建页面时先释放旧监听再重新同步。

## 5. TypeScript 播放业务

关联：`src/plugins/player`、`src/core/player`、`src/core/init`、player store。

影响：状态语义变化、事件顺序变化、结束与循环逻辑退化、歌词按钮失效。

预防：建立状态映射表和事件顺序测试；保留现有业务 API；歌词按钮迁移为自定义 Session Command。

解决：发生 UI 与实际播放不一致时，以 Session 快照为权威并重新同步 store。

## 6. QuickJS SourceEngine

关联：`userApi` 原生目录、音源脚本存储和预加载脚本。

影响：前台音源退化、两个 Runtime、脚本崩溃拖垮播放、销毁后回调。

预防：单 Runtime Owner；专用 HandlerThread；统一回调接口；硬超时；销毁时取消请求和定时器；UI 能力与 URL 解析能力分离。

解决：脚本异常只返回解析失败，不终止 PlaybackService；必要时回退到缓存或有效旧 URL。

## 7. 原生 HTTP 桥

关联：QuickJS request/response 协议、Cookie、Header、二进制响应。

影响：与原 axios 行为不一致，导致签名、Cookie、重定向或字符集错误。

预防：制定兼容协议；用本地测试服务覆盖重定向、gzip、Cookie、Range、超时、取消、binary 和非 UTF-8；QuickJS 与 ExoPlayer共享必要网络状态。

解决：以真实历史音源请求做对照测试；禁止无条件或无限重试。

## 8. 播放快照

关联：队列、进度、模式、歌曲身份、URL 时效和元数据。

影响：数据损坏、旧版不兼容、频繁写盘、敏感 Header 泄露。

预防：schemaVersion；原子写；写入节流；大小限制；敏感字段不明文保存；单线程写入。

解决：快照解析失败时安全丢弃；迁移函数必须幂等；不影响应用正常打开。

## 9. 缓存

关联：旧 TrackPlayer 私有缓存目录、Media3 SimpleCache。

影响：格式不兼容、缓存失效、双份占用、清缓存时崩溃。

预防：新缓存使用版本化目录；旧目录只读探测；完整文件可迁移，内部碎片不强行转换；至少保留旧目录一个稳定版本。

解决：迁移中断可重试；无法识别的旧缓存延迟清理。

## 10. Widget

关联：`MusicWidgetProvider`、旧 `MusicWidgetModule` 和 JS 事件链。

影响：冷启动首击无效、状态不同步、外部广播伪造。

预防：Widget 使用 MediaController；显式 PendingIntent；按钮防抖；状态来自 Session；不依赖 JS EventEmitter。

解决：连接期间缓存一次用户命令；连接失败显示静态状态而不是伪装成功。

## 11. 通知、蓝牙与音频焦点

关联：媒体元数据、Session commands、AudioAttributes。

影响：按钮顺序变化、来电不暂停、耳机拔出外放、蓝牙控制错乱。

预防：使用 Media3 标准命令；只让 Media3 管理音频焦点；处理 noisy；自定义歌词命令单独声明；封面限制尺寸。

解决：不无条件自动恢复来电前播放；以用户原播放意图决定是否恢复。

## 12. 外部文件与 Deep Link

关联：`MainActivity` Intent、`content://` 权限、本地文件播放。

影响：Activity 关闭后 URI 失效、Controller 未连接、外部文件被错误送入音源解析。

预防：可持久化时保存 URI 权限，否则复制到私有目录；Intent 命令排队；本地文件使用独立 mediaId 类型。

解决：权限失效时明确报错，不循环解析或重试。

## 13. 动态包名

关联：applicationId、Widget action、Provider、SessionToken、PendingIntent。

影响：QQ 包仍指向默认包名，组件冲突或控制失败。

预防：禁止运行时硬编码包名；组件使用 class 引用；CI 分别构建默认包、QQ 包和自定义包并检查 APK。

解决：发现包名不一致时阻止发布，不通过运行时兼容补丁掩盖。

## 14. R8 与 Release

关联：ProGuard、QuickJS 回调、RN Bridge、序列化模型和 Service。

影响：Debug 正常而 Release 崩溃。

预防：精确 keep 规则；CI 构建签名 minified Release；保存 mapping 和 native symbols；对 Release APK 执行冷启动测试。

解决：使用 mapping 定位混淆问题，只补充必要 keep 规则。

## 核心原则

- 官方 Media3 负责底层协议和播放。
- 项目代码只做业务适配。
- 同一 APK 任何时刻只有一个播放核心。
- 所有关键路径必须可观测、可测试、可回滚。
