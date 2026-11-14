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
    private var roomEventsJob: kotlinx.coroutines.Job? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var connecting = false
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
            setBackgroundColor(Color.rgb(8, 15, 30))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        status = TextView(this).apply {
            text = "Menghubungkan kamera anak…"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.025f
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = dp(48)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(22, 43, 78), Color.rgb(17, 31, 57))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(53, 83, 130))
            }
        }
        renderer = SurfaceViewRenderer(this)
        val videoFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 8, 18))
            elevation = dp(2).toFloat()
            addView(renderer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val switch = createCameraControl("GANTI KAMERA", Color.rgb(54, 94, 190))
        switch.setOnClickListener { switchCamera() }
        val stop = createCameraControl("HENTIKAN", Color.rgb(176, 57, 68))
        stop.setOnClickListener { stopCameraAndClose() }
        controls.addView(switch, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            marginEnd = dp(6)
        })
        controls.addView(stop, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            marginStart = dp(6)
        })
        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })
        root.addView(videoFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply {
            bottomMargin = dp(12)
        })
        root.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)

        connectToCameraRoom()
    }

    private fun connectToCameraRoom() {
        if (connecting || isFinishing || isDestroyed) return
        connecting = true
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
                val liveKitConnection = LiveKitFailover.connectWithServer(
                    response = response,
                    createRoom = { LiveKit.create(applicationContext) },
                    prepareRoom = { candidateRoom ->
                        // Publish the candidate reference before connect so an immediate
                        // disconnect during the handshake is observable by the event collector.
                        room = candidateRoom
                        candidateRoom.initVideoRenderer(renderer)
                        roomEventsJob?.cancel()
                        roomEventsJob = launch {
                            candidateRoom.events.collect { event ->
                                when (event) {
                                    is RoomEvent.TrackSubscribed -> {
                                        val video = event.track as? VideoTrack
                                        if (video != null) {
                                            runOnUiThread {
                                                video.addRenderer(renderer)
                                                status.text = "KAMERA ANAK TERHUBUNG"
                                            }
                                        }
                                    }
                                    is RoomEvent.Disconnected -> {
                                        if (room === candidateRoom) {
                                            room = null
                                            runOnUiThread {
                                                status.text = "Koneksi kamera terputus • menyambungkan kembali…"
                                            }
                                            // Always queue recovery. The reconnect coroutine waits for
                                            // the current candidate loop to finish before starting again,
                                            // avoiding a lost disconnect event during the connect/finally race.
                                            scheduleCameraReconnect(candidateRoom)
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                )
                val activeRoom = liveKitConnection.room
                if (room !== activeRoom) {
                    // The room disconnected just as connect() completed.
                    try { activeRoom.disconnect() } catch (_: Exception) { }
                    try { activeRoom.release() } catch (_: Exception) { }
                    scheduleCameraReconnect(null)
                    return@launch
                }
                val selection = JSONObject().put("idToken", idToken).put("code", code)
                    .put("kind", "camera").put("serverId", liveKitConnection.serverId)
                withContext(Dispatchers.IO) {
                    postJson("$WORKER_BASE/select-server", selection)
                }
                room = activeRoom
                status.text = "Server disamakan • meminta kamera HP anak…"
                sendCameraCommand("start", "back", closeAfter = false)
            } catch (error: Exception) {
                // LiveKitFailover releases failed candidate rooms, but clear the
                // activity reference too so the retry guard does not mistake a
                // failed candidate for an active connection.
                room = null
                roomEventsJob?.cancel()
                roomEventsJob = null
                status.text = "Gagal menghubungkan kamera: ${error.localizedMessage ?: "periksa koneksi"}"
                scheduleCameraReconnect(null)
            } finally {
                connecting = false
            }
        }
    }

    private fun scheduleCameraReconnect(disconnectedRoom: Room?) {
        if (reconnectJob?.isActive == true || isFinishing || isDestroyed) return
        reconnectJob = scope.launch {
            kotlinx.coroutines.delay(2_000)
            // Do not drop the recovery attempt if the previous failover loop is
            // still unwinding. Wait until it exits, then reconnect only if no room
            // has become active in the meantime.
            while (connecting && !isFinishing && !isDestroyed) {
                kotlinx.coroutines.delay(500)
            }
            if (isFinishing || isDestroyed) return@launch
            val oldRoom = disconnectedRoom
            if (oldRoom != null) {
                roomEventsJob?.cancel()
                roomEventsJob = null
                try { oldRoom.disconnect() } catch (_: Exception) { }
                try { oldRoom.release() } catch (_: Exception) { }
            }
            if (room == null) connectToCameraRoom()
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

    private fun createCameraControl(label: String, color: Int): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        letterSpacing = 0.045f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = dp(50)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.argb(75, 255, 255, 255))
        }
        elevation = dp(2).toFloat()
        stateListAnimator = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        stopCameraAndClose()
    }

    override fun onDestroy() {
        reconnectJob?.cancel()
        reconnectJob = null
        scope.cancel()
        roomEventsJob?.cancel()
        roomEventsJob = null
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
