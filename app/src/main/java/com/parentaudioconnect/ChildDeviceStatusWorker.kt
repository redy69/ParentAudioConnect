package com.santethp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Device diagnostics run independently from LiveKit audio/video services. */
class ChildDeviceStatusWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            ChildDeviceStatusReporter.publish(applicationContext)
            AppUsageReporter.publish(applicationContext)
            ChildUsageScheduleReporter.syncAndNotify(applicationContext)
            AppPermissionAuditReporter.publish(applicationContext)
            Result.success()
        } catch (error: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "child-device-status-periodic"
        private const val INITIAL_NAME = "child-device-status-initial"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val initial = OneTimeWorkRequestBuilder<ChildDeviceStatusWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                INITIAL_NAME, ExistingWorkPolicy.KEEP, initial
            )
            val periodic = PeriodicWorkRequestBuilder<ChildDeviceStatusWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(INITIAL_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
