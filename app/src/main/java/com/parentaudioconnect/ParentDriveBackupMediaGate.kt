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
    private const val PAUSE_REASONS_KEY = "parent_drive_backup_media_pause_reasons"
    private const val PAUSE_TIME_PREFIX = "parent_drive_backup_media_pause_at_"
    private const val DIAGNOSTIC_REASONS_KEY = "crash_diag_active_media"

    /**
     * Records the media session even if Drive backup is not running yet.
     * If backup starts during the session, its service reads the active reasons
     * before opening the P2P transport.
     */
    fun pause(context: Context, reason: String): Boolean {
        val app = context.applicationContext
        recordPauseReason(app, reason, true)
        val active = app.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
            .getBoolean("parent_drive_backup_service_active", false)
        if (active) send(app, ParentDriveBackupService.ACTION_PAUSE_FOR_MEDIA, reason)
        return active
    }

    fun resume(context: Context, reason: String) {
        val app = context.applicationContext
        recordPauseReason(app, reason, false)
        val active = app.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
            .getBoolean("parent_drive_backup_service_active", false)
        if (active) send(app, ParentDriveBackupService.ACTION_RESUME_AFTER_MEDIA, reason)
    }

    /** Called by the service when it loads or expires a pause reason. */
    fun recordPauseReason(context: Context, reasonValue: String, active: Boolean) {
        val reason = reasonValue.trim().take(64)
        if (reason.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val reasons = prefs.getStringSet(PAUSE_REASONS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val diagnosticReasons = prefs.getStringSet(DIAGNOSTIC_REASONS_KEY, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        val editor = prefs.edit()
        if (active) {
            reasons.add(reason)
            diagnosticReasons.add(reason)
            editor.putLong(PAUSE_TIME_PREFIX + reason, System.currentTimeMillis())
        } else {
            reasons.remove(reason)
            diagnosticReasons.remove(reason)
            editor.remove(PAUSE_TIME_PREFIX + reason)
        }
        editor.putStringSet(PAUSE_REASONS_KEY, reasons)
            .putStringSet(DIAGNOSTIC_REASONS_KEY, diagnosticReasons)
            .apply()
    }

    /** Returns only fresh pause reasons, cleaning stale entries left by interrupted sessions. */
    fun activePauseReasons(context: Context): Map<String, Long> {
        val prefs = context.applicationContext.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val reasons = prefs.getStringSet(PAUSE_REASONS_KEY, emptySet())?.toSet() ?: emptySet()
        val now = System.currentTimeMillis()
        val active = linkedMapOf<String, Long>()
        reasons.forEach { reason ->
            val at = prefs.getLong(PAUSE_TIME_PREFIX + reason, 0L)
            if (at > 0L && now - at <= maxAgeFor(reason)) active[reason] = at
            else recordPauseReason(context, reason, false)
        }
        return active
    }

    private fun maxAgeFor(reason: String): Long = when (reason) {
        REASON_CHILD_AUDIO -> 45_000L
        REASON_MP3_TRANSFER, REASON_PARENT_MEDIA_SEND -> 15L * 60L * 1000L
        REASON_AUDIO_LIVE -> 2L * 60L * 60L * 1000L
        REASON_CAMERA_LIVE -> 6L * 60L * 60L * 1000L
        else -> 30L * 60L * 1000L
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
