package com.qingricheng.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDb(context: Context) : SQLiteOpenHelper(context, "qingricheng.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE personas(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                avatar TEXT NOT NULL DEFAULT '🌙',
                tagline TEXT NOT NULL DEFAULT '',
                json TEXT NOT NULL,
                builtin INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                meta TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE events(
                id TEXT PRIMARY KEY,
                uid TEXT NOT NULL DEFAULT '',
                href TEXT NOT NULL DEFAULT '',
                etag TEXT NOT NULL DEFAULT '',
                calendarUrl TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                date TEXT NOT NULL,
                start TEXT NOT NULL DEFAULT '',
                end TEXT NOT NULL DEFAULT '',
                endDate TEXT NOT NULL DEFAULT '',
                allDay INTEGER NOT NULL DEFAULT 0,
                location TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                local INTEGER NOT NULL DEFAULT 1,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_events_date ON events(date)")
        db.execSQL(
            """
            CREATE TABLE tasks(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                date TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tasks_date ON tasks(date)")
        db.execSQL(
            """
            CREATE TABLE stickies(
                id TEXT PRIMARY KEY,
                text TEXT NOT NULL,
                color TEXT NOT NULL DEFAULT 'butter',
                done INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE memories(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE profiles(
                id TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                value TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_profiles_label ON profiles(label)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS profiles(
                    id TEXT PRIMARY KEY,
                    label TEXT NOT NULL,
                    value TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_profiles_label ON profiles(label)")
        }
    }

    // ---- personas ---------------------------------------------------------
    fun upsertPersona(card: PersonaCard) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", card.id)
            put("name", card.name)
            put("avatar", card.avatar)
            put("tagline", card.tagline)
            put("json", card.rawJson)
            put("builtin", if (card.builtin) 1 else 0)
            put("createdAt", now)
            put("updatedAt", now)
        }
        writableDatabase.insertWithOnConflict("personas", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listPersonas(): List<PersonaCard> {
        val out = ArrayList<PersonaCard>()
        readableDatabase.query("personas", null, null, null, null, null, "builtin DESC, createdAt ASC").use { c ->
            while (c.moveToNext()) {
                val parsed = PersonaParser.parse(c.getString(c.getColumnIndexOrThrow("json")))
                if (parsed != null) {
                    out += parsed.copy(builtin = c.getInt(c.getColumnIndexOrThrow("builtin")) == 1)
                }
            }
        }
        return out
    }

    fun deletePersona(id: String) = writableDatabase.delete("personas", "id=?", arrayOf(id))

    // ---- messages ---------------------------------------------------------
    fun insertMessage(role: String, content: String, meta: String = ""): ChatMessage {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("role", role)
            put("content", content)
            put("createdAt", now)
            put("meta", meta)
        }
        val id = writableDatabase.insert("messages", null, values)
        return ChatMessage(id = id, role = role, content = content, createdAt = now, meta = meta)
    }

    fun listMessages(limit: Int = 200): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        readableDatabase.rawQuery(
            "SELECT * FROM (SELECT * FROM messages ORDER BY id DESC LIMIT ?) ORDER BY id ASC",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += readMessage(c)
        }
        return out
    }

    fun clearMessages() = writableDatabase.delete("messages", null, null)

    private fun readMessage(c: Cursor) = ChatMessage(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        role = c.getString(c.getColumnIndexOrThrow("role")),
        content = c.getString(c.getColumnIndexOrThrow("content")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
        meta = c.getString(c.getColumnIndexOrThrow("meta"))
    )

    // ---- events -----------------------------------------------------------
    fun upsertEvent(event: EventItem) {
        val values = ContentValues().apply {
            put("id", event.id)
            put("uid", event.uid)
            put("href", event.href)
            put("etag", event.etag)
            put("calendarUrl", event.calendarUrl)
            put("title", event.title)
            put("date", event.date)
            put("start", event.start)
            put("end", event.end)
            put("endDate", event.endDate)
            put("allDay", if (event.allDay) 1 else 0)
            put("location", event.location)
            put("description", event.description)
            put("local", if (event.local) 1 else 0)
            put("updatedAt", event.updatedAt)
        }
        writableDatabase.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listEvents(startDate: String, endDate: String): List<EventItem> {
        val out = ArrayList<EventItem>()
        readableDatabase.query(
            "events", null,
            "date <= ? AND (endDate = '' OR endDate >= ?)",
            arrayOf(endDate, startDate),
            null, null, "date ASC, allDay DESC, start ASC"
        ).use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(c.getColumnIndexOrThrow("allDay")) == 1
                out += EventItem(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    uid = c.getString(c.getColumnIndexOrThrow("uid")),
                    href = c.getString(c.getColumnIndexOrThrow("href")),
                    etag = c.getString(c.getColumnIndexOrThrow("etag")),
                    calendarUrl = c.getString(c.getColumnIndexOrThrow("calendarUrl")),
                    title = c.getString(c.getColumnIndexOrThrow("title")),
                    date = c.getString(c.getColumnIndexOrThrow("date")),
                    start = if (allDay) "" else c.getString(c.getColumnIndexOrThrow("start")),
                    end = if (allDay) "" else c.getString(c.getColumnIndexOrThrow("end")),
                    endDate = c.getString(c.getColumnIndexOrThrow("endDate")),
                    allDay = allDay,
                    location = c.getString(c.getColumnIndexOrThrow("location")),
                    description = c.getString(c.getColumnIndexOrThrow("description")),
                    local = c.getInt(c.getColumnIndexOrThrow("local")) == 1,
                    updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt"))
                )
            }
        }
        return out
    }

    fun event(id: String): EventItem? {
        readableDatabase.query("events", null, "id=?", arrayOf(id), null, null, null).use { c ->
            if (!c.moveToFirst()) return null
            val allDay = c.getInt(c.getColumnIndexOrThrow("allDay")) == 1
            return EventItem(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                uid = c.getString(c.getColumnIndexOrThrow("uid")),
                href = c.getString(c.getColumnIndexOrThrow("href")),
                etag = c.getString(c.getColumnIndexOrThrow("etag")),
                calendarUrl = c.getString(c.getColumnIndexOrThrow("calendarUrl")),
                title = c.getString(c.getColumnIndexOrThrow("title")),
                date = c.getString(c.getColumnIndexOrThrow("date")),
                start = if (allDay) "" else c.getString(c.getColumnIndexOrThrow("start")),
                end = if (allDay) "" else c.getString(c.getColumnIndexOrThrow("end")),
                endDate = c.getString(c.getColumnIndexOrThrow("endDate")),
                allDay = allDay,
                location = c.getString(c.getColumnIndexOrThrow("location")),
                description = c.getString(c.getColumnIndexOrThrow("description")),
                local = c.getInt(c.getColumnIndexOrThrow("local")) == 1,
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt"))
            )
        }
    }

    fun deleteEvent(id: String) = writableDatabase.delete("events", "id=?", arrayOf(id))

    // ---- tasks ------------------------------------------------------------
    fun upsertTask(task: TaskItem) {
        val values = ContentValues().apply {
            put("id", task.id)
            put("title", task.title)
            put("date", task.date)
            put("done", if (task.done) 1 else 0)
            put("createdAt", task.createdAt)
            put("updatedAt", task.updatedAt)
        }
        writableDatabase.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listTasks(date: String? = null): List<TaskItem> {
        val out = ArrayList<TaskItem>()
        val selection = if (date == null) null else "date=?"
        val args = if (date == null) null else arrayOf(date)
        readableDatabase.query("tasks", null, selection, args, null, null, "done ASC, createdAt ASC").use { c ->
            while (c.moveToNext()) out += TaskItem(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                title = c.getString(c.getColumnIndexOrThrow("title")),
                date = c.getString(c.getColumnIndexOrThrow("date")),
                done = c.getInt(c.getColumnIndexOrThrow("done")) == 1,
                createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt"))
            )
        }
        return out
    }


    fun listTasksBetween(startDate: String, endDate: String): List<TaskItem> {
        val out = ArrayList<TaskItem>()
        readableDatabase.query(
            "tasks", null, "date >= ? AND date <= ?", arrayOf(startDate, endDate),
            null, null, "date ASC, done ASC, createdAt ASC"
        ).use { c ->
            while (c.moveToNext()) out += TaskItem(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                title = c.getString(c.getColumnIndexOrThrow("title")),
                date = c.getString(c.getColumnIndexOrThrow("date")),
                done = c.getInt(c.getColumnIndexOrThrow("done")) == 1,
                createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt"))
            )
        }
        return out
    }

    fun deleteTask(id: String) = writableDatabase.delete("tasks", "id=?", arrayOf(id))

    // ---- stickies ---------------------------------------------------------
    fun upsertSticky(sticky: StickyItem) {
        val values = ContentValues().apply {
            put("id", sticky.id)
            put("text", sticky.text)
            put("color", sticky.color)
            put("done", if (sticky.done) 1 else 0)
            put("createdAt", sticky.createdAt)
            put("updatedAt", sticky.updatedAt)
        }
        writableDatabase.insertWithOnConflict("stickies", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listStickies(): List<StickyItem> {
        val out = ArrayList<StickyItem>()
        readableDatabase.query("stickies", null, null, null, null, null, "done ASC, createdAt ASC").use { c ->
            while (c.moveToNext()) out += StickyItem(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                text = c.getString(c.getColumnIndexOrThrow("text")),
                color = c.getString(c.getColumnIndexOrThrow("color")),
                done = c.getInt(c.getColumnIndexOrThrow("done")) == 1,
                createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt"))
            )
        }
        return out
    }

    fun deleteSticky(id: String) = writableDatabase.delete("stickies", "id=?", arrayOf(id))

    // ---- memories ---------------------------------------------------------
    fun insertMemory(text: String, source: String): MemoryItem {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("text", text)
            put("source", source)
            put("createdAt", now)
        }
        val id = writableDatabase.insert("memories", null, values)
        return MemoryItem(id = id, text = text, source = source, createdAt = now)
    }

    fun listMemories(limit: Int = 80): List<MemoryItem> {
        val out = ArrayList<MemoryItem>()
        readableDatabase.rawQuery(
            "SELECT * FROM (SELECT * FROM memories ORDER BY createdAt DESC LIMIT ?) ORDER BY createdAt ASC",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += MemoryItem(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                text = c.getString(c.getColumnIndexOrThrow("text")),
                source = c.getString(c.getColumnIndexOrThrow("source")),
                createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
            )
        }
        return out
    }

    fun searchMemories(query: String, limit: Int = 20): List<MemoryItem> {
        val out = ArrayList<MemoryItem>()
        readableDatabase.query(
            "memories", null, "text LIKE ?", arrayOf("%$query%"), null, null, "createdAt DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) out += MemoryItem(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                text = c.getString(c.getColumnIndexOrThrow("text")),
                source = c.getString(c.getColumnIndexOrThrow("source")),
                createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
            )
        }
        return out
    }
    // ---- profiles ---------------------------------------------------------
fun listProfiles(): List<ProfileItem> {
    val out = ArrayList<ProfileItem>()
    readableDatabase.query("profiles", null, null, null, null, null, "updatedAt DESC").use { c ->
        while (c.moveToNext()) {
            out += ProfileItem(
                id = c.getString(c.getColumnIndexOrThrow("id")),
                label = c.getString(c.getColumnIndexOrThrow("label")),
                value = c.getString(c.getColumnIndexOrThrow("value")),
                createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt"))
            )
        }
    }
    return out
}

fun profile(id: String): ProfileItem? {
    readableDatabase.query("profiles", null, "id=?", arrayOf(id), null, null, null).use { c ->
        if (!c.moveToFirst()) return null
        return ProfileItem(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            label = c.getString(c.getColumnIndexOrThrow("label")),
            value = c.getString(c.getColumnIndexOrThrow("value")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt"))
        )
    }
}

fun upsertProfile(item: ProfileItem) {
    val values = ContentValues().apply {
        put("id", item.id)
        put("label", item.label)
        put("value", item.value)
        put("createdAt", item.createdAt)
        put("updatedAt", item.updatedAt)
    }
    writableDatabase.insertWithOnConflict("profiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
}

fun deleteProfile(id: String) = writableDatabase.delete("profiles", "id=?", arrayOf(id))
}
