package com.santethp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.JavascriptInterface
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
    var onMapPointSelected: ((Double, Double) -> Unit)? = null

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(12))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.WHITE, Color.rgb(242, 247, 255))
        ).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.rgb(211, 225, 246))
        }
        elevation = dp(3).toFloat()

        addView(TextView(context).apply {
            text = "LOKASI PERANGKAT"
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(48, 78, 133))
            includeFontPadding = false
            setPadding(dp(10), dp(7), dp(10), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.rgb(235, 242, 255))
                cornerRadius = dp(10).toFloat()
            }
        })
        status.text = "Menunggu lokasi dari HP anak. Berbagi lokasi harus diaktifkan dari perangkat anak."
        status.textSize = 11.5f
        status.setLineSpacing(dp(2).toFloat(), 1f)
        status.setTextColor(Color.rgb(78, 96, 126))
        status.includeFontPadding = false
        status.setPadding(dp(10), dp(9), dp(10), dp(9))
        status.background = GradientDrawable().apply {
            setColor(Color.rgb(246, 249, 255))
            cornerRadius = dp(11).toFloat()
            setStroke(dp(1), Color.rgb(222, 231, 246))
        }
        addView(status)

        map.settings.javaScriptEnabled = true
        map.settings.domStorageEnabled = true
        map.settings.loadsImagesAutomatically = true
        map.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        map.addJavascriptInterface(object {
            @JavascriptInterface
            fun onMapTap(latitude: Double, longitude: Double) {
                post { onMapPointSelected?.invoke(latitude, longitude) }
            }
        }, "SantetMap")
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
        map.background = GradientDrawable().apply {
            setColor(Color.rgb(226, 235, 248))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(205, 220, 239))
        }
        map.clipToOutline = true
        map.isFocusable = true
        addView(map, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(390)).apply {
            topMargin = dp(8)
        })
        loadMapPage()
    }

    fun showLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
        updatedAt: Timestamp?,
        sharingEnabled: Boolean,
        fresh: Boolean,
        safeZonesJson: String = "[]"
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
            when {
                sharingEnabled && fresh -> Color.rgb(28, 112, 73)
                sharingEnabled -> Color.rgb(139, 99, 38)
                else -> Color.rgb(104, 117, 140)
            }
        )
        status.background = GradientDrawable().apply {
            val fill = when {
                sharingEnabled && fresh -> Color.rgb(239, 249, 242)
                sharingEnabled -> Color.rgb(255, 249, 237)
                else -> Color.rgb(246, 249, 255)
            }
            val border = when {
                sharingEnabled && fresh -> Color.rgb(202, 230, 210)
                sharingEnabled -> Color.rgb(239, 224, 194)
                else -> Color.rgb(222, 231, 246)
            }
            setColor(fill)
            cornerRadius = dp(11).toFloat()
            setStroke(dp(1), border)
        }
        runMapScript(
            "updateMap($latitude,$longitude,${accuracyMeters ?: 0.0},$safeZonesJson);"
        )
    }

    fun setMapTapMode(enabled: Boolean) {
        runMapScript("setMapTapMode(" + enabled + ");")
    }

    fun focusLocation(latitude: Double, longitude: Double) {
        runMapScript("focusLocation(" + latitude + "," + longitude + ");")
    }

    fun showUnavailable(sharingEnabled: Boolean) {
        status.text = if (sharingEnabled) {
            "Berbagi aktif, tetapi lokasi pertama belum diterima. Periksa izin dan koneksi HP anak."
        } else {
            "Belum ada lokasi. Aktifkan berbagi lokasi secara sadar dari HP anak untuk menampilkan peta."
        }
        status.setTextColor(if (sharingEnabled) Color.rgb(139, 99, 38) else Color.rgb(104, 117, 140))
        status.background = GradientDrawable().apply {
            setColor(if (sharingEnabled) Color.rgb(255, 249, 237) else Color.rgb(246, 249, 255))
            cornerRadius = dp(11).toFloat()
            setStroke(dp(1), if (sharingEnabled) Color.rgb(239, 224, 194) else Color.rgb(222, 231, 246))
        }
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
              .leaflet-control-zoom a{color:#245b43;border-color:#d7e6dc}
              .leaflet-control-zoom a:hover{background:#eef7f1;color:#17452f}
              .leaflet-popup-content-wrapper{border-radius:12px;box-shadow:0 3px 14px #17345b22}
              .leaflet-popup-content{font-size:12px;line-height:1.45;color:#294536}
              .leaflet-popup-tip{box-shadow:0 2px 8px #17345b18}
              #searchPanel{display:none;position:absolute;z-index:1000;top:10px;left:10px;right:10px;background:#fff;padding:10px;border:1px solid #dce6f5;border-radius:14px;box-shadow:0 4px 16px #17345b33}
              #placeSearch{width:100%;box-sizing:border-box;border:1px solid #d5deeb;border-radius:10px;padding:12px;font-size:14px;outline:none;color:#243b60}#placeSearch:focus{border-color:#3d936b;box-shadow:0 0 0 2px #3d936b22}
              #searchResults{max-height:150px;overflow:auto}
              .searchResult{padding:10px 8px;border-bottom:1px solid #edf1f6;border-radius:7px;font-size:12px;color:#294536;line-height:1.4}
              .searchResult:active{background:#eef7f1;color:#205c40}
              #selectionPanel{display:none;position:absolute;z-index:1000;bottom:18px;left:12px;right:12px;text-align:center}
              #confirmPoint{border:0;border-radius:22px;background:#207754;color:white;padding:13px 24px;font-weight:bold;letter-spacing:.2px;box-shadow:0 3px 12px #174b3540}
              #confirmPoint:active{background:#185f41}
              #searchHint{font-size:10px;color:#68758c;padding:5px 2px}
            </style></head><body>
            <div id="searchPanel">
              <input id="placeSearch" type="search" placeholder="Cari alamat, sekolah, toko, atau tempat..." autocomplete="off">
              <div id="searchHint">Ketik nama tempat, lalu pilih hasil pencarian</div>
              <div id="searchResults"></div>
            </div>
            <div id="selectionPanel"><button id="confirmPoint" onclick="confirmSelectedPoint()">Gunakan titik ini</button></div>
            <div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
              const map=L.map('map',{zoomControl:true}).setView([-6.9,110.4],13);
              L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
                maxZoom:19,attribution:'&copy; OpenStreetMap'
              }).addTo(map);
              let marker=null,accuracyCircle=null,selectionMarker=null;
              let fences=[];
              let mapTapMode=false;
              let searchTimer=null;
              const searchInput=document.getElementById('placeSearch');
              const searchResults=document.getElementById('searchResults');
              function placeSelection(lat,lon){
                if(selectionMarker)map.removeLayer(selectionMarker);
                selectionMarker=L.marker([lat,lon],{draggable:true}).addTo(map);
                selectionMarker.on('dragend',function(){
                  const p=selectionMarker.getLatLng();
                  document.getElementById('selectionPanel').style.display='block';
                });
                document.getElementById('selectionPanel').style.display='block';
              }
              function confirmSelectedPoint(){
                if(!selectionMarker)return;
                const p=selectionMarker.getLatLng();
                setMapTapMode(false);
                if(window.SantetMap&&window.SantetMap.onMapTap)window.SantetMap.onMapTap(p.lat,p.lng);
              }
              searchInput.addEventListener('input',function(){
                clearTimeout(searchTimer);
                const q=searchInput.value.trim();
                if(q.length<3){searchResults.innerHTML='';return;}
                searchResults.innerHTML='<div class="searchResult">Mencari lokasi...</div>';
                searchTimer=setTimeout(async function(){
                  try{
                    const url='https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=6&countrycodes=id&q='+encodeURIComponent(q);
                    const response=await fetch(url,{headers:{'Accept-Language':'id'}});
                    if(!response.ok)throw new Error('search');
                    const places=await response.json();
                    searchResults.innerHTML='';
                    if(!places.length){searchResults.innerHTML='<div class="searchResult">Tempat tidak ditemukan. Coba nama jalan atau kecamatan.</div>';return;}
                    places.forEach(function(place){
                      const item=document.createElement('div');
                      item.className='searchResult';
                      item.textContent=place.display_name;
                      item.onclick=function(){
                        const lat=Number(place.lat),lon=Number(place.lon);
                        map.setView([lat,lon],17,{animate:true});
                        placeSelection(lat,lon);
                        searchResults.innerHTML='';
                        document.getElementById('searchHint').textContent='Geser pin bila perlu, lalu tekan Gunakan titik ini';
                        setTimeout(()=>map.invalidateSize(),150);
                      };
                      searchResults.appendChild(item);
                    });
                  }catch(e){
                    searchResults.innerHTML='<div class="searchResult">Pencarian gagal. Periksa koneksi internet lalu coba lagi.</div>';
                  }
                },700);
              });
              function clearFences(){fences.forEach(item=>map.removeLayer(item));fences=[];}
              function showWaiting(){
                if(marker){map.removeLayer(marker);marker=null;}
                if(accuracyCircle){map.removeLayer(accuracyCircle);accuracyCircle=null;}
                clearFences();
              }
              function setMapTapMode(enabled){
                mapTapMode=!!enabled;
                map.getContainer().style.cursor=mapTapMode?'crosshair':'';
                document.getElementById('searchPanel').style.display=mapTapMode?'block':'none';
                if(!mapTapMode){
                  document.getElementById('selectionPanel').style.display='none';
                  searchResults.innerHTML='';
                }
              }
              function focusLocation(lat,lon){map.setView([lat,lon],17,{animate:true});}
              map.on('click',function(event){
                if(!mapTapMode)return;
                placeSelection(event.latlng.lat,event.latlng.lng);
              });
              function updateMap(lat,lon,acc,zones){
                const p=[lat,lon];
                if(!marker){marker=L.marker(p,{icon:L.divIcon({className:'',html:'<div class="child-pin"></div>',iconSize:[26,26],iconAnchor:[13,13]})}).addTo(map);}
                else marker.setLatLng(p);
                if(accuracyCircle){map.removeLayer(accuracyCircle);accuracyCircle=null;}
                if(acc>0){accuracyCircle=L.circle(p,{radius:Math.min(acc,1000),color:'#2878ed',weight:1,fillOpacity:.08}).addTo(map);}
                clearFences();
                (Array.isArray(zones)?zones:[]).forEach(function(zone){
                  const circle=L.circle([zone.latitude,zone.longitude],{
                    radius:zone.radiusM,color:'#16a36a',weight:2,fillColor:'#16a36a',fillOpacity:.12
                  }).addTo(map);
                  const label=String(zone.name||'Area aman').replace(/[&<>"']/g,'');
                  circle.bindPopup('<b>'+label+'</b><br>Radius '+zone.radiusM+' m');
                  fences.push(circle);
                });
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
