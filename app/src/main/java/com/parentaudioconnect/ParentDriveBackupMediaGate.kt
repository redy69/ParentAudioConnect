package com.santethp

import android.content.Context
import android.content.Intent
import android.util.Log

/** Coordinates parent-side media sessions with the foreground Google Drive backup service. */
object ParentDriveBackupMediaGate {
    const val REASON_CAMERA_LIVE = "camera_live"
    const val REASON_AUDIO_LIVE = "audio_live"
    const val REASON_MP3_TRANSFER = "mp3_transfer"
    const val REASON_CHILD_AUDIO = "child_audio_activity"
    const val REASON_PARENT_MEDIA_SEND = "parent_media_send"

    private const val TAG = "SantetHP-BackupGate"

    /**
     * Returns true when the Drive backup service is configured as active.
     * This helper never starts a new backup service by itself.
     */
    fun pause(context: Context, reason: String): Boolean {
        val app = context.applicationContext
        val active = app.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
            .getBoolean("parent_drive_backup_service_active", false)
        if (!active) return false
        send(app, ParentDriveBackupService.ACTION_PAUSE_FOR_MEDIA, reason)
        return true
    }

    fun resume(context: Context, reason: String) {
        val app = context.applicationContext
        val active = app.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
            .getBoolean("parent_drive_backup_service_active", false)
        if (!active) return
        send(app, ParentDriveBackupService.ACTION_RESUME_AFTER_MEDIA, reason)
    }

    private fun send(context: Context, action: String, reason: String) {
        val intent = Intent(context, ParentDriveBackupService::class.java).apply {
            this.action = action
            putExtra(ParentDriveBackupService.EXTRA_MEDIA_REASON, reason)
        }
        try {
            context.startService(intent)
        } catch (error: Exception) {
            Log.w(TAG, "Tidak dapat mengubah status jeda backup untuk $reason", error)
        }
    }
}
