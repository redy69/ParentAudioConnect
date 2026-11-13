package com.santethp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodically discovers newly added media even when the parent is not
 * browsing the child gallery. It only records a local queue; it never reads
 * file bytes, transfers media, or uploads to Google Drive.
 */
class GalleryBackupDiscoveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SantetHP-BackupScan"
        private const val UNIQUE_WORK = "santethp_gallery_backup_discovery"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GalleryBackupDiscoveryWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }

    override suspend fun doWork(): Result {
        val tracker = GalleryBackupTracker(applicationContext)
        if (!tracker.isEnabled()) return Result.success()

        val repository = GalleryMediaRepository(applicationContext)
        if (!repository.hasFullMediaAccess()) {
            Log.w(TAG, "Pemindaian dilewati: izin penuh foto dan video tidak tersedia")
            return Result.success()
        }

        return try {
            val allMedia = repository.listMedia(limit = null)
            tracker.discoverNewMedia(allMedia)
            Log.i(TAG, "Pemindaian selesai; antrean baru=" + tracker.pendingItems().size)
            Result.success()
        } catch (error: SecurityException) {
            Log.w(TAG, "Izin media dicabut saat pemindaian", error)
            Result.success()
        } catch (error: Exception) {
            Log.e(TAG, "Pemindaian backup gagal; akan dicoba kembali", error)
            Result.retry()
        }
    }
}
