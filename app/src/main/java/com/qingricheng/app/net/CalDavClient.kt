package com.qingricheng.app.net

import com.qingricheng.app.data.EventItem
import com.qingricheng.app.data.SecretStore
import com.qingricheng.app.data.SettingsStore
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CalDavConfig(
    val url: String,
    val user: String,
    val password: String,
    val timezone: String
)

class CalDavClient(private val settings: SettingsStore, private val secrets: SecretStore) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun config(): CalDavConfig? {
        val url = settings.caldavUrl.trim()
        val user = settings.caldavUser.trim()
        val password = secrets.get(KEY_PASSWORD) ?: ""
        if (url.isBlank() || user.isBlank() || password.isBlank()) return null
        return CalDavConfig(url, user, password, settings.caldavTimezone.ifBlank { "Asia/Shanghai" })
    }

    fun isConfigured(): Boolean = config() != null

    fun testConnection(): Result<String> = runCatching {
        val config = config() ?: throw IOException("还没有配置 CalDAV，请先在设置里填写地址、账号和密码")
        dav(
            config, "PROPFIND", config.url,
            mapOf("Depth" to "0", "Content-Type" to XML),
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/></d:prop></d:propfind>"""
        ).use { response ->
            val text = response.body.string()
            val names = xmlBlocks(text, "displayname")
            names.firstOrNull()?.let { decodeXml(it).trim() } ?: "已连接"
        }
    }

    fun listEvents(startDate: String, endDate: String): Result<List<EventItem>> = runCatching {
        val config = config() ?: throw IOException("还没有配置 CalDAV")
        val zone = zoneOf(config.timezone)
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        val startStamp = start.atStartOfDay(zone).toInstant().icsUtc()
        val endStamp = end.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1).icsUtc()
        val headers = mapOf("Depth" to "1", "Content-Type" to XML)
        val events = runCatching {
            dav(config, "REPORT", config.url, headers, timeRangeBody(startStamp, endStamp, true)).use { response ->
                parseResponse(response.body.string(), config)
            }
        }.getOrElse {
            dav(config, "REPORT", config.url, headers, timeRangeBody(startStamp, endStamp, false)).use { response ->
                parseResponse(response.body.string(), config)
            }
        }
        events.filter { event ->
            val last = event.endDate.ifBlank { event.date }
            event.date <= endDate && last >= startDate
        }.sortedWith(compareBy({ it.date }, { it.allDay == false }, { it.start }))
    }

    fun createEvent(event: EventItem): Result<EventItem> = runCatching {
        val config = config() ?: throw IOException("还没有配置 CalDAV")
        val uid = UUID.randomUUID().toString()
        val filename = "qingricheng-" + sha1(uid).take(32) + ".ics"
        val href = baseUrl(config.url) + filename
        dav(
            config, "PUT", href,
            mapOf(
                "Content-Type" to "text/calendar; charset=utf-8",
                "If-None-Match" to "*"
            ),
            eventBody(event, uid, config.timezone)
        ).use { response ->
            val etag = validEtag(response.header("ETag"))
            event.copy(
                id = eventId(uid),
                uid = uid,
                href = href,
                etag = etag,
                calendarUrl = config.url,
                local = false,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun updateEvent(event: EventItem): Result<EventItem> = runCatching {
        val config = config() ?: throw IOException("还没有配置 CalDAV")
        if (event.href.isBlank()) throw IOException("这条日程不是从 CalDAV 同步来的，无法写回")
        dav(
            config, "PUT", event.href,
            mapOf(
                "Content-Type" to "text/calendar; charset=utf-8",
                "If-Match" to (validEtag(event.etag).ifBlank { "*" })
            ),
            eventBody(event, event.uid, config.timezone)
        ).use { response ->
            event.copy(
                etag = validEtag(response.header("ETag")),
                local = false,
                updatedAt = System.currentTimeMillis()
            )
        }
    }.recoverCatching { failure ->
        val message = failure.message.orEmpty()
        val updateUnsupported = message.contains("HTTP 400") || message.contains("HTTP 405") || message.contains("HTTP 501")
        if (!updateUnsupported) throw failure
        // Some CalDAV services accept creation but reject PUT on an existing resource.
        // Replace by creating a new resource with the same local id, then best-effort delete the old one.
        val created = createEvent(event.copy(href = "", etag = "", uid = "", calendarUrl = "", local = true))
        if (created.isFailure) throw failure
        val replacement = created.getOrThrow()
        runCatching { deleteRemote(event) }
        replacement
    }

    fun deleteEvent(event: EventItem): Result<Unit> = runCatching {
        val config = config() ?: throw IOException("还没有配置 CalDAV")
        if (event.href.isBlank()) throw IOException("这条日程不是从 CalDAV 同步来的，无法删除")
        dav(
            config, "DELETE", event.href,
            mapOf("Accept" to "text/calendar", "If-Match" to (validEtag(event.etag).ifBlank { "*" }))
        ).close()
    }.recoverCatching { failure ->
        val message = failure.message.orEmpty()
        // A missing remote resource is already deleted; a stale/invalid If-Match may
        // be rejected by some servers, so retry without a precondition.
        if (isNotFound(message)) return@recoverCatching Unit
        if (message.contains("HTTP 400") || message.contains("HTTP 412")) {
            deleteRemote(event)
        } else {
            throw failure
        }
    }

    private fun deleteRemote(event: EventItem) {
        val config = config() ?: throw IOException("还没有配置 CalDAV")
        val base = baseUrl(event.calendarUrl.ifBlank { config.url })
        val candidates = linkedSetOf(event.href)
        if (event.uid.isNotBlank()) {
            candidates += base + event.uid
            candidates += base + event.uid + ".ics"
        }
        var last: Exception? = null
        for (candidate in candidates.filter { it.isNotBlank() }) {
            try {
                dav(config, "DELETE", candidate, mapOf("Accept" to "text/calendar")).close()
                return
            } catch (e: Exception) {
                last = e
                if (!isNotFound(e.message.orEmpty())) throw e
            }
        }
        // If every candidate is 404 the remote resource is already gone.
        if (isNotFound(last?.message.orEmpty())) return
        last?.let { throw it } ?: throw IOException("无法删除这条日程")
    }

    private fun isNotFound(message: String): Boolean =
        message.contains("HTTP 404") || message.contains("日历集合不存在") || message.contains("not-found", ignoreCase = true)

    private fun validEtag(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return if (value.isBlank() || value.equals("null", true) || value.equals("W/null", true) || value.equals("\"null\"", true)) "" else value
    }

    private fun dav(
        config: CalDavConfig,
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Response {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(config.user, config.password, Charsets.UTF_8))
            .header("Accept", "application/xml, text/calendar, */*")
        headers.forEach { (key, value) -> builder.header(key, value) }
        val requestBody = body?.toRequestBody(if (method == "PUT") CAL_MEDIA else XML_MEDIA)
        builder.method(method, requestBody)
        val response = client.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            val text = runCatching { response.body.string() }.getOrDefault("")
            response.close()
            throw IOException(statusError(response.code, text))
        }
        return response
    }

    private fun parseResponse(xml: String, config: CalDavConfig): List<EventItem> {
        val out = ArrayList<EventItem>()
        for (item in xmlBlocks(xml, "response")) {
            val href = xmlBlocks(item, "href").firstOrNull()?.let { decodeXml(it).trim() }.orEmpty()
            val etag = xmlBlocks(item, "getetag").firstOrNull()?.let { decodeXml(it).trim() }.orEmpty()
            val data = xmlBlocks(item, "calendar-data").firstOrNull() ?: continue
            if (href.isBlank()) continue
            val fullHref = if (href.startsWith("http://") || href.startsWith("https://")) href else URI(baseUrl(config.url)).resolve(href).toString()
            val ics = decodeXml(data)
            out += parseVEvents(ics, config, fullHref, etag)
        }
        return out
    }

    private fun parseVEvents(ics: String, config: CalDavConfig, href: String, etag: String): List<EventItem> {
        val lines = unfold(ics).split('\n')
        val out = ArrayList<EventItem>()
        var current: RawEvent? = null
        var inAlarm = false
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.equals("BEGIN:VALARM", true)) { inAlarm = true; continue }
            if (line.equals("END:VALARM", true)) { inAlarm = false; continue }
            if (inAlarm) continue
            if (line.equals("BEGIN:VEVENT", true)) { current = RawEvent(); continue }
            if (line.equals("END:VEVENT", true)) {
                current?.let { raw ->
                    raw.dtStart?.let { start ->
                        val uid = raw.uid.ifBlank { href.substringAfterLast('/') }
                        val end = raw.dtEnd
                        val id = eventId(uid)
                        val description = raw.description.take(2000)
                        var location = raw.location.take(300)
                        if (location.isBlank()) {
                            Regex("(?:地点|位置)[：:]\\s*([^\\n\\r]+)").find(description)?.let {
                                location = it.groupValues[1].trim().take(300)
                            }
                        }
                        out += EventItem(
                            id = id,
                            uid = uid,
                            href = href,
                            etag = etag,
                            calendarUrl = config.url,
                            title = raw.summary.ifBlank { "(无标题日程)" }.take(300),
                            date = start.date,
                            start = if (start.allDay) "" else start.time,
                            end = if (end?.allDay == false) end.time else "",
                            endDate = if (end != null && end.date != start.date) end.date else "",
                            allDay = start.allDay,
                            location = location,
                            description = description,
                            local = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
                current = null
                continue
            }
            val colon = line.indexOf(':')
            if (colon < 1 || current == null) continue
            val left = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val parts = left.split(';')
            val prop = parts.first().uppercase()
            val params = HashMap<String, String>()
            parts.drop(1).forEach { piece ->
                val eq = piece.indexOf('=')
                if (eq > 0) params[piece.substring(0, eq).uppercase()] = piece.substring(eq + 1)
            }
            when (prop) {
                "UID" -> current!!.uid = unescape(value).trim()
                "SUMMARY" -> current!!.summary = unescape(value).trim()
                "LOCATION" -> current!!.location = unescape(value).trim()
                "DESCRIPTION" -> current!!.description = unescape(value).trim()
                "DTSTART" -> current!!.dtStart = parseDateProperty(params, value, config.timezone)
                "DTEND" -> current!!.dtEnd = parseDateProperty(params, value, config.timezone)
            }
        }
        return out
    }

    private fun parseDateProperty(params: Map<String, String>, value: String, defaultZone: String): ParsedDate? {
        val raw = value.trim()
        if (params["VALUE"] == "DATE" || Regex("^\\d{8}").matches(raw)) {
            val m = Regex("^(\\d{4})(\\d{2})(\\d{2})").find(raw) ?: return null
            return ParsedDate("${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}", "", true)
        }
        val m = Regex("^(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})(Z?)$").find(raw) ?: return null
        val local = LocalDateTime.of(
            m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
            m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt()
        )
        val target = zoneOf(defaultZone)
        val zoned = if (m.groupValues[7] == "Z") {
            local.toInstant(ZoneOffset.UTC).atZone(target)
        } else {
            val sourceZone = runCatching { ZoneId.of(params["TZID"] ?: defaultZone) }.getOrDefault(target)
            local.atZone(sourceZone).withZoneSameInstant(target)
        }
        return ParsedDate(
            date = zoned.toLocalDate().toString(),
            time = zoned.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
            allDay = false
        )
    }

    private fun eventBody(event: EventItem, uid: String, timezone: String): String {
        val date = LocalDate.parse(event.date)
        val stamp = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val lines = ArrayList<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:-//QingRiCheng//Light Schedule//CN"
        lines += "CALSCALE:GREGORIAN"
        lines += "BEGIN:VEVENT"
        lines += "UID:$uid"
        lines += "DTSTAMP:$stamp"
        if (event.allDay) {
            lines += "DTSTART;VALUE=DATE:" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            lines += "DTEND;VALUE=DATE:" + date.plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        } else {
            val start = LocalTime.parse(event.start.ifBlank { "09:00" })
            var end = if (event.end.isBlank()) start.plusHours(1) else runCatching { LocalTime.parse(event.end) }.getOrDefault(start.plusHours(1))
            if (!end.isAfter(start)) end = start.plusHours(1)
            lines += "DTSTART;TZID=$timezone:" + LocalDateTime.of(date, start).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            lines += "DTEND;TZID=$timezone:" + LocalDateTime.of(date, end).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        }
        lines += "SUMMARY:" + escape(event.title)
        if (event.location.isNotBlank()) lines += "LOCATION:" + escape(event.location)
        if (event.description.isNotBlank()) lines += "DESCRIPTION:" + escape(event.description)
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"
        lines += ""
        return lines.joinToString("\r\n")
    }

    private fun timeRangeBody(start: String, end: String, expand: Boolean): String =
        """<?xml version="1.0" encoding="utf-8"?><c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><d:getetag/><c:calendar-data>${if (expand) "<c:expand start=\"$start\" end=\"$end\"/>" else ""}</c:calendar-data></d:prop><c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT"><c:time-range start="$start" end="$end"/></c:comp-filter></c:comp-filter></c:filter></c:calendar-query>"""

    private fun statusError(status: Int, body: String): String {
        val detail = body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(160)
        return when (status) {
            401 -> "日历认证失败，请检查账号或应用专用密码"
            403 -> "这个账号没有访问该日历的权限"
            404 -> "日历集合不存在，请检查 CalDAV 地址"
            409, 412 -> "日程在服务器上已经被改动，请先同步再试"
            in 500..599 -> "日历服务暂时不可用（HTTP $status）"
            else -> "日历请求失败（HTTP $status）${if (detail.isNotEmpty()) "：$detail" else ""}"
        }
    }

    private fun baseUrl(url: String): String = if (url.endsWith("/")) url else "$url/"
    private fun zoneOf(value: String): ZoneId = runCatching { ZoneId.of(value) }.getOrDefault(ZoneId.systemDefault())
    private fun sha1(value: String): String = MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun eventId(uid: String): String = "cal_" + sha1(uid).take(24)

    private fun unfold(text: String): String = text.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\\n[ \\t]"), "")
    private fun unescape(value: String): String = value
        .replace("\\n", "\n", ignoreCase = true)
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    private fun decodeXml(value: String): String = value
        .replace(Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>")) { it.groupValues[1] }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    private fun xmlBlocks(xml: String, tag: String): List<String> =
        Regex("<(?:[A-Za-z0-9_-]+:)?$tag\\b[^>]*>([\\s\\S]*?)</(?:[A-Za-z0-9_-]+:)?$tag>", RegexOption.IGNORE_CASE)
            .findAll(xml).map { it.groupValues[1] }.toList()

    private fun Instant.icsUtc(): String = atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

    private data class ParsedDate(val date: String, val time: String, val allDay: Boolean)
    private data class RawEvent(
        var uid: String = "",
        var summary: String = "",
        var location: String = "",
        var description: String = "",
        var dtStart: ParsedDate? = null,
        var dtEnd: ParsedDate? = null
    )

    companion object {
        const val KEY_PASSWORD = "caldav_password"
        private const val XML = "application/xml; charset=utf-8"
        private val XML_MEDIA = XML.toMediaType()
        private val CAL_MEDIA = "text/calendar; charset=utf-8".toMediaType()
    }
}
