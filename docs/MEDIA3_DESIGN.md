# Media3 冷启动播放改造设计方案

## 方案 A

目标：在不升级 React Native 和 Android 构建工具链的前提下，迁移到官方 Media3 播放核心。

固定基线：

- React Native 0.73.11
- AGP 8.6.1
- Gradle 8.8
- Kotlin 1.9.24
- compileSdk 35
- Media3 1.9.4

## 设计目标

最终：

- 一个 MediaLibraryService
- 一个 MediaLibrarySession
- 一个 ExoPlayer
- 一个媒体通知
- RN 通过 Bridge 控制
- QuickJS 继续负责音源解析

## 修改前检查要求

任何依赖、工具、组件修改前必须：

1. 检查完整依赖链。
2. 检查 Manifest 合并结果。
3. 检查运行生命周期。
4. 分析影响模块。
5. 发现冲突立即停止并报告。

禁止：

- 双播放器
- 双 Session
- 双媒体通知
- 未验证依赖升级

## 实施阶段

Phase 0：基线审计

输出依赖树、Manifest、播放器链路和测试记录。

Phase 1：Media3 服务骨架

新增 PlaybackService、PlayerFactory、Session Callback，不接管业务。

Phase 2：Native Bridge

新增 Media3 Bridge，保持业务接口兼容。

Phase 3：播放业务迁移

迁移 player adapter、store 和事件映射。

Phase 4：冷启动恢复

增加快照、URL 生命周期和恢复流程。

Phase 5：QuickJS SourceEngine

复用现有音源脚本，不重写解析规则。

Phase 6：Widget 与外部入口

使用 MediaController 控制播放服务。

Phase 7：移除 TrackPlayer Android 运行链。

## 测试要求

每阶段必须验证：

- dependencyInsight
- merged manifest
- Debug 构建
- minified Release
- 蓝牙控制
- 锁屏播放
- Widget
- 冷启动恢复

## 验收原则

- 单一播放器
- 单一 Session
- 单一通知
- 音源能力不退化
- Release 稳定
- 动态包名组件正确
