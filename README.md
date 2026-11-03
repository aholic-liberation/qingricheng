# 轻日程（QingRiCheng）

一个纯 Android 的轻量陪伴 + 日程应用：聊天、日程、周视图、便签、CalDAV 同步和通知提醒，全部跑在本地。

- **Kotlin + Jetpack Compose**（Material 3，暖色纸感主题）
- **纯原生**：没有 WebView、没有 Node、没有 proot、没有内嵌 Linux
- **数据本地**：聊天、日程、便签存在本机 SQLite；API Key 和 CalDAV 密码用 Android Keystore AES/GCM 加密
- **无遥测**：不收集、不上报，只有你配置的模型/日历/搜索端点会收到请求

当前版本：`0.6.1`（versionCode 14，minSdk 30 / targetSdk 37）

## 功能

### 陪聊

- 内置一张示例人设卡，支持导入多张 JSON 人设卡，可多选
- 勾选顺序中第一张为主人设，决定名字、头像、称呼；其余作为补充特质
- 兼容 OpenAI 格式的流式接口，默认 DeepSeek `deepseek-flash`
- 设置里可改 Base URL、模型名和 API Key

### 工具调用

模型可以在对话中直接操作应用数据：

- 日程：创建、查询、修改、删除、上传、同步
- 日历：CalDAV 双向同步（拉取、创建、修改、删除）
- 待办 / 便签：创建、查询
- 长期资料：`list_profile` / `set_profile` / `delete_profile`，带 confidence 阈值
- 联网：网页搜索、读取网页正文、配置 CalDAV 账号

### 附件

聊天输入框支持图片和文档：

- 图片：JPG / PNG / WebP，自动压缩为 JPEG 后按 `image_url` 发送（需要支持视觉的模型）
- 文档：PDF、DOCX、MD、TXT、CSV、JSON、HTML
- PDF 用 PDFBox-Android 抽取正文，DOCX 用 Zip + XmlPullParser 抽取正文
- 旧版 `.doc` 不支持，请另存为 `.docx` 或 `.pdf`

### 日程与周视图

- 日程 tab：月历 + 当日列表
- 周视图：7 天时间网格；点击事件查看标题、时间、地点、备注，可删除或修改
- 新建/修改事件复用同一张表单
- 本地日程与远程日程用不同颜色的色条区分

### 便签

本地彩色便签，支持完成和删除。

### 提醒

- 使用 AlarmManager 排程，不依赖轮询
- 提前时间可在设置页选择 5 / 10 / 15 / 30 / 60 分钟
- 支持开机重排、测试通知、跳转系统通知设置

### 关于我

在设置页内维护年级、身份、学校、专业、称呼、城市、习惯等长期资料，可手动增删改，也可由模型在聊天中采集。

### 界面

- 顶部人设栏和底部「输入框 + 标签栏」是一整块 Liquid Glass 面板
- 使用 [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 做真实背景折射、色散、高光、内外阴影
- 底部胶囊随 tab 弹簧移动，高度为栏高的 0.618
- 底边 3cm 渐进模糊，ease-in 曲线
- 配色以暖纸和墨色为底，钴蓝只留给发送按钮和「今天」圆环

## 技术栈

| 层 | 实现 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 玻璃 / 折射 | Backdrop 1.0.2（AGSL / RenderEffect） |
| 数据 | SQLiteOpenHelper |
| 网络 | OkHttp |
| HTML 解析 | Jsoup |
| PDF | PDFBox-Android |
| DOCX | Zip + XmlPullParser |
| 密钥存储 | Android Keystore AES/GCM |
| 后台任务 | WorkManager |
| 提醒 | AlarmManager |

## 构建

需要：

- JDK 17
- Android SDK Platform 37 / Build-Tools 36
- Gradle 由 wrapper 提供，无需单独安装

```bash
./gradlew :app:assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

Release 构建：

```bash
./gradlew :app:assembleRelease
```

仓库不包含签名密钥，Release 产物默认未签名；请自行在 `app/build.gradle.kts` 里加 signingConfig。

`app/libs/` 下是随仓库携带的依赖（PDFBox-Android 2.0.27.0、BouncyCastle 1.72）。保留它们是为了在离线环境里也能构建。

## 配置

所有配置都在应用内完成，不需要改代码：

- **模型**：设置 → 模型，填 Base URL、模型名、API Key
- **CalDAV**：设置 → CalDAV 日历，填地址、账号、密码；钉钉日历地址形如 `https://calendar.dingtalk.com/dav/<用户id>/primary/`
- **搜索**：默认 Bing 网页搜索（免 Key），也可切换 Tavily / Serper

API Key 和 CalDAV 密码只保存在本机，使用 Android Keystore 加密。

## 目录

```
app/src/main/java/com/qingricheng/app/
  data/      模型、SQLite、设置、加密存储、人设解析、附件
  domain/    人设提示词、工具注册、时间工具
  net/       LLM、CalDAV、搜索客户端
  notify/    通知渠道、提醒排程与接收器
  sync/      WorkManager 同步任务
  ui/        Compose 页面与主题
docs/personas/   人设卡格式说明与示例
tools/           本地 mock LLM / CalDAV，用于离线开发
```

## 本地开发

两个 mock 服务：

```bash
python3 tools/mock_llm.py     # 端口 19090
python3 tools/mock_caldav.py  # 端口 19091
```

模拟器里把 Base URL 指向 `http://10.0.2.2:19090/v1` 即可。

`network_security_config.xml` 只对 `10.0.2.2`、`localhost`、`127.0.0.1` 放开明文，其他端点必须走 HTTPS。

## 已知限制

- 图片需要支持视觉的模型端点，纯文本模型会拒绝
- 旧版 `.doc` 不支持
- 提醒使用 inexact `setAndAllowWhileIdle`，Doze / 省电模式可能延迟
- 部分 CalDAV 服务器（如钉钉）不接受对已有资源的 PUT，更新会退化为「新建资源 + 删除旧资源」
- 玻璃透镜需要 Android 13+；Android 12 只有模糊，Android 11 退化为半透明色块
- 目前只有单会话，历史会话导入和多会话列表还没做

## 第三方依赖

- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) — Apache-2.0
- [Bouncy Castle](https://www.bouncycastle.org/) — MIT
- [OkHttp](https://square.github.io/okhttp/) — Apache-2.0
- [Jsoup](https://jsoup.org/) — MIT
- [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) — MIT
- AndroidX / Jetpack Compose — Apache-2.0

各组件的许可证以其自身文件为准。

## License

[MIT](LICENSE) © 2026 The QingRiCheng Authors
