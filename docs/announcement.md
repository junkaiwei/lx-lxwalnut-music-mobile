# 公告功能使用文档

## 功能概述

公告功能允许开发者通过远程 JSON 配置，在 App 内弹出公告窗口。支持 Markdown 渲染，包括图片、视频、代码块、可点击链接等。

## 核心特性

| 特性 | 说明 |
|------|------|
| Markdown 渲染 | 支持标题、粗体、斜体、列表、引用、分割线 |
| 图片支持 | `![alt](url)` 插入图片 |
| 视频支持 | `[video](url)` 插入视频播放器 |
| 代码块 | ` ``` ` 包裹代码，支持一键复制 |
| 可点击链接 | `[text](url)` 点击跳转浏览器 |
| 彩色字体 | `<color=#FF0000>红色文字</color>` |
| 按钮系统 | 最多 3 个自定义按钮 |
| 自动关闭按钮 | 按钮 URL 为空时点击关闭弹窗 |
| ID 检测更新 | 服务器 ID 变化时自动弹出 |
| 调试模式 | 开发时可跳过 ID 检查直接弹出 |

## 目录结构

```
src/
├── store/
│   └── announcement/
│       ├── state.ts          # 公告状态定义
│       ├── action.ts         # 状态操作方法
│       └── hook.ts           # React Hook
├── core/
│   └── announcement.ts       # 核心逻辑（检查/显示/关闭）
├── utils/
│   └── announcement.js       # 网络请求 + 调试开关
├── navigation/
│   ├── screenNames.ts        # 屏幕名称定义
│   ├── registerScreens.tsx   # 组件注册
│   ├── utils.ts              # showAnnouncementModal
│   └── components/
│       └── AnnouncementModal.tsx  # 公告弹窗 UI
└── config/
    └── constant.ts           # 存储键定义

publish/
└── announcement.json         # 公告数据源（GitHub 托管）
```

## 文件说明

### `src/utils/announcement.js`

**调试开关配置**

```javascript
// 调试模式：true = 每次启动都弹出，false = 正常模式
export const DEBUG_MODE = true

// 测试模式：true = 使用本地数据，false = 使用远程数据
const TEST_MODE = true
```

**切换模式**

| 场景 | DEBUG_MODE | TEST_MODE |
|------|------------|-----------|
| 开发调试 | `true` | `true` |
| 测试远程数据 | `false` | `false` |
| 正式发布 | `false` | `false` |

### `src/core/announcement.ts`

**导出函数**

```javascript
// 检查公告（App 启动时自动调用）
checkAnnouncement(silent?: boolean)

// 手动显示弹窗
showModal()

// 关闭弹窗
hideModal(componentId)

// 关闭并记录已读
dismissAnnouncement()

// 测试用：清除本地 ID
resetAnnouncementForTest()

// 测试用：强制显示
forceShowAnnouncement()

// 测试用：重新检查
recheckAnnouncement()
```

### `publish/announcement.json`

**JSON 格式**

```json
{
  "announcementId": "v1.0.0",
  "title": "公告标题",
  "content": "Markdown 内容",
  "image": "顶部图片 URL（可选）",
  "buttons": [
    {
      "text": "按钮文字",
      "enabled": true,
      "url": "https://example.com"
    }
  ]
}
```

## Markdown 语法

### 基础语法

| 语法 | 效果 | 示例 |
|------|------|------|
| `# 标题` | 一级标题 | `# 欢迎使用` |
| `## 标题` | 二级标题 | `## 更新内容` |
| `### 标题` | 三级标题 | `### 新增功能` |
| `**粗体**` | 粗体 | `**重要**` |
| `*斜体*` | 斜体 | `*强调*` |
| `` `代码` `` | 行内代码 | `` `npm install` `` |
| `- 列表` | 无序列表 | `- 功能1` |
| `1. 列表` | 有序列表 | `1. 步骤一` |
| `> 引用` | 引用块 | `> 注意事项` |
| `---` | 分割线 | `---` |

### 扩展语法

| 语法 | 效果 | 示例 |
|------|------|------|
| `[text](url)` | 可点击链接 | `[GitHub](https://github.com)` |
| `![alt](url)` | 插入图片 | `![Logo](https://example.com/logo.png)` |
| `[video](url)` | 插入视频 | `[video](https://example.com/video.mp4)` |
| `<color=#FF0000>text</color>` | 彩色字体 | `<color=#FF0000>红色</color>` |
| ` ``` code ``` ` | 代码块（可复制） | 见下方示例 |

### 代码块示例

````markdown
```javascript
const greeting = "Hello!";
console.log(greeting);
```
````

### 完整示例

```markdown
## 更新公告

### 新增功能

- 支持 **Markdown** 渲染
- 支持 `行内代码`
- 支持 <color=#FF0000>彩色文字</color>

### 图片展示

![示例图片](https://example.com/image.png)

### 视频演示

[video](https://example.com/demo.mp4)

### 代码示例

```javascript
console.log("Hello, LX Music!");
```

### 相关链接

访问 [GitHub 仓库](https://github.com/WalnutBai/lx-lxwalnut-music-mobile) 获取更多信息。

---
```

## 按钮配置

### 按钮格式

```json
{
  "text": "按钮文字",
  "enabled": true,
  "url": "https://example.com"
}
```

### 按钮行为

| url 值 | enabled | 点击行为 |
|--------|---------|----------|
| 有链接 | `true` | 打开链接 + 关闭弹窗 |
| 空字符串 | `true` | 直接关闭弹窗 |
| 任意值 | `false` | 按钮不显示 |

### 按钮数量

- 最多 3 个按钮
- `enabled: false` 的按钮会隐藏
- 剩余按钮自动横向排列

### 示例

```json
{
  "buttons": [
    {
      "text": "网盘下载",
      "enabled": true,
      "url": "https://pan.baidu.com/xxx"
    },
    {
      "text": "帮助文档",
      "enabled": true,
      "url": "https://github.com/xxx"
    },
    {
      "text": "关闭",
      "enabled": true,
      "url": ""
    }
  ]
}
```

## ID 更新机制

### 工作原理

1. App 启动时从 GitHub 获取 `announcement.json`
2. 比较本地 `announcementId` 与服务器 `announcementId`
3. 不一致则弹出公告
4. 用户关闭后保存新的 `announcementId`

### 特殊 ID 值

| announcementId | 行为 |
|----------------|------|
| 有效字符串（如 `"v1.0.0"`） | 正常工作 |
| `false` 或 `"false"` | 完全禁用公告 |
| `null` / `undefined` / `""` | 完全禁用公告 |

### 禁用公告

```json
{
  "announcementId": false
}
```

## 调试方法

### 调试控制台命令

```javascript
// 清除本地 ID（下次启动会重新弹出）
import { resetAnnouncementForTest } from '@/core/announcement'
resetAnnouncementForTest()

// 强制重新检查
import { recheckAnnouncement } from '@/core/announcement'
recheckAnnouncement()

// 强制显示（跳过所有检查）
import { forceShowAnnouncement } from '@/core/announcement'
forceShowAnnouncement()
```

### 查看日志

Metro 控制台搜索 `[Announcement]`：

```
[Announcement] Running in TEST MODE, ID: v1.0.0
[Announcement] Starting check, DEBUG_MODE: true
[Announcement] Got info: v1.0.0
[Announcement] DEBUG MODE: Force showing modal
[Announcement] showModal called, current state: false
```

## 发布流程

1. 编辑 `publish/announcement.json`
2. 更新 `announcementId`（如 `"v1.0.1"`）
3. 提交到 GitHub
4. App 启动时自动检测更新

## 常见问题

### Q: 公告没有弹出？

1. 检查 `DEBUG_MODE` 是否为 `true`
2. 检查 `announcementId` 是否为 `false`
3. 执行 `resetAnnouncementForTest()` 清除本地 ID
4. 查看 Metro 控制台日志

### Q: 视频关闭后还在播放？

这是已知问题，已通过以下方式修复：
- 点击任何按钮都会先卸载视频
- 组件卸载时调用 `seek(0)` 重置视频

### Q: 如何测试远程数据？

1. 设置 `TEST_MODE = false`
2. 确保网络可访问 GitHub
3. 重启 App

### Q: 如何完全禁用公告？

在 `announcement.json` 中设置：
```json
{
  "announcementId": false
}
```
