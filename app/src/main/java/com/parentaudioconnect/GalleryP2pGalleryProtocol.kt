package com.santethp

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** Consent-gated gallery metadata and on-demand thumbnail protocol over WebRTC DataChannel. */
class GalleryP2pGalleryProtocol(
    context: Context,
    private val role: GalleryP2pManager.Role,
    private val sendFrame: (ByteArray, Boolean) -> Boolean,
    private val onMediaIndex: (List<RemoteMedia>) -> Unit,
    private val onThumbnail: (String, ByteArray) -> Unit,
    private val onStatus: (String) -> Unit
) {
    data class RemoteMedia(val key: String, val name: String, val mime: String, val sizeBytes: Long, val modifiedSeconds: Long, val kind: String)
    companion object { private const val TAG = "SantetHP-GalleryProto"; private const val MAX_FRAME_BYTES = 96 * 1024; private const val MAX_INDEX_ITEMS = 300; private const val MAX_THUMB_BYTES = 64 * 1024 }
    private val appContext = context.applicationContext
    private val repository = GalleryMediaRepository(appContext)
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    @Volatile private var childItems = emptyMap<String, GalleryMediaRepository.MediaItem>()

    fun requestIndex(): Boolean = role == GalleryP2pManager.Role.PARENT && sendJson(JSONObject().put("type", "index_request"))
    fun requestThumbnail(key: String): Boolean = role == GalleryP2pManager.Role.PARENT && key.isNotBlank() && sendJson(JSONObject().put("type", "thumbnail_request").put("key", key))

    fun onFrame(bytes: ByteArray, binary: Boolean) {
        if (closed || binary || bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) return
        val message = try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (error: Exception) { Log.w(TAG, "Frame galeri bukan JSON valid", error); return }
        when (message.optString("type")) {
            "index_request" -> if (role == GalleryP2pManager.Role.CHILD) sendIndex()
            "index_response" -> if (role == GalleryP2pManager.Role.PARENT) receiveIndex(message)
            "thumbnail_request" -> if (role == GalleryP2pManager.Role.CHILD) sendThumbnail(message.optString("key"))
            "thumbnail_response" -> if (role == GalleryP2pManager.Role.PARENT) receiveThumbnail(message)
        }
    }

    private fun sharingAllowed(): Boolean {
        if (!appContext.getSharedPreferences("parent_audio", Context.MODE_PRIVATE).getBoolean("child_gallery_sharing_enabled", false)) return false
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 34) listOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if (android.os.Build.VERSION.SDK_INT >= 33) listOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO) else listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        return permissions.any { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun sendIndex() { worker.execute {
        if (!sharingAllowed()) { onStatus("Berbagi galeri nonaktif atau izin media tidak tersedia di HP anak"); return@execute }
        try {
            val items = repository.listMedia(MAX_INDEX_ITEMS)
            childItems = items.associateBy { it.stableKey }
            val array = JSONArray()
            items.forEach { item -> array.put(JSONObject().put("key", item.stableKey).put("name", item.displayName.take(120)).put("mime", item.mimeType.take(100)).put("size", item.sizeBytes).put("modified", item.modifiedEpochSeconds).put("kind", item.kind.name)) }
            val sent = sendJson(JSONObject().put("type", "index_response").put("items", array))
            if (!sent) onStatus("Daftar galeri terlalu besar atau koneksi P2P sedang sibuk") else onStatus("Mengirim daftar " + items.size + " media • tanpa file asli")
        } catch (error: Exception) { Log.e(TAG, "Gagal membaca indeks media", error); onStatus("Tidak dapat membaca daftar media: " + (error.localizedMessage ?: "kesalahan")) }
    } }

    private fun receiveIndex(message: JSONObject) {
        val array = message.optJSONArray("items") ?: JSONArray()
        val result = ArrayList<RemoteMedia>(minOf(array.length(), MAX_INDEX_ITEMS))
        for (i in 0 until minOf(array.length(), MAX_INDEX_ITEMS)) {
            val item = array.optJSONObject(i) ?: continue
            val key = item.optString("key"); if (key.isBlank() || key.length > 160) continue
            result += RemoteMedia(key, item.optString("name").take(120), item.optString("mime").take(100), item.optLong("size").coerceAtLeast(0), item.optLong("modified").coerceAtLeast(0), item.optString("kind").take(12))
        }
        onMediaIndex(result); onStatus("Daftar galeri diterima • " + result.size + " item")
    }

    private fun sendThumbnail(key: String) {
        if (key.isBlank() || key.length > 160) return
        worker.execute {
            if (!sharingAllowed()) { onStatus("Permintaan thumbnail ditolak: berbagi galeri tidak aktif"); return@execute }
            val item = childItems[key] ?: run { onStatus("Media sudah berubah; minta daftar galeri terbaru"); return@execute }
            try {
                val preview = repository.thumbnail(item, maxEdge = 240, quality = 60)
                if (preview.size > MAX_THUMB_BYTES) { onStatus("Thumbnail terlalu besar untuk koneksi ini"); return@execute }
                val message = JSONObject().put("type", "thumbnail_response").put("key", key).put("mime", "image/jpeg").put("data", Base64.encodeToString(preview, Base64.NO_WRAP))
                if (!sendJson(message)) onStatus("Thumbnail gagal dikirim; coba lagi")
            } catch (error: Exception) { Log.e(TAG, "Thumbnail gagal dibuat", error); onStatus("Thumbnail tidak tersedia: " + (error.localizedMessage ?: "kesalahan")) }
        }
    }

    private fun receiveThumbnail(message: JSONObject) {
        val key = message.optString("key"); val encoded = message.optString("data")
        if (key.isBlank() || encoded.isBlank() || encoded.length > MAX_THUMB_BYTES * 2) return
        try { val decoded = Base64.decode(encoded, Base64.DEFAULT); if (decoded.isNotEmpty() && decoded.size <= MAX_THUMB_BYTES) onThumbnail(key, decoded) } catch (error: Exception) { Log.w(TAG, "Thumbnail diterima tidak valid", error) }
    }

    private fun sendJson(message: JSONObject): Boolean { if (closed) return false; val bytes = message.toString().toByteArray(Charsets.UTF_8); return bytes.isNotEmpty() && bytes.size <= MAX_FRAME_BYTES && sendFrame(bytes, false) }
    fun close() { closed = true; worker.shutdownNow(); childItems = emptyMap() }
}