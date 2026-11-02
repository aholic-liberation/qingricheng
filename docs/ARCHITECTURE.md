# 轻日程 · 代码架构与底层说明

> 适用版本：0.6.0（versionCode 13）
> 本文只描述代码本身，不包含部署、签名和任何账号信息。

## 1. 一句话概括

轻日程是一个**单 Activity、纯 Compose、无 WebView** 的 Android 应用。它把「聊天 / 日程 / 周视图 / 便签 / 设置」五个页面放在同一个 Activity 里，用一份手写的依赖容器串起 SQLite、网络客户端、工具调用和系统提醒。

没有 Node、没有 proot、没有内嵌 Linux，也没有跨端运行时。APK 里跑的就是 Kotlin 编译出的 DEX。

---

## 2. 技术栈与工程配置

| 项 | 值 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3（Compose BOM 2025.10.01） |
| 玻璃 / 折射 | Backdrop 1.0.2（`io.github.kyant0:backdrop`） |
| 构建 | Gradle 9.3.1 + AGP 9.1.1 |
| minSdk / targetSdk / compileSdk | 30 / 37 / 37 |
| 数据库 | 原生 `SQLiteOpenHelper`（不是 Room） |
| 网络 | OkHttp 5.5.1；LLM 走手写 SSE，CalDAV 走 WebDAV/XML |
| HTML 解析 | Jsoup 1.18.3 |
| PDF / DOCX | PDFBox-Android 2.0.27.0；DOCX 用 `Zip + XmlPullParser` |
| 密钥存储 | Android Keystore（AES/GCM） |
| 后台 | WorkManager（周期同步）+ AlarmManager（提醒） |
| 依赖注入 | 手写 `AppContainer`（没有 Hilt/Koin） |
| 导航 | 无 Navigation 路由，`AppRoot` 用 `tab: Int` 切换 |

---

## 3. 包结构

```text
app/src/main/java/com/qingricheng/app/
├── QingApp.kt                  Application：创建容器、通知渠道、启动任务
├── MainActivity.kt             唯一 Activity：edge-to-edge、隐藏状态栏、请求通知权限
├── AppContainer.kt             手动依赖容器（组合根）
├── data/
│   ├── AppDb.kt                SQLiteOpenHelper + DAO 方法
│   ├── Models.kt               PersonaCard / ChatMessage / EventItem / TaskItem / StickyItem ...
│   ├── ChatRepository.kt       消息读写、欢迎语
│   ├── ScheduleRepository.kt   日程/待办读写 + 提醒排程
│   ├── NotesRepository.kt      便签
│   ├── PersonaRepository.kt    人设卡导入/启用/删除
│   ├── MemoryRepository.kt     长期记忆
│   ├── ProfileRepository.kt    「关于我」资料
│   ├── PersonaParser.kt        人设 JSON 解析
│   ├── AttachmentStore.kt      附件导入、图片压缩、PDF/DOCX 抽正文
│   ├── SettingsStore.kt        SharedPreferences 配置
│   ├── SecretStore.kt          Android Keystore 加密存储
│   └── MessageMeta.kt          消息附件元数据编解码
├── domain/
│   ├── PersonaPrompt.kt        组装 system prompt
│   ├── ToolRegistry.kt         20 个工具的定义与分发
│   └── TimeUtils.kt            日期/时间/时区工具
├── net/
│   ├── DeepSeekClient.kt       OpenAI 兼容的流式客户端
│   ├── CalDavClient.kt         CalDAV 读写
│   └── SearchClient.kt         Bing / Tavily / Serper + 网页正文
├── notify/
│   ├── Notifications.kt        通知渠道与通知构建
│   ├── ReminderScheduler.kt    AlarmManager 排程
│   ├── ReminderReceiver.kt     到点广播
│   └── BootReceiver.kt         开机重排
├── sync/
│   ├── CalDavSyncWorker.kt     周期拉取
│   └── ReminderWorker.kt       辅助提醒任务
└── ui/
    ├── AppRoot.kt              五个 tab 的根
    ├── theme/Theme.kt          纸感主题与颜色
    ├── chat/                   ChatScreen + ChatViewModel
    ├── schedule/               ScheduleScreen / WeekScreen + ScheduleViewModel
    ├── notes/                  NotesScreen + NotesViewModel
    └── settings/               SettingsScreen + SettingsViewModel
```

---

## 4. 启动与依赖注入

```text
QingApp.onCreate()
  ├─ AppContainer(this)            → 创建 db / settings / secrets / repos / clients / tools
  ├─ Notifications.ensureChannel()
  ├─ 后台协程：seedExampleIfNeeded → ensureGreeting → rescheduleAll
  └─ WorkManager 注册每 2 小时的 CalDAV 周期同步

MainActivity.onCreate()
  ├─ enableEdgeToEdge()
  ├─ 隐藏状态栏（可上滑临时显示）
  ├─ Android 13+ 请求 POST_NOTIFICATIONS
  └─ setContent { QingRiChengTheme { AppRoot(container) } }
```

`AppContainer` 是**组合根**，所有单例都在这里 new 出来：

```kotlin
class AppContainer(context: Context) {
    val settings = SettingsStore(appContext)
    val secrets = SecretStore(appContext)
    val db = AppDb(appContext)
    val personas = PersonaRepository(...)
    val chat = ChatRepository(db)
    val schedule = ScheduleRepository(db, appContext)
    val notes = NotesRepository(db)
    val profile = ProfileRepository(db)
    val attachments = AttachmentStore(appContext)
    val memory = MemoryRepository(db)
    val llm = DeepSeekClient()
    val calDav = CalDavClient(settings, secrets)
    val search = SearchClient(settings, secrets)
    val tools = ToolRegistry(this)
}
```

ViewModel 通过 `viewModelFactory` 拿到 container，不使用注解注入：

```kotlin
val chatVm: ChatViewModel = viewModel(
    key = "chat",
    factory = viewModelFactory { initializer { ChatViewModel(container) } }
)
```

---

## 5. 分层职责

### 5.1 UI 层

- `AppRoot` 持有 `tab: Int`，用 `when (tab)` 在五个页面之间切换。
- 底部是「输入框 + 五个单字标签」合并成的一整块玻璃面板：聊 / 日 / 周 / 贴 / 设。
- 键盘弹出时标签行隐藏，只保留输入框；输入区用 `imePadding()` 贴住键盘。
- 顶部人设栏和底部面板使用 Backdrop 玻璃：`rememberLayerBackdrop` 记录页面，`drawBackdrop` 叠加 `vibrancy()`、`blur()`、`lens()` 和内置高光/阴影。
- 底部胶囊随 tab 做 spring 移动，高度为栏高的 0.618。
- 底边 189dp（≈3cm）渐进模糊：`drawPlainBackdrop` + `blur()` + ease-in alpha mask。
- 配色以暖纸和墨色为主，钴蓝只用于发送按钮和「今天」圆环；用户气泡/头像/链接降为暖中性色。
- 没有 WebView，所有界面都是原生 Composable。

**渲染边界**：玻璃依赖 `io.github.kyant0:backdrop`。透镜折射需要 Android 13+（`RuntimeShader`），Android 12 只有 `RenderEffect` 模糊，Android 11 退化为半透明 tint。

### 5.2 ViewModel 层

| ViewModel | 主要状态 | 主要动作 |
|---|---|---|
| `ChatViewModel` | 消息、流式文本、工具状态、搜索来源、待发送附件 | send / stop / clear / attach |
| `ScheduleViewModel` | 月份、选中日期、日程、待办、同步状态 | 选择日期、翻月/翻周、增删改、CalDAV 同步 |
| `NotesViewModel` | 便签列表 | 新增、完成、删除 |
| `SettingsViewModel` | 人设、模型配置、CalDAV 配置、搜索配置、提醒提前量、关于我 | 保存/测试/导入/删除 |

所有状态用 `MutableStateFlow<UiState>` 暴露，UI 用 `collectAsState()` 订阅。耗时操作在 `Dispatchers.IO`，UI 更新切回 `Dispatchers.Main`。

### 5.3 数据层

`AppDb` 继承 `SQLiteOpenHelper`，数据库名 `qingricheng.db`，版本 2。

| 表 | 关键列 | 用途 |
|---|---|---|
| `personas` | id, name, avatar, tagline, json, builtin | 人设卡 |
| `messages` | id, role, content, createdAt, meta | 聊天记录；meta 存附件/工具/搜索来源的 JSON |
| `events` | id, uid, href, etag, calendarUrl, title, date, start, end, endDate, allDay, location, description, local | 日程；`local=0` 表示来自 CalDAV |
| `tasks` | id, title, date, done | 每日待办 |
| `stickies` | id, text, color, done | 便签 |
| `memories` | id, text, source | 长期记忆 |
| `profiles` | id, label, value | 「关于我」长期资料 |

`onUpgrade` 目前只做 v1 → v2：补 `profiles` 表和索引。加字段时在这里加迁移。

Repository 很薄，基本是对 `AppDb` 的封装；`ScheduleRepository` 额外负责在日程写入/删除时排程或取消提醒。

### 5.4 领域层

- `PersonaPrompt.systemPrompt()`：把人设卡、长期记忆、关于我资料、当前时间拼成 system prompt。多张人设卡时，第一张为主，其余为补充。
- `ToolRegistry`：定义 20 个 function-calling 工具，`execute()` 按名字分发到 repository 或网络客户端。工具异常会被捕获成 `ToolExecution` 失败结果，交给模型解释，不会让整个对话崩掉。
- `TimeUtils`：本地日期、时间、时区换算，统一用 `Asia/Shanghai` 作为默认时区。

### 5.5 网络层

**DeepSeekClient**

- `POST {baseUrl}/chat/completions`，`stream=true`，`temperature=0.85`。
- 超时：connect 30s / read 180s / write 30s。
- 手写 SSE：逐行读 `data:`，忽略空行和 `[DONE]`。
- `tool_calls` 按 `index` 增量聚合，最后一个 chunk 才拿到完整 arguments。
- 支持 `image_url` 多模态消息；纯文本模型会拒绝图片。

**CalDavClient**

- `PROPFIND` 测试连接并取 displayname。
- `REPORT` + `calendar-query` 拉取时间范围内的 VEVENT；先试带 `expand` 的请求，失败再退回不带 expand 的版本。
- `PUT` 新建：`If-None-Match: *`，文件名由 UID 的 SHA-1 生成。
- `PUT` 更新：带 `If-Match`（清洗过 `W/null`）；部分服务器（如钉钉）对已有资源 PUT 返回 400/405/501，此时自动改为「新建新资源 + 删除旧资源」。
- `DELETE`：带 `If-Match`；404 视为已删除，400/412 时按 UID 候选路径重试。
- 所有远程日程都保留 `uid`、`href`、`etag`、`calendarUrl`，用于判断更新路径。

**SearchClient**

- 默认 Bing 网页搜索（免 Key），解析结果页。
- 可切换 Tavily / Serper，Key 存在 SecretStore。
- `fetchUrl` 用 Jsoup 抽正文，结果以来源卡片保留在对话里。

### 5.6 后台与通知

- **提醒**：`ReminderScheduler.schedule()` 用 `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)`，触发时间 = 事件开始时间 − 提前分钟。不使用精确闹钟权限，Doze/省电模式可能延迟。
- **到点**：`ReminderReceiver` 收到广播后构建通知；同一事件用 `event.id|date|start` 去重。
- **开机**：`BootReceiver` 重新排未来 60 天的提醒。
- **周期同步**：`CalDavSyncWorker` 每 2 小时拉一次当前月视图范围的日程（`gridStart − 7 天` 到 `gridStart + 48 天`），写入 SQLite，并清理远端已消失的本地缓存。
- **进程死亡**：没有常驻 Service；已排的 AlarmManager 和 WorkManager 任务仍会被系统唤醒。

---

## 6. 关键数据流

### 6.1 聊天 + 工具循环

```text
用户输入（可带图片/文档）
  → ChatViewModel.send()
  → PersonaPrompt.systemPrompt(人设 + 记忆 + profile + 当前时间)
  → 取最近 24 条历史；prompt 文本上限 60,000 字符，单条历史上限 20,000
  → DeepSeekClient.stream()  ──SSE──▶  OpenAI 兼容端点
  ◀── text delta / tool_calls ──
  → 有 tool_calls：
       ToolRegistry.execute(call)
         → SQLite / CalDAV / 搜索 / 网页
       → 结果作为 role=tool 回填
       → 最多 4 轮
  → 最终文本写入 messages 表
  → StateFlow 更新 → Compose 重组
```

- 流式文本先显示在 `streamingText`，结束后落库。
- 工具执行期间显示「正在写日程…」「正在联网搜索…」等状态。
- 搜索结果以来源卡片形式保留在消息 meta 里。
- 用户点「停止」会取消协程，已生成的部分文本仍会保存并标注「（已停止）」。

### 6.2 日程创建 / 修改 / 删除

```text
新建
  → ScheduleViewModel.createEvent()
  → ScheduleRepository.upsertEvent()
       ├─ SQLite 写入
       └─ ReminderScheduler.schedule()
  → CalDAV 已配置时：CalDavClient.createEvent()
       └─ 回写 uid / href / etag / calendarUrl / local=0

修改
  → 更新本地
  → 有 href：CalDavClient.updateEvent()
       └─ 服务器拒绝 PUT 时：新建替代资源 + 删除旧资源
  → 无 href（本地日程）：按本地处理

删除
  → CalDavClient.deleteEvent()
       └─ 404 视为已删除；400/412 按 UID 候选路径重试
  → 删除本地 + 取消提醒
```

### 6.3 提醒

```text
事件写入
  → AlarmManager.setAndAllowWhileIdle(
        RTC_WAKEUP,
        开始时间 - reminderLeadMinutes,
        PendingIntent → ReminderReceiver
     )
到点
  → ReminderReceiver
  → Notifications.showEvent()
  → 记录 fired 集合，避免重复提醒
```

### 6.4 附件

```text
选图/选文件
  → AttachmentStore.import()
       ├─ 文件上限 25MB
       └─ 复制到应用私有目录
  → AttachmentStore.prepare()
       ├─ 图片：最长边压到 1600px，JPEG 质量 82，转 base64 data URL
       └─ 文档：PDF(PDFBox) / DOCX(Zip+XmlPullParser) / 文本类直接读
  → 文本上限 30,000 字符
  → 图片走 image_url，文档正文拼进 prompt
```

### 6.5 人设、记忆、关于我

- **人设卡**：JSON 导入，支持单卡、数组和 `{ "personas": [...] }`。勾选顺序决定主/次。
- **长期记忆**：`remember` / `recall` 工具读写 `memories` 表；仓库最多取最近 80 条，拼进 prompt 时取最近 60 条。
- **关于我**：`list_profile` / `set_profile` / `delete_profile` 工具读写 `profiles` 表；`set_profile` 带 confidence，低于阈值不写入。
- 三者在每次请求前拼进 system prompt，不额外占上下文。

---

## 7. 工具清单（20 个）

| 分组 | 工具 | 作用 |
|---|---|---|
| 日程 | `create_event` | 新建日程 |
| 日程 | `list_events` | 查询日期范围内的日程 |
| 日程 | `update_event` | 修改日程并写回 CalDAV |
| 日程 | `delete_event` | 删除日程（远程同时删除） |
| 日程 | `upload_event` | 把本地日程上传到 CalDAV |
| 日程 | `sync_calendar` | 双向同步 |
| 待办 | `create_task` / `list_tasks` / `update_task` | 新建、查询、完成/取消完成 |
| 便签 | `create_sticky` / `list_stickies` | 新建、查询 |
| 联网 | `web_search` | Bing / Tavily / Serper |
| 联网 | `fetch_url` | 读取网页正文 |
| 记忆 | `remember` / `recall` | 写入、检索长期记忆 |
| 日历 | `configure_caldav` | 保存账号并测试连接 |
| 关于我 | `list_profile` / `set_profile` / `delete_profile` | 长期资料增删查 |
| 时间 | `get_current_time` | 当前日期、时间、时区 |

工具定义是 OpenAI function-calling schema，参数用 `JSONObject` 构建；新增工具需要同时改 `ToolRegistry.definitions()` 和 `execute()` 的 `when`。

---

## 8. 并发与生命周期

- 所有 ViewModel 用 `viewModelScope` 启动协程；IO 操作切 `Dispatchers.IO`，UI 更新切 `Dispatchers.Main`。
- 流式请求是 `Flow<StreamEvent>`，`collect` 在 IO 协程里执行；取消 Job 会取消底层 OkHttp Call。
- 数据库操作是同步的，全部在 IO 线程执行；UI 层不直接碰 `SQLiteDatabase`。
- 每 2 小时的 CalDAV 同步由 `CoroutineWorker` 执行，网络不可用时 `Result.retry()`。
- 提醒不依赖进程存活，AlarmManager 到点会拉起广播接收器。

---

## 9. 安全与隐私

- **API Key 和 CalDAV 密码**：`SecretStore` 用 Android Keystore 生成 AES/GCM 密钥，密文存 SharedPreferences；密钥不出 Keystore。
- **普通配置**：SharedPreferences 明文，但只包含 Base URL、模型名、时区、提醒提前量等非敏感项。
- **网络**：`network_security_config.xml` 只对 `10.0.2.2`、`localhost`、`127.0.0.1` 放开明文，其他端点强制 HTTPS。
- **无遥测**：应用不包含统计 SDK，不会主动上报任何数据；请求只发往用户自己配置的模型、日历和搜索端点。
- **数据本地**：聊天、日程、待办、便签、记忆、资料都存在 `qingricheng.db`，卸载应用即删除。
- **工具边界**：模型只能通过 `ToolRegistry` 暴露的 20 个工具操作数据，不能直接访问文件系统或任意网络地址。

---

## 10. 扩展指南

### 加一个工具

1. 在 `ToolRegistry.definitions()` 里加 `def(...)`，写清参数和 required。
2. 在 `execute()` 的 `when` 里加分支。
3. 实现对应的 `private fun`，返回 `ToolExecution(payload, summary, sources)`。
4. 如果需要在 UI 上显示状态，在 `ChatViewModel.toolStatus()` 里加文案。

### 加一个页面

1. 在 `ui/` 下新建 `XxxScreen.kt` 和 `XxxViewModel.kt`。
2. 在 `AppRoot` 的 `TABS` 里加一项。
3. 在 `when (tab)` 里加分支，并在 `LaunchedEffect(tab)` 里触发数据刷新。
4. 如果页面需要跨页共享状态，复用现有 ViewModel 或新建一个挂在 `AppRoot` 上的 ViewModel。

### 换模型 / 换端点

- 设置页直接改 Base URL、模型名、API Key，不需要改代码。
- 端点必须是 OpenAI 兼容的 `/chat/completions`，支持 `stream=true` 和 `tools`。
- 图片输入需要支持 `image_url` 的多模态模型。

### 加搜索提供方

- 在 `SearchClient` 里加分支，Key 存 `SecretStore`，并在设置页加选项。
- 返回统一的 `SearchResult(title, url, snippet)`。

### 加数据库字段

1. 提高 `AppDb` 的版本号。
2. 在 `onUpgrade` 里写 `ALTER TABLE` 或重建表。
3. 更新 `Models.kt` 和对应的 DAO 读写方法。

---

## 11. 已知限制

- 只有单会话，没有会话列表和历史导入；消息一次读取最近 200 条，没有分页。
- 工具循环最多 4 轮，复杂任务可能需要更多轮。
- 提醒是 inexact alarm，Doze / 省电模式可能延迟；事件开始超过约 5 分钟不再补发。
- 旧版 `.doc` 不支持，需要转 `.docx` 或 `.pdf`。
- 图片需要支持视觉的模型端点。
- 部分 CalDAV 服务器不接受对已有资源的 `PUT`，更新会退化为「新建 + 删除」。
- minSdk 30：玻璃透镜需要 Android 13+；Android 12 只有模糊，Android 11 退化为半透明色块。
- 没有分页：消息一次取最近 200 条，日程按日期范围查询，便签全量读取。

---

## 12. 文件地图

| 关注点 | 从哪里看 |
|---|---|
| 应用启动 | `QingApp.kt`、`MainActivity.kt` |
| 依赖装配 | `AppContainer.kt` |
| 页面切换与底栏 | `ui/AppRoot.kt` |
| 聊天与工具循环 | `ui/chat/ChatViewModel.kt`、`domain/ToolRegistry.kt` |
| 流式协议 | `net/DeepSeekClient.kt` |
| 日程与 CalDAV | `ui/schedule/ScheduleViewModel.kt`、`net/CalDavClient.kt` |
| 数据库 | `data/AppDb.kt` |
| 提醒 | `notify/ReminderScheduler.kt`、`notify/ReminderReceiver.kt` |
| 密钥 | `data/SecretStore.kt` |
| 附件 | `data/AttachmentStore.kt` |
| 人设提示词 | `domain/PersonaPrompt.kt` |
| 主题 / 色彩 | `ui/theme/Theme.kt` |
| 玻璃几何 | `ui/glass/GlassSpec.kt` |

---

## 13. 构建与本地 mock

```bash
# Debug
./gradlew :app:assembleDebug

# Release（仓库不含签名密钥，需自行配置 signingConfig）
./gradlew :app:assembleRelease
```

需要 JDK 17 和 Android SDK Platform 37。

本地 mock：

```bash
python3 tools/mock_llm.py     # 19090
python3 tools/mock_caldav.py  # 19091
```

模拟器里把 Base URL 指向 `http://10.0.2.2:19090/v1`。

---

## 14. 设计取舍

**选择手写而不是框架的地方**

- 不用 Room：表结构简单，直接 `SQLiteOpenHelper` 少一层注解处理，启动更快。
- 不用 Hilt/Koin：只有一个 `AppContainer`，没有多模块和复杂作用域。
- 不用 Retrofit：LLM 需要手写 SSE 和增量 `tool_calls` 聚合，OkHttp 直接控制更简单。
- 不用 Navigation Compose：只有五个平级 tab，`when (tab)` 足够。

**代价**

- 单模块，代码全在 `:app` 里，没有按 feature 拆模块。
- 工具分发是硬编码 `when`，工具多起来会变长。
- 没有分页和增量加载，数据量大时需要补。
- ViewModel 直接依赖 `AppContainer`，测试时要构造完整容器。

这些取舍的目标是：**体积小、启动快、行为可预测**，而不是追求大型工程的标准分层。
