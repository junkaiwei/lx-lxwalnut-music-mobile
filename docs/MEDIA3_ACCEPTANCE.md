# Media3 验收标准

## 自动测试

覆盖 MediaItem 编解码、快照迁移、URL 时效、队列事件顺序、状态映射、SourceEngine、HTTP 兼容、Controller 重连和命令排队。

## 系统矩阵

至少验证 API 23、26、29、31、33、34、35、36。

至少实测 Pixel/AOSP、Samsung、小米、OPPO/OnePlus、vivo。

重点场景：锁屏、省电、划掉最近任务、系统回收、设备重启、蓝牙重连、耳机拔出、电话或导航打断、通知关闭和 Widget 冷启动。

## 音源与网络

覆盖完整缓存、部分缓存、无缓存、有效 URL、403、超时、空 URL、脚本异常、网络切换、Cookie、Referer、重定向和 Range。

## 媒体格式

至少覆盖 MP3、AAC/M4A、FLAC、OGG/Opus、本地 content URI 和长音频 seek。

## 动态包名

分别构建默认包、`com.tencent.qqmusic` 和一个额外自定义包。检查 Service、Receiver、SessionToken、Widget action、Provider authority 和 PendingIntent。

## Release

Debug 与签名 minified Release 均须通过。Release 必须能完成后台播放、蓝牙控制、缓存恢复和 QuickJS URL 刷新。Media3 依赖只能存在一个版本。

## 合并硬门槛

以下任一项失败，不得合并：

1. 存在两个播放 Service、Receiver、播放器实例或媒体通知。
2. 前台播放、暂停、切歌、seek、循环或随机行为退化。
3. minified Release 冷启动失败。
4. QuickJS 或网络异常导致 Service 崩溃或无限等待。
5. 队列、歌曲或位置恢复错误。
6. 覆盖安装后设置或播放数据异常丢失。
7. 动态包名组件仍引用默认包名。
8. 日志包含完整 URL、Cookie、Token 或密钥。
9. 关键 Android 版本未通过。
10. 划掉 UI 后蓝牙或 Widget 控制失效。
11. 普通进程死亡后不能恢复已缓存歌曲。
12. RN 页面重开后创建第二个播放器而非连接既有 Session。

## 发布与回滚

先内部 Debug，再内部 minified Release，再逐步灰度。观察启动成功率、冷恢复成功率、解析耗时、播放错误、ANR、native crash、服务重启和缓存命中率。

播放核心回滚通过发布更高 versionCode 的旧引擎版本完成。新快照和缓存使用独立版本路径，第一稳定版不删除旧数据。
