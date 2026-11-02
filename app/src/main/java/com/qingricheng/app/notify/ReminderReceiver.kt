package com.qingricheng.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingricheng.app.QingApp

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val app = context.applicationContext as? QingApp ?: return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return
        val event = app.container.schedule.event(id) ?: return
        if (ReminderScheduler.isFired(context, event)) return
        if (Notifications.showEvent(context, event)) {
            ReminderScheduler.markFired(context, event)
        }
    }
}
