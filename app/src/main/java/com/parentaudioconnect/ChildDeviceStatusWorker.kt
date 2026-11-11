package com.santethp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Device diagnostics run independently from LiveKit audio/video services.
 * A failure in one reporter must not prevent the remaining reports from running.
 */
class ChildDeviceStatusWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        var hasFailure = false

        suspend fun runReporter(name: String, report: suspend () -> Unit) {
            try {
                report()
                Log.i(TAG, "Reporter succeeded: $name")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                hasFailure = true
                Log.e(
                    TAG,
                    "Reporter failed: $name (${error.javaClass.simpleName}): ${error.message}",
                    error
                )
            }
        }

        Log.i(TAG, "Device status worker started")

        runReporter("battery and network status") {
            ChildDeviceStatusReporter.publish(applicationContext)
        }
        runReporter("app usage history") {
            AppUsageReporter.publish(applicationContext)
        }
        runReporter("usage schedule") {
            ChildUsageScheduleReporter.syncAndNotify(applicationContext)
        }
        runReporter("app blocking list") {
            AppBlockReporter.publish(applicationContext)
        }

        return if (hasFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "ChildDeviceStatusWorker"
        private const val PERIODIC_NAME = "child-device-status-periodic"
        private const val INITIAL_NAME = "child-device-status-initial"

        fun schedule(context: Context) {
            // Local schedule reminders must run even when the child device is offline.
            ChildUsageScheduleWorker.schedule(context)
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
            ChildUsageScheduleWorker.cancel(context)
        }
    }
}
