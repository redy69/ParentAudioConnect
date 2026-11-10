package com.santethp

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ParentAudioMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        // Re-register immediately when Firebase refreshes the token. The
        // activity also performs registration after pairing and on startup.
        val code = prefs.getString("pair_code", null)
        if (prefs.getString("pair_role", null) == "child" && !code.isNullOrBlank()) {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)
                ?.addOnSuccessListener { result ->
                    val idToken = result.token
                    if (!idToken.isNullOrBlank()) {
                        Thread {
                            var connection: HttpURLConnection? = null
                            try {
                                connection = URL("https://parent-audio-token.yudhis-r15.workers.dev/register-device")
                                    .openConnection() as HttpURLConnection
                                connection.requestMethod = "POST"
                                connection.connectTimeout = 15000
                                connection.readTimeout = 15000
                                connection.doOutput = true
                                connection.setRequestProperty("Content-Type", "application/json")
                                val body = JSONObject()
                                    .put("idToken", idToken)
                                    .put("code", code)
                                    .put("fcmToken", token)
                                connection.outputStream.use {
                                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                                }
                                val responseCode = connection.responseCode
                                Log.i(TAG, "FCM device registration response: $responseCode")
                            } catch (error: Exception) {
                                Log.w(TAG, "FCM device registration after token refresh failed", error)
                            } finally {
                                connection?.disconnect()
                            }
                        }.start()
                    }
                }
        }

        Log.i(TAG, "FCM registration token refreshed and saved")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val command = message.data["command"]
        val code = message.data["code"]
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val savedCode = prefs.getString("pair_code", null)
        val isChild = prefs.getString("pair_role", null) == "child"
        val isArmed = prefs.getBoolean("remote_camera_armed", false)

        if (command in setOf("CAMERA_START", "CAMERA_SWITCH", "CAMERA_STOP") &&
            isChild && isArmed && !code.isNullOrBlank() && code == savedCode
        ) {
            try {
                val cameraIntent = android.content.Intent(
                    this, RemoteCameraService::class.java
                ).apply {
                    action = when (command) {
                        "CAMERA_SWITCH" -> RemoteCameraService.ACTION_SWITCH_CAMERA
                        "CAMERA_STOP" -> RemoteCameraService.ACTION_STOP_CAMERA
                        else -> RemoteCameraService.ACTION_START_CAMERA
                    }
                    putExtra(RemoteCameraService.EXTRA_REQUEST_ID, message.data["requestId"])
                    putExtra("facing", message.data["facing"] ?: "back")
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
