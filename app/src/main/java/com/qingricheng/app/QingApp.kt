package com.qingricheng.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.qingricheng.app.notify.Notifications
import com.qingricheng.app.notify.ReminderScheduler
import com.qingricheng.app.sync.CalDavSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class QingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.personas.seedExampleIfNeeded()
            container.chat.ensureGreeting(container.personas.activeCards())
            ReminderScheduler.rescheduleAll(this@QingApp)
        }
        runCatching {
            val request = PeriodicWorkRequestBuilder<CalDavSyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "caldav-periodic-sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
