package com.qingricheng.app.data

import android.content.Context
import com.qingricheng.app.notify.ReminderScheduler

class ScheduleRepository(private val db: AppDb, private val context: Context? = null) {
    fun events(startDate: String, endDate: String): List<EventItem> = db.listEvents(startDate, endDate)
    fun event(id: String): EventItem? = db.event(id)
    fun upsertEvent(event: EventItem) {
        db.upsertEvent(event)
        context?.let { ReminderScheduler.schedule(it, event) }
    }
    fun deleteEvent(id: String) {
        val event = db.event(id)
        db.deleteEvent(id)
        if (event != null) context?.let { ReminderScheduler.cancel(it, event) }
    }
    fun tasks(date: String? = null): List<TaskItem> = db.listTasks(date)
    fun tasksBetween(startDate: String, endDate: String): List<TaskItem> = db.listTasksBetween(startDate, endDate)
    fun upsertTask(task: TaskItem) = db.upsertTask(task)
    fun deleteTask(id: String) = db.deleteTask(id)
}
