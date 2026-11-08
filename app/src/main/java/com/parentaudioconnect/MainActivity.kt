package com.parentaudioconnect

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
            setBackgroundColor(Color.rgb(15, 20, 35))
        }

        val title = TextView(this).apply {
            text = "Parent Audio Connect"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Selamat datang"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val parentButton = createButton("MODE ORANG TUA")
        val childButton = createButton("MODE ANAK")

        parentButton.setOnClickListener {
            statusText.text = "Mode Orang Tua dipilih"
            Toast.makeText(this, "Mode Orang Tua", Toast.LENGTH_SHORT).show()
        }

        childButton.setOnClickListener {
            statusText.text = "Mode Anak dipilih"
            Toast.makeText(this, "Mode Anak", Toast.LENGTH_SHORT).show()
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(parentButton)
        layout.addView(childButton)

        setContentView(layout)

        requestMicrophonePermission()
    }

    private fun createButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }
        }
    }

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }
}
