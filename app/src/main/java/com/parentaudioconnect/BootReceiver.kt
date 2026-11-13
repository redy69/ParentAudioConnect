package com.santethp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Restores lightweight device reporting and the child's explicitly consented
 * gallery P2P foreground service after boot or an app update.
 *
 * Audio transport is intentionally not started here; it still requires an
 * authenticated AUDIO_START command from the parent device.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SantetHP-BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child") return

        ChildDeviceStatusWorker.schedule(context)

        // Restore the transport when either the gallery consent was
        // acknowledged by the server or the child separately consented to
        // Drive backup. The service re-checks media access and pairing code.
        val gallerySharingEnabled =
            prefs.getBoolean("child_gallery_sharing_enabled", false)
        val serverConsentConfirmed =
            prefs.getBoolean("child_gallery_server_consent_confirmed", false)
        val backupConsent = prefs.getBoolean("child_drive_backup_consent", false)
        if ((gallerySharingEnabled && serverConsentConfirmed) || backupConsent) {
            try {
                val serviceIntent = Intent(context, ChildGalleryP2pService::class.java)
                    .setAction(ChildGalleryP2pService.ACTION_START)
                ContextCompat.startForegroundService(context, serviceIntent)
                Log.i(TAG, "Memulihkan layanan galeri P2P setelah $action")
            } catch (error: Exception) {
                // Never change the saved consent state on a transient boot/startup
                // failure. The service can be started again when the app opens.
                Log.e(TAG, "Gagal memulihkan layanan galeri setelah $action", error)
            }
        }
    }
}
