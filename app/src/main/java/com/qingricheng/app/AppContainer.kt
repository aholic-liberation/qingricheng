package com.qingricheng.app

import android.content.Context
import com.qingricheng.app.data.AppDb
import com.qingricheng.app.data.AttachmentStore
import com.qingricheng.app.data.ChatRepository
import com.qingricheng.app.data.MemoryRepository
import com.qingricheng.app.data.NotesRepository
import com.qingricheng.app.data.PersonaRepository
import com.qingricheng.app.data.ProfileRepository
import com.qingricheng.app.data.ScheduleRepository
import com.qingricheng.app.data.SecretStore
import com.qingricheng.app.data.SettingsStore
import com.qingricheng.app.domain.ToolRegistry
import com.qingricheng.app.net.CalDavClient
import com.qingricheng.app.net.DeepSeekClient
import com.qingricheng.app.net.SearchClient
import com.qingricheng.app.notify.ReminderScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsStore(appContext)
    val secrets = SecretStore(appContext)
    val db = AppDb(appContext)
    val personas = PersonaRepository(appContext, db, settings)
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

    fun rescheduleReminders() = ReminderScheduler.rescheduleAll(appContext)
}
