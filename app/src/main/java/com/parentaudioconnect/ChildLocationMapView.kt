package com.santethp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.Timestamp
import java.util.Locale

/** Parent-side OpenStreetMap view for the currently selected paired child. */
class ChildLocationMapView(context: Context) : LinearLayout(context) {
    private val status = TextView(context)
    private val map = WebView(context)
    private var lastMapUrl: String? = null

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(10))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.WHITE, Color.rgb(241, 247, 255))
        ).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.rgb(194, 215, 246))
        }
        elevation = dp(2).toFloat()

        addView(TextView(context).apply {
            text = "📍  PETA LOKASI PERANGKAT ANAK"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(42, 70, 125))
            setPadding(0, 0, 0, dp(7))
        })
        status.text = "Menunggu lokasi dari HP anak. Berbagi lokasi harus diaktifkan dari perangkat anak."
        status.textSize = 11.5f
        status.setTextColor(Color.rgb(104, 117, 140))
        status.setPadding(0, 0, 0, dp(9))
        addView(status)

        map.settings.javaScriptEnabled = true
        map.settings.domStorageEnabled = true
        map.settings.loadsImagesAutomatically = true
        map.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        map.webViewClient = WebViewClient()
        map.isVerticalScrollBarEnabled = false
        map.isHorizontalScrollBarEnabled = false
        map.setBackgroundColor(Color.rgb(232, 239, 248))
        addView(map, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(235)))
        showPlaceholder()
    }

    fun showLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
        updatedAt: Timestamp?,
        sharingEnabled: Boolean,
        fresh: Boolean
    ) {
        val timeLabel = updatedAt?.let {
            android.text.format.DateFormat.getTimeFormat(context).format(it.toDate())
        } ?: "waktu belum tersedia"
        val accuracyLabel = accuracyMeters?.takeIf { it >= 0.0 }?.let {
            " • akurasi ±${it.toInt()} m"
        } ?: ""
        status.text = when {
            !sharingEnabled -> "Berbagi lokasi dihentikan • lokasi terakhir: $timeLabel$accuracyLabel"
            fresh -> "Lokasi terbaru • $timeLabel$accuracyLabel"
            else -> "Lokasi mungkin sudah lama • terakhir: $timeLabel$accuracyLabel"
        }
        status.setTextColor(
            if (sharingEnabled && fresh) Color.rgb(28, 130, 83) else Color.rgb(104, 117, 140)
        )

        val delta = 0.004
        val url = String.format(
            Locale.US,
            "https://www.openstreetmap.org/export/embed.html?bbox=%.6f%%2C%.6f%%2C%.6f%%2C%.6f&layer=mapnik&marker=%.6f%%2C%.6f",
            longitude - delta, latitude - delta, longitude + delta, latitude + delta,
            latitude, longitude
        )
        if (url != lastMapUrl) {
            lastMapUrl = url
            map.loadUrl(url)
        }
    }

    fun showUnavailable(sharingEnabled: Boolean) {
        status.text = if (sharingEnabled) {
            "Berbagi aktif, tetapi lokasi pertama belum diterima. Periksa izin dan koneksi HP anak."
        } else {
            "Belum ada lokasi. Aktifkan berbagi lokasi secara sadar dari HP anak untuk menampilkan peta."
        }
        status.setTextColor(Color.rgb(104, 117, 140))
        lastMapUrl = null
        showPlaceholder()
    }

    private fun showPlaceholder() {
        map.loadDataWithBaseURL(
            null,
            "<html><body style='margin:0;background:#e8eff8;color:#53647e;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'><div>📍<br>Lokasi anak akan tampil di sini</div></body></html>",
            "text/html",
            "UTF-8",
            null
        )
    }

    fun release() {
        map.stopLoading()
        map.loadUrl("about:blank")
        map.destroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
