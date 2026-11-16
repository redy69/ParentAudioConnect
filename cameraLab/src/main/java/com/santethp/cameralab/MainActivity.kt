package com.santethp.cameralab

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private enum class Transport { LIVEKIT, P2P }

    private var selectedTransport = Transport.LIVEKIT
    private lateinit var status: TextView
    private lateinit var liveKitButton: Button
    private lateinit var p2pButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        updateTransportButtons()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(Color.rgb(7, 30, 24))
        }

        val title = TextView(this).apply {
            text = "SantetHP Camera"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Laboratorium pengujian audio dan video"
            textSize = 14f
            setTextColor(Color.rgb(173, 205, 193))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(28))
        }
        root.addView(subtitle, matchWrap())

        val transportTitle = sectionTitle("PILIH JALUR KONEKSI")
        root.addView(transportTitle, matchWrap())

        liveKitButton = actionButton("LiveKit  •  SFU")
        p2pButton = actionButton("WebRTC  •  P2P")
        root.addView(liveKitButton, matchWrap(dp(10)))
        root.addView(p2pButton, matchWrap(dp(10)))

        liveKitButton.setOnClickListener {
            selectedTransport = Transport.LIVEKIT
            updateTransportButtons()
            showStatus("Jalur LiveKit dipilih. Mesin media belum diaktifkan pada tahap kerangka ini.")
        }
        p2pButton.setOnClickListener {
            selectedTransport = Transport.P2P
            updateTransportButtons()
            showStatus("Jalur WebRTC P2P dipilih. Signaling dan STUN/TURN belum diaktifkan pada tahap kerangka ini.")
        }

        val modeTitle = sectionTitle("MODE PENGUJIAN")
        modeTitle.setPadding(0, dp(26), 0, dp(8))
        root.addView(modeTitle, matchWrap())

        val cameraButton = actionButton("Live Camera + Audio")
        root.addView(cameraButton, matchWrap(dp(10)))
        cameraButton.setOnClickListener { requestMediaPermissions("Live Camera + Audio") }

        val audioButton = actionButton("Audio Live saja")
        root.addView(audioButton, matchWrap(dp(10)))
        audioButton.setOnClickListener { requestMediaPermissions("Audio Live") }

        status = TextView(this).apply {
            text = "Belum ada sesi aktif. Pilih jalur dan mode pengujian."
            textSize = 14f
            setTextColor(Color.rgb(210, 229, 221))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(22), dp(12), dp(12))
        }
        root.addView(status, matchWrap())

        val note = TextView(this).apply {
            text = "Tahap 1: kerangka aplikasi dan izin perangkat. Belum mengirim kamera atau mikrofon."
            textSize = 12f
            setTextColor(Color.rgb(139, 169, 157))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(24), dp(8), 0)
        }
        root.addView(note, matchWrap())

        setContentView(root)
    }

    private fun requestMediaPermissions(mode: String) {
        val required = if (mode == "Audio Live") {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            showStatus("Izin kamera dan mikrofon tersedia. Mesin media tahap berikutnya belum dipasang.")
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_MEDIA)
            showStatus("Permintaan izin diperlukan sebelum mode $mode dapat digunakan.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MEDIA) {
            val granted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            showStatus(
                if (granted) "Izin kamera dan mikrofon diberikan. Mesin media belum diaktifkan."
                else "Izin belum lengkap. Tidak ada kamera atau mikrofon yang dijalankan."
            )
        }
    }

    private fun updateTransportButtons() {
        liveKitButton.alpha = if (selectedTransport == Transport.LIVEKIT) 1f else 0.58f
        p2pButton.alpha = if (selectedTransport == Transport.P2P) 1f else 0.58f
    }

    private fun showStatus(message: String) {
        status.text = message
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
        textSize = 15f
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(11, 107, 80)))
    }

    private fun matchWrap(topMargin: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (topMargin > 0) this.topMargin = topMargin
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_MEDIA = 410
    }
}
