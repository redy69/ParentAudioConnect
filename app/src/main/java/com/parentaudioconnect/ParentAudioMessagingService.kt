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

        val command = message.data["command"]
        val code = message.data["code"]
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val savedCode = prefs.getString("pair_code", null)
        val isChild = prefs.getString("pair_role", null) == "child"
        val isArmed = prefs.getBoolean("remote_camera_armed", false)

        if (command == "CAMERA_START" && isChild && isArmed &&
            !code.isNullOrBlank() && code == savedCode
        ) {
            try {
                val cameraIntent = android.content.Intent(
                    this, RemoteCameraService::class.java
                ).apply {
                    action = RemoteCameraService.ACTION_START_CAMERA
                    putExtra(
                        RemoteCameraService.EXTRA_REQUEST_ID,
                        message.data["requestId"]
                    )
                }
                // This command only reaches an already armed camera service.
                // If Android has stopped that service, no background camera
                // activation is attempted.
                startService(cameraIntent)
                Log.i(TAG, "Remote camera request delivered to armed service")
            } catch (error: Exception) {
                Log.w(TAG, "Could not deliver camera request to active service", error)
            }
        } else {
            Log.i(TAG, "FCM message ignored: no matching armed camera session")
        }
    }

    companion object {
        private const val TAG = "ParentAudioFCM"
    }
}
