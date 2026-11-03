package com.qingricheng.app.domain

import com.qingricheng.app.data.EventItem
import com.qingricheng.app.data.SearchResult
import com.qingricheng.app.data.TaskItem
import com.qingricheng.app.net.ApiToolCall
import com.qingricheng.app.net.CalDavClient
import com.qingricheng.app.net.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ToolExecution(
    val name: String,
    val payload: String,
    val summary: String,
    val sources: List<SearchResult> = emptyList()
)

class ToolRegistry(private val container: com.qingricheng.app.AppContainer) {
    fun definitions(): List<ToolDefinition> = listOf(
        def(
            "create_event",
            "在用户日历里新建一条日程。日期用 yyyy-MM-dd，时间用 24 小时 HH:mm；用户说“明天下午三点”时先换算成本地日期时间。",
            obj(
                "title" to str("日程标题"),
                "date" to str("日期 yyyy-MM-dd"),
                "start" to str("开始时间 HH:mm；全天日程可省略"),
                "end" to str("结束时间 HH:mm；可省略"),
                "all_day" to bool("是否全天"),
                "location" to str("地点，可省略"),
                "description" to str("备注，可省略")
            ),
            "title", "date"
        ),
        def(
            "list_events",
            "查看某个日期范围内的日程。",
            obj(
                "start" to str("开始日期 yyyy-MM-dd，默认今天"),
                "end" to str("结束日期 yyyy-MM-dd，默认七天后")
            )
        ),
        def(
            "update_event",
            "修改一条已存在的日程并写回 CalDAV；本地未上传的日程会先上传。只传需要改的字段，id 来自 list_events。",
            obj(
                "id" to str("日程 id，来自 list_events"),
                "title" to str("新标题，可省略"),
                "date" to str("新日期 yyyy-MM-dd，可省略"),
                "start" to str("新开始时间 HH:mm，可省略"),
                "end" to str("新结束时间 HH:mm，可省略"),
                "all_day" to bool("是否改为全天，可省略"),
                "location" to str("新地点，可省略"),
                "description" to str("新备注，可省略")
            ),
            "id"
        ),
        def(
            "delete_event",
            "删除一条日程；CalDAV 日程会同时从服务器删除，本地日程只删本地。id 来自 list_events。",
            obj("id" to str("日程 id，来自 list_events")),
            "id"
        ),
        def(
            "upload_event",
            "把一条已存在的本地日程上传到 CalDAV；如果已有远程链接则更新远程。id 来自 list_events。",
            obj("id" to str("日程 id，来自 list_events")),
            "id"
        ),
        def(
            "sync_calendar",
            "双向同步日程：先把本地未上传的日程上传到 CalDAV，再拉取服务器日程并清理已删除项。",
            obj(
                "start" to str("开始日期 yyyy-MM-dd，默认今天"),
                "end" to str("结束日期 yyyy-MM-dd，默认三十天后")
            )
        ),
        def(
            "create_task",
            "新建一件待办（轻量任务，只存在本机）。",
            obj(
                "title" to str("待办内容"),
                "date" to str("日期 yyyy-MM-dd，默认今天")
            ),
            "title"
        ),
        def(
            "list_tasks",
            "查看某一天的待办。",
            obj("date" to str("日期 yyyy-MM-dd，默认今天"))
        ),
        def(
            "update_task",
            "把某件待办标记为完成或未完成；id 来自 list_tasks。",
            obj("id" to str("待办 id"), "done" to bool("是否完成")),
            "id", "done"
        ),
        def(
            "web_search",
            "联网搜索最新信息。默认使用 Bing 网页搜索（无需 Key）；也可以在设置里改用 Tavily/Serper。",
            obj(
                "query" to str("搜索关键词"),
                "max_results" to num("返回条数，1-10，默认 5")
            ),
            "query"
        ),
        def(
            "fetch_url",
            "读取一个网页的正文，用于在搜索结果里深入查看。",
            obj("url" to str("http/https 网页地址")),
            "url"
        ),
        def(
            "remember",
            "记住一条用户长期偏好或事实，之后会放进人设上下文。",
            obj("text" to str("要记住的内容")),
            "text"
        ),
        def(
            "recall",
            "在长期记忆里查找和关键词有关的内容。",
            obj("query" to str("关键词")),
            "query"
        ),
        def(
            "configure_caldav",
            "配置 CalDAV 日历账号并立即测试连接、同步当前月份的日程。用户明确给出账号和密码时调用；钉钉账号可省略地址，会自动补全；密码只保存在本机。",
            obj(
                "url" to str("CalDAV 地址，可省略；钉钉默认 https://calendar.dingtalk.com/dav/<账号>/primary/"),
                "username" to str("CalDAV 账号"),
                "password" to str("密码或应用专用密码"),
                "timezone" to str("时区，默认 Asia/Shanghai")
            ),
            "username", "password"
        ),
        def(
            "list_profile",
            "查看关于用户的长期资料（年级、身份、学校、专业、称呼、长期习惯等）。",
            obj()
        ),
        def(
            "set_profile",
            "新增或更新一条关于用户的长期资料；label 相同会覆盖旧值。用户明确提供稳定资料时调用；不确定时先追问，不要猜。",
            obj(
                "label" to str("资料项名称，如 年级/身份/学校/专业/称呼/城市/习惯"),
                "value" to str("资料内容"),
                "confidence" to num("置信度 0-1；用户明确说出时应 >=0.8，模糊或猜的低置信不要写")
            ),
            "label", "value"
        ),
        def(
            "delete_profile",
            "删除一条关于用户的长期资料；按 id 或 label 删除。",
            obj(
                "id" to str("资料 id，可省略"),
                "label" to str("资料项名称，可省略")
            )
        ),
        def(
            "get_current_time",
            "获取当前本地日期、时间和时区。",
            obj()
        )
    )

    suspend fun execute(call: ApiToolCall): ToolExecution = withContext(Dispatchers.IO) {
        try {
            val args = if (call.arguments.isBlank()) JSONObject() else JSONObject(call.arguments)
            when (call.name) {
                "create_event" -> createEvent(args)
                "list_events" -> listEvents(args)
                "update_event" -> updateEvent(args)
                "upload_event" -> updateEvent(args)
                "delete_event" -> deleteEvent(args)
                "sync_calendar" -> syncCalendar(args)
                "create_task" -> createTask(args)
                "list_tasks" -> listTasks(args)
                "update_task" -> updateTask(args)
                "web_search" -> webSearch(args)
                "fetch_url" -> fetchUrl(args)
                "remember" -> remember(args)
                "recall" -> recall(args)
                "configure_caldav" -> configureCaldav(args)
                "list_profile" -> listProfile()
                "set_profile" -> setProfile(args)
                "delete_profile" -> deleteProfile(args)
                "get_current_time" -> currentTime()
                else -> failure(call.name, "未知工具：${call.name}")
            }
        } catch (e: Exception) {
            failure(call.name, e.message ?: "工具执行失败")
        }
    }

    private fun createEvent(args: JSONObject): ToolExecution {
        val title = args.optString("title").trim()
        if (title.isEmpty()) return failure("create_event", "缺少日程标题")
        val date = args.optString("date").trim().ifEmpty { TimeUtils.todayKey() }
        val dateParsed = TimeUtils.parseDate(date) ?: return failure("create_event", "日期格式应为 yyyy-MM-dd")
        val allDay = args.optBoolean("all_day", false)
        val start = if (allDay) "" else args.optString("start").trim().ifEmpty { "09:00" }
        var end = if (allDay) "" else args.optString("end").trim()
        if (!allDay) {
            if (TimeUtils.parseTime(start) == null) return failure("create_event", "开始时间格式应为 HH:mm")
            if (end.isNotEmpty() && TimeUtils.parseTime(end) == null) return failure("create_event", "结束时间格式应为 HH:mm")
            if (end.isEmpty()) {
                end = LocalTime.parse(start).plusHours(1).toString().take(5)
            }
        }
        val event = EventItem(
            id = "local_" + UUID.randomUUID().toString().take(12),
            title = title,
            date = date,
            start = start,
            end = end,
            allDay = allDay,
            location = args.optString("location").trim(),
            description = args.optString("description").trim(),
            local = true
        )
        val caldav = container.calDav
        if (caldav.isConfigured()) {
            val remote = caldav.createEvent(event)
            if (remote.isSuccess) {
                val saved = remote.getOrThrow()
                container.schedule.upsertEvent(saved)
                return ToolExecution(
                    "create_event",
                    JSONObject().put("ok", true).put("id", saved.id).put("title", saved.title)
                        .put("date", saved.date).put("start", saved.start).put("end", saved.end)
                        .put("all_day", saved.allDay).put("remote", true).toString(),
                    "已创建日程：${saved.title}（${TimeUtils.prettyDateTime(saved.date, saved.start)}）"
                )
            }
            val warning = remote.exceptionOrNull()?.message ?: "CalDAV 写入失败"
            container.schedule.upsertEvent(event)
            return ToolExecution(
                "create_event",
                JSONObject().put("ok", true).put("id", event.id).put("title", event.title)
                    .put("date", event.date).put("start", event.start).put("end", event.end)
                    .put("all_day", event.allDay).put("remote", false).put("warning", warning).toString(),
                "已创建本地日程：${event.title}；CalDAV 未写入（$warning）"
            )
        }
        container.schedule.upsertEvent(event)
        return ToolExecution(
            "create_event",
            JSONObject().put("ok", true).put("id", event.id).put("title", event.title)
                .put("date", event.date).put("start", event.start).put("end", event.end)
                .put("all_day", event.allDay).put("remote", false).toString(),
            "已创建本地日程：${event.title}（${TimeUtils.prettyDateTime(event.date, event.start)}）"
        )
    }

    private fun listEvents(args: JSONObject): ToolExecution {
        val start = args.optString("start").trim().ifEmpty { TimeUtils.todayKey() }
        val end = args.optString("end").trim().ifEmpty { TimeUtils.dateKey(TimeUtils.today().plusDays(7)) }
        val events = container.schedule.events(start, end)
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(
                JSONObject().put("id", e.id).put("title", e.title).put("date", e.date)
                    .put("start", e.start).put("end", e.end).put("all_day", e.allDay)
                    .put("location", e.location).put("remote", !e.local)
            )
        }
        return ToolExecution(
            "list_events",
            JSONObject().put("ok", true).put("events", arr).toString(),
            if (events.isEmpty()) "这段时间没有日程" else "查到 ${events.size} 条日程"
        )
    }

    private fun updateEvent(args: JSONObject): ToolExecution {
        val id = args.optString("id").trim()
        if (id.isEmpty()) return failure("update_event", "缺少日程 id")
        val existing = container.schedule.event(id) ?: return failure("update_event", "找不到这条日程，请先 list_events")
        var title = existing.title
        if (args.has("title")) {
            title = args.optString("title").trim()
            if (title.isEmpty()) return failure("update_event", "日程标题不能为空")
        }
        val date = if (args.has("date")) args.optString("date").trim() else existing.date
        if (TimeUtils.parseDate(date) == null) return failure("update_event", "日期格式应为 yyyy-MM-dd")
        val allDay = if (args.has("all_day")) args.optBoolean("all_day") else existing.allDay
        var start = if (args.has("start")) args.optString("start").trim() else existing.start
        var end = if (args.has("end")) args.optString("end").trim() else existing.end
        if (allDay) {
            start = ""
            end = ""
        } else {
            if (start.isEmpty()) start = "09:00"
            if (TimeUtils.parseTime(start) == null) return failure("update_event", "开始时间格式应为 HH:mm")
            if (end.isEmpty()) end = LocalTime.parse(start).plusHours(1).toString().take(5)
            if (TimeUtils.parseTime(end) == null) return failure("update_event", "结束时间格式应为 HH:mm")
        }
        val location = if (args.has("location")) args.optString("location").trim() else existing.location
        val description = if (args.has("description")) args.optString("description").trim() else existing.description
        var updated = existing.copy(
            title = title,
            date = date,
            start = start,
            end = end,
            allDay = allDay,
            endDate = if (allDay) "" else existing.endDate,
            location = location,
            description = description,
            updatedAt = System.currentTimeMillis()
        )
        val caldav = container.calDav
        if (!caldav.isConfigured()) {
            container.schedule.upsertEvent(updated)
            return ToolExecution(
                "update_event",
                JSONObject().put("ok", true).put("id", updated.id).put("remote", false).toString(),
                "已更新本地日程：${updated.title}（CalDAV 未配置）"
            )
        }
        if (updated.href.isBlank()) {
            val created = caldav.createEvent(updated)
            if (created.isFailure) {
                return failure("update_event", "上传到 CalDAV 失败：${created.exceptionOrNull()?.message ?: "未知错误"}")
            }
            val remote = created.getOrThrow()
            container.schedule.deleteEvent(existing.id)
            container.schedule.upsertEvent(remote)
            updated = remote
        } else {
            val result = caldav.updateEvent(updated)
            if (result.isFailure) {
                return failure("update_event", "写回 CalDAV 失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
            }
            val replacement = result.getOrThrow()
            if (replacement.id != existing.id) container.schedule.deleteEvent(existing.id)
            updated = replacement
            container.schedule.upsertEvent(updated)
        }
        return ToolExecution(
            "update_event",
            JSONObject().put("ok", true).put("id", updated.id).put("remote", true)
                .put("title", updated.title).put("date", updated.date).put("start", updated.start).put("end", updated.end)
                .toString(),
            "已更新并写回 CalDAV：${updated.title}（${TimeUtils.prettyDateTime(updated.date, updated.start)}）"
        )
    }

    private fun deleteEvent(args: JSONObject): ToolExecution {
        val id = args.optString("id").trim()
        if (id.isEmpty()) return failure("delete_event", "缺少日程 id")
        val event = container.schedule.event(id) ?: return failure("delete_event", "找不到这条日程，请先 list_events")
        if (!event.local && event.href.isNotBlank()) {
            if (!container.calDav.isConfigured()) {
                return failure("delete_event", "这条日程在 CalDAV 上，请先配置 CalDAV 再删除")
            }
            val result = container.calDav.deleteEvent(event)
            if (result.isFailure) {
                return failure("delete_event", "从 CalDAV 删除失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
            }
        }
        container.schedule.deleteEvent(event.id)
        return ToolExecution(
            "delete_event",
            JSONObject().put("ok", true).put("id", event.id).put("remote", !event.local && event.href.isNotBlank()).toString(),
            "已删除日程：${event.title}"
        )
    }

    private fun syncCalendar(args: JSONObject): ToolExecution {
        if (!container.calDav.isConfigured()) return failure("sync_calendar", "还没有配置 CalDAV")
        val start = args.optString("start").trim().ifEmpty { TimeUtils.todayKey() }
        val end = args.optString("end").trim().ifEmpty { TimeUtils.dateKey(TimeUtils.today().plusDays(30)) }
        if (TimeUtils.parseDate(start) == null || TimeUtils.parseDate(end) == null) {
            return failure("sync_calendar", "日期格式应为 yyyy-MM-dd")
        }
        var uploaded = 0
        val warnings = ArrayList<String>()
        val uploadedUids = ArrayList<String>()
        container.schedule.events(start, end)
            .filter { it.local && it.href.isBlank() }
            .forEach { local ->
                val result = container.calDav.createEvent(local)
                if (result.isSuccess) {
                    val remote = result.getOrThrow()
                    uploadedUids += remote.uid
                    container.schedule.deleteEvent(local.id)
                    container.schedule.upsertEvent(remote)
                    uploaded++
                } else {
                    warnings += "${local.title}: ${result.exceptionOrNull()?.message ?: "上传失败"}"
                }
            }
        val remoteResult = container.calDav.listEvents(start, end)
        if (remoteResult.isFailure) {
            return failure("sync_calendar", "拉取 CalDAV 失败：${remoteResult.exceptionOrNull()?.message ?: "未知错误"}")
        }
        val remote = remoteResult.getOrThrow()
        remote.forEach { container.schedule.upsertEvent(it) }
        val remoteUids = remote.map { it.uid }.toSet() + uploadedUids
        var removed = 0
        container.schedule.events(start, end)
            .filter { !it.local && it.href.isNotBlank() && it.uid !in remoteUids }
            .forEach { container.schedule.deleteEvent(it.id); removed++ }
        val warningText = if (warnings.isEmpty()) "" else "；${warnings.size} 条上传失败：" + warnings.take(2).joinToString("；")
        return ToolExecution(
            "sync_calendar",
            JSONObject().put("ok", true).put("uploaded", uploaded).put("pulled", remote.size).put("removed", removed)
                .put("warnings", JSONArray(warnings)).toString(),
            "CalDAV 双向同步完成：上传 $uploaded 条，拉取 ${remote.size} 条，清理 $removed 条$warningText"
        )
    }

    private fun createTask(args: JSONObject): ToolExecution {
        val title = args.optString("title").trim()
        if (title.isEmpty()) return failure("create_task", "缺少待办内容")
        val date = args.optString("date").trim().ifEmpty { TimeUtils.todayKey() }
        if (TimeUtils.parseDate(date) == null) return failure("create_task", "日期格式应为 yyyy-MM-dd")
        val now = System.currentTimeMillis()
        val task = TaskItem(
            id = "task_" + UUID.randomUUID().toString().take(12),
            title = title,
            date = date,
            createdAt = now,
            updatedAt = now
        )
        container.schedule.upsertTask(task)
        return ToolExecution(
            "create_task",
            JSONObject().put("ok", true).put("id", task.id).put("title", task.title).put("date", task.date).toString(),
            "已记下待办：${task.title}（${TimeUtils.prettyDate(task.date)}）"
        )
    }

    private fun listTasks(args: JSONObject): ToolExecution {
        val date = args.optString("date").trim().ifEmpty { TimeUtils.todayKey() }
        val tasks = container.schedule.tasks(date)
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("title", t.title).put("date", t.date).put("done", t.done))
        }
        return ToolExecution(
            "list_tasks",
            JSONObject().put("ok", true).put("tasks", arr).toString(),
            if (tasks.isEmpty()) "这天没有待办" else "查到 ${tasks.size} 件待办"
        )
    }

    private fun updateTask(args: JSONObject): ToolExecution {
        val id = args.optString("id").trim()
        if (id.isEmpty()) return failure("update_task", "缺少待办 id")
        val task = container.schedule.tasks(null).firstOrNull { it.id == id }
            ?: return failure("update_task", "找不到这件待办")
        val done = args.optBoolean("done", true)
        val updated = task.copy(done = done, updatedAt = System.currentTimeMillis())
        container.schedule.upsertTask(updated)
        return ToolExecution(
            "update_task",
            JSONObject().put("ok", true).put("id", id).put("done", done).toString(),
            if (done) "已勾掉：${task.title}" else "已标记未完成：${task.title}"
        )
    }

    private fun webSearch(args: JSONObject): ToolExecution {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return failure("web_search", "缺少搜索关键词")
        val max = args.optInt("max_results", 5).coerceIn(1, 10)
        val result = container.search.search(query, max)
        if (result.isFailure) return failure("web_search", result.exceptionOrNull()?.message ?: "搜索失败")
        val results = result.getOrThrow()
        val arr = JSONArray()
        results.forEach { r ->
            arr.put(JSONObject().put("title", r.title).put("url", r.url).put("snippet", r.snippet))
        }
        return ToolExecution(
            "web_search",
            JSONObject().put("ok", true).put("query", query).put("results", arr).toString(),
            if (results.isEmpty()) "没有搜到结果" else "搜到 ${results.size} 条结果",
            sources = results
        )
    }

    private fun fetchUrl(args: JSONObject): ToolExecution {
        val url = args.optString("url").trim()
        if (url.isEmpty()) return failure("fetch_url", "缺少网页地址")
        val result = container.search.fetchUrl(url)
        if (result.isFailure) return failure("fetch_url", result.exceptionOrNull()?.message ?: "读取失败")
        val text = result.getOrThrow()
        return ToolExecution(
            "fetch_url",
            JSONObject().put("ok", true).put("url", url).put("text", text).toString(),
            "已读取网页：${url.take(60)}"
        )
    }

    private fun remember(args: JSONObject): ToolExecution {
        val text = args.optString("text").trim()
        if (text.isEmpty()) return failure("remember", "没有要记住的内容")
        container.memory.remember(text.take(500), "chat")
        return ToolExecution(
            "remember",
            JSONObject().put("ok", true).put("text", text).toString(),
            "已记住：${text.take(30)}"
        )
    }

    private fun recall(args: JSONObject): ToolExecution {
        val query = args.optString("query").trim()
        val memories = if (query.isEmpty()) container.memory.list() else container.memory.search(query)
        val arr = JSONArray()
        memories.forEach { arr.put(it.text) }
        return ToolExecution(
            "recall",
            JSONObject().put("ok", true).put("memories", arr).toString(),
            if (memories.isEmpty()) "没有相关记忆" else "找到 ${memories.size} 条记忆"
        )
    }

    private fun configureCaldav(args: JSONObject): ToolExecution {
        var url = args.optString("url").trim()
        val username = args.optString("username").trim()
        val password = args.optString("password")
        if (username.isEmpty() || password.isEmpty()) {
            return failure("configure_caldav", "需要 CalDAV 账号和密码")
        }
        if (url.isEmpty()) {
            url = "https://calendar.dingtalk.com/dav/$username/primary/"
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return failure("configure_caldav", "CalDAV 地址需要以 http:// 或 https:// 开头")
        }
        val tz = args.optString("timezone").trim().ifEmpty { "Asia/Shanghai" }
        val settings = container.settings
        settings.caldavUrl = url
        settings.caldavUser = username
        settings.caldavTimezone = tz
        container.secrets.put(CalDavClient.KEY_PASSWORD, password)
        val test = container.calDav.testConnection()
        if (test.isFailure) {
            return failure("configure_caldav", "已保存，但连接失败：${test.exceptionOrNull()?.message ?: "未知错误"}")
        }
        val month = java.time.YearMonth.now()
        val start = TimeUtils.dateKey(TimeUtils.gridStart(month).minusDays(7))
        val end = TimeUtils.dateKey(TimeUtils.gridStart(month).plusDays(48))
        var uploaded = 0
        container.schedule.events(start, end)
            .filter { it.local && it.href.isBlank() }
            .forEach { local ->
                val result = container.calDav.createEvent(local)
                if (result.isSuccess) {
                    val remote = result.getOrThrow()
                    container.schedule.deleteEvent(local.id)
                    container.schedule.upsertEvent(remote)
                    uploaded++
                }
            }
        val list = container.calDav.listEvents(start, end)
        var synced = 0
        if (list.isSuccess) {
            list.getOrThrow().forEach { container.schedule.upsertEvent(it); synced++ }
        }
        return ToolExecution(
            "configure_caldav",
            JSONObject().put("ok", true).put("displayName", test.getOrNull() ?: "")
                .put("uploaded", uploaded).put("synced", synced).toString(),
            "CalDAV 已配置并连接：${test.getOrNull() ?: url}；上传 $uploaded 条，拉取 $synced 条日程"
        )
    }

    private fun listProfile(): ToolExecution {
        val items = container.profile.list()
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().put("id", item.id).put("label", item.label).put("value", item.value))
        }
        return ToolExecution(
            "list_profile",
            JSONObject().put("ok", true).put("items", arr).toString(),
            if (items.isEmpty()) "还没有关于用户的资料" else "查到 ${items.size} 条关于用户的资料"
        )
    }

    private fun setProfile(args: JSONObject): ToolExecution {
        val label = args.optString("label").trim()
        val value = args.optString("value").trim()
        if (label.isEmpty() || value.isEmpty()) return failure("set_profile", "需要资料项名称和内容")
        val confidence = if (args.has("confidence")) args.optDouble("confidence", 0.8) else 0.8
        if (confidence < 0.6) {
            return ToolExecution(
                "set_profile",
                JSONObject().put("ok", false).put("skipped", true).put("confidence", confidence).toString(),
                "置信度不足，先不写入关于用户的资料"
            )
        }
        val item = container.profile.set(label, value)
        return ToolExecution(
            "set_profile",
            JSONObject().put("ok", true).put("id", item.id).put("label", item.label).put("value", item.value).toString(),
            "已记录关于用户：${item.label} = ${item.value}"
        )
    }

    private fun deleteProfile(args: JSONObject): ToolExecution {
        val id = args.optString("id").trim()
        val label = args.optString("label").trim()
        val item = when {
            id.isNotEmpty() -> container.profile.get(id)
            label.isNotEmpty() -> container.profile.list().firstOrNull { it.label.equals(label, ignoreCase = true) }
            else -> null
        } ?: return failure("delete_profile", "找不到这条资料，请先 list_profile")
        container.profile.delete(item.id)
        return ToolExecution(
            "delete_profile",
            JSONObject().put("ok", true).put("id", item.id).put("label", item.label).toString(),
            "已删除关于用户：${item.label}"
        )
    }

    private fun currentTime(): ToolExecution {
        val now = TimeUtils.nowText()
        return ToolExecution(
            "get_current_time",
            JSONObject().put("ok", true).put("time", now).put("timezone", TimeUtils.zone().id).toString(),
            now
        )
    }

    private fun failure(name: String, message: String): ToolExecution = ToolExecution(
        name,
        JSONObject().put("ok", false).put("error", message).toString(),
        "操作失败：$message"
    )

    private fun def(name: String, description: String, properties: JSONObject, vararg required: String): ToolDefinition {
        val schema = JSONObject()
            .put("type", "object")
            .put("properties", properties)
        if (required.isNotEmpty()) schema.put("required", JSONArray(required.toList()))
        return ToolDefinition(name, description, schema)
    }

    private fun obj(vararg pairs: Pair<String, JSONObject>): JSONObject = JSONObject().apply {
        pairs.forEach { put(it.first, it.second) }
    }

    private fun str(description: String) = JSONObject().put("type", "string").put("description", description)
    private fun num(description: String) = JSONObject().put("type", "number").put("description", description)
    private fun bool(description: String) = JSONObject().put("type", "boolean").put("description", description)
}
