package com.santethp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Runs local schedule reminders independently of network-dependent device reporting. */
class ChildUsageScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            ChildUsageScheduleReporter.evaluateCachedAndNotify(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Unable to evaluate cached usage schedule", error)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ChildUsageScheduleWorker"
        private const val PERIODIC_NAME = "child-usage-schedule-local-periodic"
        private const val INITIAL_NAME = "child-usage-schedule-local-initial"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val initial = OneTimeWorkRequestBuilder<ChildUsageScheduleWorker>().build()
            workManager.enqueueUniqueWork(
                INITIAL_NAME, ExistingWorkPolicy.KEEP, initial
            )
            val periodic = PeriodicWorkRequestBuilder<ChildUsageScheduleWorker>(
                15, TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(INITIAL_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
