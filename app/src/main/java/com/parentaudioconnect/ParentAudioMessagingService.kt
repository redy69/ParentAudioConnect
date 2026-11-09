package com.santethp

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ParentAudioMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Keep the current device token locally until the authenticated
        // parent-child registration endpoint is implemented.
        getSharedPreferences("parent_audio", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()

        Log.i(TAG, "FCM registration token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Audio delivery will be connected after the secure server-side
        // pairing and file-download flow is implemented.
        Log.i(TAG, "FCM message received")
    }

    companion object {
        private const val TAG = "ParentAudioFCM"
    }
}
