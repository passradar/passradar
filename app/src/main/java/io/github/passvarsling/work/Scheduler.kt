package io.github.passvarsling.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.passvarsling.data.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Keeps the periodic background check in line with the settings and watch list. */
class Scheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** When Android plans to run the next check, or null when none is planned. */
    val nextRun: Flow<Long?> = workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK).map { infos ->
        infos.firstOrNull { !it.state.isFinished }?.nextScheduleTimeMillis?.takeIf { it in 1 until Long.MAX_VALUE }
    }

    fun apply(settings: AppSettings, hasActiveWatches: Boolean) {
        if (!settings.backgroundEnabled || !hasActiveWatches) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        // 15 minutes is both Android's minimum and the most we want to ask of the booking system.
        val minutes = settings.intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<CheckWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val PERIODIC_WORK = "check-watches"
    }
}
