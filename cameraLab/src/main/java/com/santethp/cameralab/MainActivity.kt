package com.santethp.cameralab

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task

class MainActivity : Activity() {

    private enum class Role { SENDER, VIEWER }
    private enum class Mode { CAMERA_AUDIO, AUDIO_ONLY }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var status: TextView
    private lateinit var codeInput: EditText
    private lateinit var videoRenderer: SurfaceViewRenderer
    private var room: Room? = null
    private var eventJob: kotlinx.coroutines.Job? = null
    private var role: Role? = null
    private var mode = Mode.CAMERA_AUDIO
    private var pairingCode: String? = null
    private var pairingListener: ListenerRegistration? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val app = FirebaseApp.initializeApp(this, FirebaseOptions.Builder()
                .setApplicationId("1:510992503464:android:dc7ad2dcec068eb927289d")
                .setApiKey("AIzaSyArnd_Ejlu045Sgu_UYKaD0BLs3BtFLTDg")
                .setProjectId("parentaudioconnect")
                .setGcmSenderId("510992503464")
                .build())
                ?: FirebaseApp.getInstance()
            auth = FirebaseAuth.getInstance(app)
            db = FirebaseFirestore.getInstance(app)
            buildScreen()
            ensureSignedIn()
        } catch (error: Exception) {
            buildScreen()
            showStatus("Inisialisasi Firebase gagal: ${error.localizedMessage ?: "kesalahan"}")
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.rgb(7, 30, 24))
        }
        root.addView(TextView(this).apply {
            text = "SantetHP Camera Lab"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "Uji jalur media antar dua HP secara terpisah dari SantetHP utama."
            textSize = 13f
            setTextColor(Color.rgb(173, 205, 193))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        }, matchWrap())

        root.addView(sectionTitle("MODE PENGUJIAN"), matchWrap())
        val cameraMode = actionButton("Live Camera + Audio")
        val audioMode = actionButton("Audio Live saja")
        root.addView(cameraMode, matchWrap(dp(8)))
        root.addView(audioMode, matchWrap(dp(8)))
        cameraMode.setOnClickListener { mode = Mode.CAMERA_AUDIO; showStatus("Mode kamera + mikrofon dipilih.") }
        audioMode.setOnClickListener { mode = Mode.AUDIO_ONLY; showStatus("Mode audio saja dipilih.") }

        root.addView(sectionTitle("PERAN HP INI"), matchWrap(dp(22)))
        val sender = actionButton("KIRIM dari HP ini")
        val viewer = actionButton("TERIMA di HP ini")
        root.addView(sender, matchWrap(dp(8)))
        root.addView(viewer, matchWrap(dp(8)))
        sender.setOnClickListener { chooseRole(Role.SENDER) }
        viewer.setOnClickListener { chooseRole(Role.VIEWER) }

        codeInput = EditText(this).apply {
            hint = "Masukkan kode 8 karakter dari HP pengirim"
            textSize = 15f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(24, 54, 44))
        }
        root.addView(codeInput, matchWrap(dp(12)))
        val join = actionButton("GABUNG dengan kode")
        root.addView(join, matchWrap(dp(8)))
        join.setOnClickListener { claimAndConnect(codeInput.text.toString().trim().uppercase()) }

        val stop = actionButton("HENTIKAN SESI")
        root.addView(stop, matchWrap(dp(18)))
        stop.setOnClickListener { stopSession() }

        videoRenderer = SurfaceViewRenderer(this)
        val videoFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(videoRenderer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        root.addView(videoFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(16); bottomMargin = dp(12) })

        status = TextView(this).apply {
            text = "Menyiapkan autentikasi…"
            textSize = 13f
            setTextColor(Color.rgb(210, 229, 221))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(8))
        }
        root.addView(status, matchWrap())
        setContentView(root)
    }

    private fun ensureSignedIn() {
        if (!::auth.isInitialized) return
        if (auth.currentUser != null) {
            showStatus("Firebase siap. Pilih mode dan peran HP.")
            return
        }
        auth.signInAnonymously().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) showStatus("Firebase siap. Identitas Camera Lab terpisah dari sesi aplikasi utama.")
            else showStatus("Login Camera Lab gagal: ${task.exception?.localizedMessage ?: "periksa internet"}")
        }
    }

    private fun chooseRole(selected: Role) {
        role = selected
        if (selected == Role.SENDER) {
            createPairing()
        } else {
            showStatus("HP ini akan menerima media. Masukkan kode yang dibuat HP pengirim.")
        }
    }

    private fun createPairing() {
        if (!::auth.isInitialized || auth.currentUser == null) {
            showStatus("Autentikasi belum siap. Tunggu sebentar lalu coba lagi.")
            return
        }
        busy = true
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val code = (1..8).map { chars[random.nextInt(chars.length)] }.joinToString("")
        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any?>(
            "parentUid" to auth.currentUser!!.uid,
            "childUid" to null,
            "createdAt" to Timestamp(Date(now)),
            "expiresAt" to Timestamp(Date(now + 5 * 60 * 1000)),
            "mediaMode" to mode.name
        )
        db.collection("pairings").document(code).set(data)
            .addOnSuccessListener {
                pairingCode = code
                busy = false
                showStatus("KODE PASANGAN: $code\nMasukkan kode ini di HP penerima. Berlaku 5 menit.")
                pairingListener?.remove()
                pairingListener = db.collection("pairings").document(code).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        showStatus("Pemantauan pasangan gagal: " + (error.localizedMessage ?: "Firestore"))
                    } else if (snapshot?.getString("childUid") != null && room == null && !busy) {
                        showStatus("HP penerima bergabung. Menyiapkan sesi media…")
                        startMediaSession()
                    }
                }
            }
            .addOnFailureListener {
                busy = false
                showStatus("Gagal membuat kode: ${it.localizedMessage ?: "Firestore menolak"}")
            }
    }

    private fun claimAndConnect(code: String) {
        if (busy) return
        if (role != Role.VIEWER) {
            showStatus("Pilih TERIMA di HP ini terlebih dahulu.")
            return
        }
        if (!code.matches(Regex("^[A-HJ-NP-Z2-9]{8}$"))) {
            showStatus("Kode harus terdiri dari 8 karakter.")
            return
        }
        if (auth.currentUser == null) {
            showStatus("Autentikasi belum siap.")
            return
        }
        busy = true
        val doc = db.collection("pairings").document(code)
        doc.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || snapshot.getTimestamp("expiresAt")?.toDate()?.time?.let { it <= System.currentTimeMillis() } != false) {
                busy = false
                showStatus("Kode tidak ditemukan atau sudah kedaluwarsa.")
                return@addOnSuccessListener
            }
            if (snapshot.getString("childUid") != null) {
                busy = false
                showStatus("Kode ini sudah digunakan. Minta kode baru.")
                return@addOnSuccessListener
            }
            mode = if (snapshot.getString("mediaMode") == Mode.AUDIO_ONLY.name) {
                Mode.AUDIO_ONLY
            } else {
                Mode.CAMERA_AUDIO
            }
            doc.update("childUid", auth.currentUser!!.uid)
                .addOnSuccessListener {
                    pairingCode = code
                    busy = false
                    startMediaSession()
                }
                .addOnFailureListener {
                    busy = false
                    showStatus("Gagal mengklaim kode: ${it.localizedMessage ?: "akses ditolak"}")
                }
        }.addOnFailureListener {
            busy = false
            showStatus("Tidak dapat membaca kode: ${it.localizedMessage ?: "periksa jaringan"}")
        }
    }

    private fun startMediaSession() {
        if (busy || room != null) return
        val code = pairingCode ?: run { showStatus("Kode pasangan hilang."); return }
        val currentRole = role ?: return
        val permissions = if (currentRole == Role.SENDER && mode == Mode.CAMERA_AUDIO) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else if (currentRole == Role.SENDER) {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        } else emptyArray()
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_MEDIA)
            return
        }
        busy = true
        scope.launch {
            try {
                showStatus("Meminta token sesi…")
                val user = auth.currentUser ?: throw IllegalStateException("Firebase belum login")
                val idToken = user.getIdToken(false).awaitResult().token
                    ?: throw IllegalStateException("Token autentikasi kosong")
                val started = System.currentTimeMillis()
                val response = withContext(Dispatchers.IO) {
                    postJson("$WORKER_BASE/token", JSONObject()
                        .put("idToken", idToken).put("code", code))
                }
                val tokenMs = System.currentTimeMillis() - started
                val expectedRole = if (currentRole == Role.SENDER) "parent" else "child"
                if (response.optString("role") != expectedRole) {
                    throw IllegalStateException("Peran token tidak sesuai: ${response.optString("role")}")
                }
                showStatus("Token diterima (${tokenMs} ms). Menghubungkan LiveKit…")
                val connected = io.livekit.android.LiveKit.create(applicationContext)
                room = connected
                if (currentRole == Role.VIEWER && mode == Mode.CAMERA_AUDIO) {
                    connected.initVideoRenderer(videoRenderer)
                }
                eventJob?.cancel()
                eventJob = scope.launch {
                    connected.events.collect { event ->
                        if (event is RoomEvent.TrackSubscribed) {
                            val video = event.track as? VideoTrack
                            if (video != null && currentRole == Role.VIEWER) {
                                video.addRenderer(videoRenderer)
                                showStatus("VIDEO TERTERIMA • LiveKit tersambung")
                            }
                        }
                        if (event is RoomEvent.Disconnected) {
                            showStatus("Koneksi LiveKit terputus.")
                        }
                    }
                }
                connected.connect(
                    url = response.optString("serverUrl"),
                    token = response.optString("token")
                )
                if (currentRole == Role.SENDER) {
                    connected.localParticipant.setMicrophoneEnabled(true)
                    if (mode == Mode.CAMERA_AUDIO) {
                        connected.localParticipant.videoTrackCaptureDefaults =
                            connected.localParticipant.videoTrackCaptureDefaults.copy(
                                position = io.livekit.android.room.track.CameraPosition.BACK
                            )
                        val cameraOn = connected.localParticipant.setCameraEnabled(true)
                        if (!cameraOn) throw IllegalStateException("Kamera tidak dapat diaktifkan")
                        showStatus("MENGIRIM KAMERA + AUDIO • sesi terlihat aktif di HP ini")
                    } else {
                        showStatus("MENGIRIM AUDIO LIVE • mikrofon aktif")
                    }
                } else {
                    if (mode == Mode.CAMERA_AUDIO) {
                        showStatus("Menunggu video dan audio dari HP pengirim…")
                    } else {
                        showStatus("Menerima audio LiveKit…")
                    }
                }
            } catch (error: Exception) {
                showStatus("GAGAL: ${error.localizedMessage ?: error.javaClass.simpleName}")
                try { room?.disconnect() } catch (_: Exception) { }
                try { room?.release() } catch (_: Exception) { }
                room = null
            } finally {
                busy = false
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MEDIA) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startMediaSession()
            } else showStatus("Izin kamera/mikrofon tidak diberikan. Tidak ada media yang dijalankan.")
        }
    }

    private fun stopSession() {
        scope.launch {
            val active = room
            room = null
            eventJob?.cancel()
            eventJob = null
            try { active?.localParticipant?.setCameraEnabled(false) } catch (_: Exception) { }
            try { active?.localParticipant?.setMicrophoneEnabled(false) } catch (_: Exception) { }
            try { active?.disconnect() } catch (_: Exception) { }
            try { active?.release() } catch (_: Exception) { }
            showStatus("Sesi dihentikan. Kamera dan mikrofon tidak aktif.")
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
                throw IllegalStateException("Worker HTTP $statusCode: " + response.optString("error", "permintaan ditolak"))
            }
            return response
        } finally { connection.disconnect() }
    }

    private fun showStatus(message: String) {
        if (::status.isInitialized) status.text = message
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(Color.rgb(87, 199, 162))
        gravity = Gravity.CENTER
    }

    private fun actionButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(11, 107, 80)))
    }

    private fun matchWrap(topMargin: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (topMargin > 0) this.topMargin = topMargin
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        eventJob?.cancel()
        pairingListener?.remove()
        scope.cancel()
        val active = room
        room = null
        try { videoRenderer.release() } catch (_: Exception) { }
        if (active != null) {
            kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try { active.disconnect() } finally { active.release() }
            }
        }
        super.onDestroy()
    }

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) continuation.resume(task.result)
                else continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase task failed")
                )
            }
        }

    companion object {
        private const val REQUEST_MEDIA = 410
        private const val WORKER_BASE = "https://parent-audio-token.yudhis-r15.workers.dev"
    }
}
