package com.qingricheng.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qingricheng.app.QingApp
import com.qingricheng.app.domain.TimeUtils
import com.qingricheng.app.notify.Notifications
import java.time.LocalDateTime
import java.time.LocalTime

/** Checks upcoming schedule events and posts a notification once per event. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? QingApp ?: return Result.success()
        if (!Notifications.canNotify(app)) return Result.success()
        val today = TimeUtils.today()
        val events = app.container.schedule.events(
            TimeUtils.dateKey(today),
            TimeUtils.dateKey(today.plusDays(1))
        )
        val prefs = app.getSharedPreferences("schedule_reminders", Context.MODE_PRIVATE)
        val fired = prefs.getStringSet("fired", emptySet())?.toMutableSet() ?: mutableSetOf()
        val now = LocalDateTime.now()
        var changed = false
        for (event in events) {
            val date = TimeUtils.parseDate(event.date) ?: continue
            val time = if (event.allDay) LocalTime.of(8, 30) else TimeUtils.parseTime(event.start) ?: continue
            val start = LocalDateTime.of(date, time)
            val notifyFrom = start.minusMinutes(15)
            val notifyUntil = start.plusMinutes(5)
            if (now.isBefore(notifyFrom) || now.isAfter(notifyUntil)) continue
            val key = "${event.id}|${event.date}|${event.start}"
            if (fired.add(key)) {
                Notifications.showEvent(app, event)
                changed = true
            }
        }
        if (fired.size > 400) {
            val keep = fired.filter { it.contains(TimeUtils.dateKey(today)) || it.contains(TimeUtils.dateKey(today.plusDays(1))) }.toMutableSet()
            fired.clear(); fired.addAll(keep)
        }
        if (changed) prefs.edit().putStringSet("fired", fired).apply()
        return Result.success()
    }
}
