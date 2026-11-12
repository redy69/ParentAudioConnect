package com.santethp

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Task
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraViewerActivity : Activity() {
    companion object {
        const val EXTRA_CODE = "camera_pairing_code"
        private const val WORKER_BASE = "https://parent-audio-token.yudhis-r15.workers.dev"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private lateinit var renderer: SurfaceViewRenderer
    private lateinit var status: TextView
    private var code: String = ""
    private var facing = "back"
    private var stopping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        if (code.isBlank()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        status = TextView(this).apply {
            text = "Menghubungkan kamera anak…"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minHeight = dp(48)
            setLineSpacing(dp(2).toFloat(), 1f)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(25, 35, 54))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(66, 83, 112))
            }
        }
        renderer = SurfaceViewRenderer(this)
        val videoFrame = FrameLayout(this).apply {
            addView(renderer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val switch = Button(this).apply {
            text = "GANTI KAMERA"
            styleCameraControl(Color.rgb(49, 88, 178))
            setOnClickListener { switchCamera() }
        }
        val stop = Button(this).apply {
            text = "HENTIKAN"
            styleCameraControl(Color.rgb(171, 57, 66))
            setOnClickListener { stopCameraAndClose() }
        }
        controls.addView(switch, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            rightMargin = dp(5)
        })
        controls.addView(stop, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            leftMargin = dp(5)
        })
        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(videoFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
        setContentView(root)

        room = LiveKit.create(applicationContext)
        room?.initVideoRenderer(renderer)
        connectToCameraRoom()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun styleCameraControl(accent: Int) {
        setTextColor(Color.WHITE)
        textSize = 12f
        isAllCaps = false
        minHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        stateListAnimator = null
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(accent, Color.rgb(
                (Color.red(accent) * 0.82f).toInt(),
                (Color.green(accent) * 0.82f).toInt(),
                (Color.blue(accent) * 0.82f).toInt()
            ))
        ).apply { cornerRadius = dp(14).toFloat() }
        elevation = dp(2).toFloat()
    }

    private fun connectToCameraRoom() {
        scope.launch {
            try {
                status.text = "Meminta akses tayangan kamera…"
                val idToken = FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(false)?.awaitResult()?.token
                    ?: throw IllegalStateException("Sesi Firebase orang tua tidak tersedia")
                val response = withContext(Dispatchers.IO) {
                    postJson("$WORKER_BASE/camera-token", JSONObject()
                        .put("idToken", idToken).put("code", code))
                }
                if (response.optString("role") != "parent") {
                    throw IllegalStateException("Akun ini bukan orang tua untuk perangkat tersebut")
                }
                val activeRoom = room ?: return@launch
                launch {
                    activeRoom.events.collect { event ->
                        if (event is RoomEvent.TrackSubscribed) {
                            val video = event.track as? VideoTrack
                            if (video != null) {
                                runOnUiThread {
                                    video.addRenderer(renderer)
                                    status.text = "KAMERA ANAK TERHUBUNG"
                                }
                            }
                        }
                    }
                }
                activeRoom.connect(response.getString("serverUrl"), response.getString("token"))
                status.text = "Terhubung. Menunggu video dari HP anak…"
            } catch (error: Exception) {
                status.text = "Gagal menghubungkan kamera: ${error.localizedMessage ?: "periksa koneksi"}"
            }
        }
    }

    private fun switchCamera() {
        facing = if (facing == "back") "front" else "back"
        sendCameraCommand("switch", facing, closeAfter = false)
    }

    private fun stopCameraAndClose() {
        if (stopping) return
        stopping = true
        sendCameraCommand("stop", facing, closeAfter = true)
    }

    private fun sendCameraCommand(action: String, requestedFacing: String, closeAfter: Boolean) {
        scope.launch {
            try {
                val idToken = FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(false)?.awaitResult()?.token
                    ?: throw IllegalStateException("Sesi Firebase orang tua tidak tersedia")
                withContext(Dispatchers.IO) {
                    postJson("$WORKER_BASE/request-camera", JSONObject()
                        .put("idToken", idToken)
                        .put("code", code)
                        .put("requestId", UUID.randomUUID().toString())
                        .put("action", action)
                        .put("facing", requestedFacing))
                }
                status.text = if (closeAfter) "Perintah menghentikan kamera dikirim" else
                    "Perintah kamera " + (if (requestedFacing == "front") "depan" else "belakang") + " dikirim"
                if (closeAfter) finish()
            } catch (error: Exception) {
                stopping = false
                Toast.makeText(this@CameraViewerActivity,
                    "Perintah gagal: ${error.localizedMessage ?: "koneksi bermasalah"}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun postJson(endpoint: String, body: JSONObject): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val response = JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
            if (statusCode !in 200..299) {
                throw IllegalStateException(response.optString("error", "Server menolak permintaan"))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        stopCameraAndClose()
    }

    override fun onDestroy() {
        scope.cancel()
        val activeRoom = room
        room = null
        try { renderer.release() } catch (_: Exception) { }
        if (activeRoom != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try { activeRoom.disconnect() } finally { activeRoom.release() }
            }
        }
        super.onDestroy()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(
                task.exception ?: IllegalStateException("Autentikasi Firebase gagal")
            )
        }
    }
