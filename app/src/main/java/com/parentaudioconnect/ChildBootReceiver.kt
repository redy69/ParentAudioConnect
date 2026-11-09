package com.parentaudioconnect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

/**
 * Restarts the child's LiveKit foreground service after device boot.
 * Only restores an already paired child device; it does not create a new pairing.
 */
class ChildBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val role = prefs.getString("pair_role", null)
        val code = prefs.getString("pair_code", null)
        if (role != "child" || code.isNullOrBlank()) return

        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.w("ChildBootReceiver", "Firebase user missing after boot; app launch required")
            return
        }

        val serviceIntent = Intent(context, ChildAudioConnectionService::class.java).apply {
            action = ChildAudioConnectionService.ACTION_CONNECT
            putExtra(ChildAudioConnectionService.EXTRA_PAIR_CODE, code)
            // Service refreshes Firebase ID token itself after boot.
            putExtra(ChildAudioConnectionService.EXTRA_FIREBASE_ID_TOKEN, "")
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (error: Exception) {
            Log.e("ChildBootReceiver", "Could not restart child audio service after boot", error)
        }
    }
}