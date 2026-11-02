package com.qingricheng.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.qingricheng.app.QingApp
import com.qingricheng.app.data.EventItem
import com.qingricheng.app.data.SettingsStore
import com.qingricheng.app.domain.TimeUtils
import java.time.LocalDateTime
import java.time.LocalTime

object ReminderScheduler {
    const val ACTION_REMIND = "com.qingricheng.app.REMIND"
    const val EXTRA_EVENT_ID = "event_id"
    const val PREFS = "schedule_reminders"

    fun schedule(context: Context, event: EventItem) {
        val date = TimeUtils.parseDate(event.date) ?: return
        val startMillis = run {
            val time = if (event.allDay) LocalTime.of(9, 0) else TimeUtils.parseTime(event.start) ?: return
            LocalDateTime.of(date, time).atZone(TimeUtils.zone()).toInstant().toEpochMilli()
        }
        val now = System.currentTimeMillis()
        val leadMillis = SettingsStore(context).reminderLeadMinutes * 60_000L
        var triggerAt = startMillis - leadMillis
        if (startMillis < now - 5 * 60_000L) return
        if (triggerAt <= now) triggerAt = now + 1_000L
        val pending = pending(context, event, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun cancel(context: Context, event: EventItem) {
        val pending = pending(context, event, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { context.getSystemService(AlarmManager::class.java).cancel(pending) }
    }

    fun rescheduleAll(context: Context) {
        val app = context.applicationContext as? QingApp ?: return
        val today = TimeUtils.today()
        val events = app.container.schedule.events(
            TimeUtils.dateKey(today.minusDays(1)),
            TimeUtils.dateKey(today.plusDays(60))
        )
        events.forEach { schedule(context, it) }
    }

    fun markFired(context: Context, event: EventItem) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fired = prefs.getStringSet("fired", emptySet())?.toMutableSet() ?: mutableSetOf()
        fired.add(key(event))
        prefs.edit().putStringSet("fired", fired).apply()
    }

    fun isFired(context: Context, event: EventItem): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("fired", emptySet())?.contains(key(event)) == true

    fun key(event: EventItem): String = "${event.id}|${event.date}|${event.start}"

    private fun pending(context: Context, event: EventItem, flags: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_EVENT_ID, event.id)
        return PendingIntent.getBroadcast(context, event.id.hashCode(), intent, flags)
    }
}
