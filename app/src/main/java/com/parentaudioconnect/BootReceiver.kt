package com.santethp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        val role = prefs.getString("pair_role", null)

        // Only restore a previously paired child device. Never start a microphone.
        if (role != "child" || code.isNullOrBlank()) return

        val serviceIntent = Intent(context, ChildAudioConnectionService::class.java).apply {
            action = ChildAudioConnectionService.ACTION_CONNECT
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (error: Exception) {
            android.util.Log.e("ParentAudioBoot", "Unable to restore child audio connection", error)
        }
    }
}
