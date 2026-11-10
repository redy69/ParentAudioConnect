package com.santethp

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ParentAudioMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        getSharedPreferences("parent_audio", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()

        Log.i(TAG, "FCM registration token refreshed")
        registerRefreshedToken(token)
    }

    private fun registerRefreshedToken(token: String) {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val role = prefs.getString("pair_role", null) ?: return
        val codes = when (role) {
            "child" -> listOfNotNull(prefs.getString("pair_code", null))
            "parent" -> {
                val result = mutableListOf<String>()
                val saved = prefs.getString("parent_children", null)
                if (!saved.isNullOrBlank()) {
                    try {
                        val json = JSONObject(saved)
                        json.keys().forEach { result.add(it) }
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not read saved child list", error)
                    }
                }
                if (result.isEmpty()) prefs.getString("pair_code", null)?.let { result.add(it) }
                result
            }
            else -> emptyList()
        }.filter { it.isNotBlank() }.distinct()

        if (codes.isEmpty()) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { result ->
            val idToken = result.token ?: return@addOnSuccessListener
            codes.forEach { code ->
                networkExecutor.execute {
                    registerToken(code, idToken, token)
                }
            }
        }.addOnFailureListener { error ->
            Log.w(TAG, "Could not obtain Firebase token for FCM registration", error)
        }
    }

    private fun registerToken(code: String, idToken: String, fcmToken: String) {
        val connection = URL(REGISTER_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("idToken", idToken)
                .put("code", code)
                .put("fcmToken", fcmToken)
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "FCM token registration failed for pairing $code: HTTP $status $detail")
            } else {
                Log.i(TAG, "FCM token registered for pairing $code")
            }
        } catch (error: Exception) {
            Log.w(TAG, "FCM token registration request failed", error)
        } finally {
            connection.disconnect()
        }
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
        private const val REGISTER_URL =
            "https://parent-audio-token.yudhis-r15.workers.dev/register-device"
        private val networkExecutor = Executors.newSingleThreadExecutor()
    }
}
