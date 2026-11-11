package com.santethp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Device status and FCM registration are restored by the app/worker.
 * Audio transport is intentionally not started at boot; it starts only
 * after an authenticated AUDIO_START command from the parent device.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child") return
        ChildDeviceStatusWorker.schedule(context)
    }
}
