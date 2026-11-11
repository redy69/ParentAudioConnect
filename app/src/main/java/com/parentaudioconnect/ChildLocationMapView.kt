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

/** Parent-side location screen. Map tiles are provided by OpenStreetMap. */
class ChildLocationMapView(context: Context) : LinearLayout(context) {
    private val status = TextView(context)
    private val map = WebView(context)
    private var pageLoaded = false
    private var pendingScript: String? = null

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(12))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.WHITE, Color.rgb(244, 248, 255))
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.rgb(218, 228, 245))
        }
        elevation = dp(3).toFloat()

        addView(TextView(context).apply {
            text = "LOKASI PERANGKAT"
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(75, 98, 139))
            setPadding(0, 0, 0, dp(8))
        })
        status.text = "Menunggu lokasi dari HP anak. Berbagi lokasi harus diaktifkan dari perangkat anak."
        status.textSize = 12f
        status.setLineSpacing(dp(2).toFloat(), 1f)
        status.setTextColor(Color.rgb(104, 117, 140))
        status.setPadding(0, 0, 0, dp(12))
        addView(status)

        map.settings.javaScriptEnabled = true
        map.settings.domStorageEnabled = true
        map.settings.loadsImagesAutomatically = true
        map.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        map.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded = true
                pendingScript?.let {
                    map.evaluateJavascript(it, null)
                    pendingScript = null
                }
            }
        }
        map.isVerticalScrollBarEnabled = false
        map.isHorizontalScrollBarEnabled = false
        map.setBackgroundColor(Color.rgb(232, 239, 248))
        addView(map, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(390)))
        loadMapPage()
    }

    fun showLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
        updatedAt: Timestamp?,
        sharingEnabled: Boolean,
        fresh: Boolean,
        geofenceEnabled: Boolean = false,
        geofenceLatitude: Double? = null,
        geofenceLongitude: Double? = null,
        geofenceRadiusMeters: Int = 0
    ) {
        val timeLabel = updatedAt?.let {
            android.text.format.DateFormat.getTimeFormat(context).format(it.toDate())
        } ?: "waktu belum tersedia"
        val accuracyLabel = accuracyMeters?.takeIf { it >= 0.0 }?.let {
            " • akurasi ±${it.toInt()} m"
        } ?: ""
        status.text = when {
            !sharingEnabled -> "Berbagi lokasi dihentikan • lokasi terakhir: $timeLabel$accuracyLabel"
            fresh -> "● LOKASI TERBARU  •  $timeLabel$accuracyLabel"
            else -> "Lokasi mungkin sudah lama • terakhir: $timeLabel$accuracyLabel"
        }
        status.setTextColor(
            if (sharingEnabled && fresh) Color.rgb(28, 130, 83) else Color.rgb(104, 117, 140)
        )
        val fence = if (geofenceEnabled && geofenceLatitude != null && geofenceLongitude != null && geofenceRadiusMeters > 0) {
            "true,${geofenceLatitude},${geofenceLongitude},$geofenceRadiusMeters"
        } else "false,0,0,0"
        runMapScript(
            "updateMap($latitude,$longitude,${accuracyMeters ?: 0.0},$fence);"
        )
    }

    fun showUnavailable(sharingEnabled: Boolean) {
        status.text = if (sharingEnabled) {
            "Berbagi aktif, tetapi lokasi pertama belum diterima. Periksa izin dan koneksi HP anak."
        } else {
            "Belum ada lokasi. Aktifkan berbagi lokasi secara sadar dari HP anak untuk menampilkan peta."
        }
        status.setTextColor(Color.rgb(104, 117, 140))
        runMapScript("showWaiting();")
    }

    private fun loadMapPage() {
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
            <style>
              html,body,#map{height:100%;width:100%;margin:0;background:#e8eff8}
              .leaflet-container{font-family:Arial,sans-serif}
              .child-pin{width:18px;height:18px;border-radius:50%;background:#2878ed;border:4px solid white;box-shadow:0 2px 12px #17345b88}
              .leaflet-control-attribution{font-size:9px}
            </style></head><body><div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
              const map=L.map('map',{zoomControl:true}).setView([-6.9,110.4],13);
              L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
                maxZoom:19,attribution:'&copy; OpenStreetMap'
              }).addTo(map);
              let marker=null,accuracy=null,fence=null;
              function showWaiting(){ if(marker){map.removeLayer(marker);marker=null;} if(accuracy){map.removeLayer(accuracy);accuracy=null;} if(fence){map.removeLayer(fence);fence=null;} }
              function updateMap(lat,lon,acc,fenceOn,fLat,fLon,radius){
                const p=[lat,lon];
                if(!marker){marker=L.marker(p,{icon:L.divIcon({className:'',html:'<div class="child-pin"></div>',iconSize:[26,26],iconAnchor:[13,13]})}).addTo(map);}
                else marker.setLatLng(p);
                if(accuracy>0 && window._accuracyCircle){map.removeLayer(window._accuracyCircle);}
                if(acc>0){window._accuracyCircle=L.circle(p,{radius:Math.min(acc,1000),color:'#2878ed',weight:1,fillOpacity:.08}).addTo(map);}
                if(fence){map.removeLayer(fence);fence=null;}
                if(fenceOn){fence=L.circle([fLat,fLon],{radius:radius,color:'#16a36a',weight:2,fillColor:'#16a36a',fillOpacity:.12}).addTo(map);}
                map.setView(p,Math.max(map.getZoom(),15),{animate:true});
                setTimeout(()=>map.invalidateSize(),150);
              }
            </script></body></html>
        """.trimIndent()
        map.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
    }

    private fun runMapScript(script: String) {
        if (pageLoaded) map.evaluateJavascript(script, null) else pendingScript = script
    }

    fun release() {
        map.stopLoading()
        map.loadUrl("about:blank")
        map.destroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
