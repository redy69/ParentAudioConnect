package com.parentaudioconnect

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import com.google.firebase.auth.FirebaseAuth

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

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
            text = "Menghubungkan ke Firebase..."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val parentButton = createButton("MODE ORANG TUA")
        val childButton = createButton("MODE ANAK")

        parentButton.setOnClickListener {
            statusText.text = "Mode Orang Tua dipilih"
            Toast.makeText(
                this,
                "Mode Orang Tua",
                Toast.LENGTH_SHORT
            ).show()
        }

        childButton.setOnClickListener {
            statusText.text = "Mode Anak dipilih"
            Toast.makeText(
                this,
                "Mode Anak",
                Toast.LENGTH_SHORT
            ).show()
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(parentButton)
        layout.addView(childButton)

        setContentView(layout)

        connectFirebase()
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

    private fun connectFirebase() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            statusText.text = "Firebase terhubung!"
            return
        }

        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    statusText.text = "Firebase terhubung!"

                    Toast.makeText(
                        this,
                        "Identitas perangkat berhasil dibuat",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    statusText.text =
                        "Gagal terhubung ke Firebase. Periksa internet."

                    Toast.makeText(
                        this,
                        task.exception?.localizedMessage
                            ?: "Koneksi gagal",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}
