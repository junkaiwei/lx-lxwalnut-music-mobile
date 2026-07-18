# Media3 冷启动播放改造计划

目标分支：`feat/media3-cold-resume`
基线分支：`build/packet-name`

## 总目标

将 Android 端 TrackPlayer 运行时替换为单一官方 Media3 播放核心，同时保留现有 UI、业务状态和 QuickJS 音源脚本。

最终结构：

- 一个 `MediaLibraryService`
- 一个 `MediaLibrarySession`
- 一个 ExoPlayer 实例
- 一条媒体通知
- React Native 通过原生 Bridge 控制播放器
- QuickJS 继续负责音源 URL 解析
- 有缓存时不启动 React Native 即可恢复
- URL 失效时由可复用 QuickJS 引擎重新解析

## 不在本次范围

- 不用 Kotlin 重写音源脚本
- 不重做 UI
- 不迁移 iOS
- 不实现蓝牙连接即自动播放
- 不绕过 Android force-stop 限制
- 不允许 TrackPlayer 与 Media3 两套播放器同时运行

## 文档

- `MEDIA3_PHASES.md`：分步实现顺序
- `MEDIA3_IMPACT.md`：关联模块、影响、预防与解决措施
- `MEDIA3_ACCEPTANCE.md`：测试矩阵与合并门槛
