package com.qingricheng.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qingricheng.app.QingApp
import com.qingricheng.app.domain.TimeUtils
import java.time.YearMonth

/** Periodic CalDAV pull. Keeps the local SQLite cache close to the server. */
class CalDavSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? QingApp ?: return Result.success()
        val caldav = app.container.calDav
        if (!caldav.isConfigured()) return Result.success()
        val month = YearMonth.now()
        val start = TimeUtils.dateKey(TimeUtils.gridStart(month).minusDays(7))
        val end = TimeUtils.dateKey(TimeUtils.gridStart(month).plusDays(48))
        val result = caldav.listEvents(start, end)
        if (result.isFailure) return Result.retry()
        val remote = result.getOrThrow()
        remote.forEach { app.container.schedule.upsertEvent(it) }
        val remoteUids = remote.map { it.uid }.toSet()
        app.container.schedule.events(start, end)
            .filter { !it.local && it.href.isNotBlank() && it.uid !in remoteUids }
            .forEach { app.container.schedule.deleteEvent(it.id) }
        return Result.success()
    }
}
