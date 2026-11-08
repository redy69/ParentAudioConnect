package com.parentaudioconnect

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Timestamp

import java.security.SecureRandom
import java.util.Date

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private lateinit var statusText: TextView
    private lateinit var codeText: TextView

    private var pairingListener: ListenerRegistration? = null

    private val characters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

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
            text = "Memeriksa koneksi..."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        codeText = TextView(this).apply {
            text = ""
            textSize = 30f
            setTextColor(Color.rgb(128, 222, 234))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val parentButton = createButton("BUAT KODE ORANG TUA")
        val childButton = createButton("MASUKKAN KODE ANAK")

        parentButton.setOnClickListener {
            createPairingCode()
        }

        childButton.setOnClickListener {
            showCodeDialog()
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(codeText)
        layout.addView(parentButton)
        layout.addView(childButton)

        setContentView(layout)

        if (auth.currentUser != null) {
            statusText.text = "Firebase terhubung!"
        } else {
            statusText.text = "Firebase belum terhubung. Buka ulang aplikasi."
        }
    }

    private fun createButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }
        }
    }

    private fun generateCode(): String {
        val random = SecureRandom()

        return (1..8).map {
            characters[random.nextInt(characters.length)]
        }.joinToString("")
    }

    private fun createPairingCode() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            statusText.text = "Firebase belum terhubung!"
            return
        }

        statusText.text = "Membuat kode pasangan..."
        codeText.text = ""

        val code = generateCode()

        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "parentUid" to uid,
            "childUid" to null,
            "createdAt" to Timestamp(Date(now)),
            "expiresAt" to Timestamp(Date(now + 15 * 60 * 1000))
        )

        db.collection("pairings")
            .document(code)
            .set(data)
            .addOnSuccessListener {

                codeText.text = code
                statusText.text = "Bagikan kode ini kepada HP anak"

                listenForChild(code)

            }
            .addOnFailureListener { error ->

                statusText.text = "Gagal membuat kode"
                Toast.makeText(
                    this,
                    error.localizedMessage ?: "Terjadi kesalahan",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun listenForChild(code: String) {

        pairingListener?.remove()

        pairingListener = db.collection("pairings")
            .document(code)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    statusText.text = "Gagal memeriksa pasangan"
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {

                    val childUid = snapshot.getString("childUid")

                    if (!childUid.isNullOrEmpty()) {
                        statusText.text = "HP ANAK BERHASIL TERHUBUNG!"
                    }
                }
            }
    }

    private fun showCodeDialog() {

        val input = EditText(this).apply {
            hint = "Masukkan 8 karakter kode"
            isSingleLine = true
        }

        AlertDialog.Builder(this)
            .setTitle("Hubungkan dengan Orang Tua")
            .setMessage("Masukkan kode dari HP orang tua.")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hubungkan") { _, _ ->

                val code = input.text.toString()
                    .trim()
                    .uppercase()

                if (code.length != 8) {
                    statusText.text = "Kode harus 8 karakter"
                } else {
                    joinPairing(code)
                }
            }
            .show()
    }

    private fun joinPairing(code: String) {

        val uid = auth.currentUser?.uid

        if (uid == null) {
            statusText.text = "Firebase belum terhubung!"
            return
        }

        statusText.text = "Memeriksa kode..."

        val document = db.collection("pairings").document(code)

        document.get()
            .addOnSuccessListener { snapshot ->

                if (!snapshot.exists()) {
                    statusText.text = "Kode tidak ditemukan atau kedaluwarsa"
                    return@addOnSuccessListener
                }

                val expiresAt = snapshot.getTimestamp("expiresAt")
                val childUid = snapshot.getString("childUid")
                val parentUid = snapshot.getString("parentUid")

                if (parentUid == uid) {
                    statusText.text = "Gunakan HP anak untuk memasangkan"
                    return@addOnSuccessListener
                }

                if (expiresAt == null ||
                    expiresAt.toDate().time <= System.currentTimeMillis()
                ) {
                    statusText.text = "Kode sudah kedaluwarsa"
                    return@addOnSuccessListener
                }

                if (!childUid.isNullOrEmpty()) {
                    statusText.text = "Kode sudah digunakan"
                    return@addOnSuccessListener
                }

                document.update("childUid", uid)
                    .addOnSuccessListener {

                        codeText.text = code
                        statusText.text = "BERHASIL TERHUBUNG DENGAN ORANG TUA!"

                    }
                    .addOnFailureListener { error ->

                        statusText.text = "Gagal memasangkan perangkat"

                        Toast.makeText(
                            this,
                            error.localizedMessage ?: "Terjadi kesalahan",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->

                statusText.text = "Gagal memeriksa kode"

                Toast.makeText(
                    this,
                    error.localizedMessage ?: "Terjadi kesalahan",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    override fun onDestroy() {
        pairingListener?.remove()
        super.onDestroy()
    }
}
