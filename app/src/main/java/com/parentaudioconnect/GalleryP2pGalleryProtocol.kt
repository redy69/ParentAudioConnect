package com.santethp

import android.content.Context
import android.database.ContentObserver
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Consent-gated gallery metadata and on-demand thumbnail protocol over WebRTC DataChannel. */
class GalleryP2pGalleryProtocol(
    context: Context,
    private val role: GalleryP2pManager.Role,
    private val sendFrame: (ByteArray, Boolean) -> Boolean,
    private val onMediaIndex: (List<RemoteMedia>) -> Unit,
    private val onThumbnail: (String, ByteArray) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onOriginalReady: (String, File, String, String) -> Unit = { _, _, _, _ -> }
) {
    data class RemoteMedia(
        val key: String,
        val name: String,
        val mime: String,
        val sizeBytes: Long,
        val modifiedSeconds: Long,
        val kind: String
    )

    companion object {
        private const val TAG = "SantetHP-GalleryProto"
        private const val MAX_FRAME_BYTES = 96 * 1024
        private const val MAX_INDEX_ITEMS = 1000
        private const val INDEX_PAGE_SIZE = 40
        private const val MAX_THUMB_BYTES = 64 * 1024
        private const val MAX_ORIGINAL_BYTES = 200L * 1024L * 1024L
        private const val ORIGINAL_CHUNK_BYTES = 16 * 1024
    }

    private val appContext = context.applicationContext
    private val repository = GalleryMediaRepository(appContext)
    private val backupTracker = GalleryBackupTracker(appContext)
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    @Volatile private var childItems = emptyMap<String, GalleryMediaRepository.MediaItem>()
    private val receivedIndex = linkedMapOf<String, RemoteMedia>()
    private val requestedTransfers = ConcurrentHashMap<String, String>()
    private val cancelledTransfers = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var incomingTransfer: IncomingTransfer? = null
    private val observerHandler = Handler(Looper.getMainLooper())
    private val mediaRefreshRunnable = Runnable {
        if (!closed && role == GalleryP2pManager.Role.CHILD && sharingAllowed()) {
            refreshChildMediaIndex()
            sendIndex(0)
        }
    }
    private var mediaObserver: ContentObserver? = null

    init {
        if (role == GalleryP2pManager.Role.CHILD) {
            val observer = object : ContentObserver(observerHandler) {
                override fun onChange(selfChange: Boolean) = scheduleMediaRefresh()
                override fun onChange(selfChange: Boolean, uri: android.net.Uri?) = scheduleMediaRefresh()
            }
            try {
                appContext.contentResolver.registerContentObserver(
                    MediaStore.Files.getContentUri("external"), true, observer
                )
                mediaObserver = observer
            } catch (error: Exception) {
                Log.w(TAG, "Tidak dapat memasang pemantau perubahan galeri", error)
            }
        }
    }

    private fun scheduleMediaRefresh() {
        if (closed || !sharingAllowed()) return
        observerHandler.removeCallbacks(mediaRefreshRunnable)
        observerHandler.postDelayed(mediaRefreshRunnable, 2000)
    }

    /**
     * Refreshes the child media cache and discovers only items created after
     * the user-enabled backup baseline. Existing gallery browsing is unchanged.
     */
    private fun refreshChildMediaIndex() {
        if (role != GalleryP2pManager.Role.CHILD || !sharingAllowed()) return
        worker.execute {
            try {
                // Scan the complete MediaStore collection for backup discovery.
                // Keep the existing gallery browser cache capped so its behavior
                // and memory use remain unchanged.
                val allItems = repository.listMedia(limit = null)
                backupTracker.discoverNewMedia(allItems)
                childItems = repository.listMedia(MAX_INDEX_ITEMS).associateBy { it.stableKey }
            } catch (error: Exception) {
                Log.w(TAG, "Pemindaian media baru untuk antrean backup gagal", error)
            }
        }
    }

    private data class IncomingTransfer(
        val transferId: String, val key: String, val name: String, val mime: String,
        val expectedBytes: Long, val file: File, val output: FileOutputStream,
        val digest: MessageDigest, var nextSequence: Int = 0, var receivedBytes: Long = 0
    )

    fun requestIndex(): Boolean {
        if (role != GalleryP2pManager.Role.PARENT) return false
        synchronized(receivedIndex) { receivedIndex.clear() }
        onStatus("Meminta daftar foto dan video…")
        return sendJson(JSONObject().put("type", "index_request").put("offset", 0))
    }

    fun requestThumbnail(key: String): Boolean =
        role == GalleryP2pManager.Role.PARENT &&
            key.isNotBlank() && key.length <= 160 &&
            sendJson(JSONObject().put("type", "thumbnail_request").put("key", key))


    fun requestOriginal(key: String): Boolean {
        if (role != GalleryP2pManager.Role.PARENT || key.isBlank() || key.length > 160) return false
        if (incomingTransfer != null || requestedTransfers.isNotEmpty()) {
            onStatus("Selesaikan unduhan yang sedang berjalan terlebih dahulu")
            return false
        }
        val id = UUID.randomUUID().toString()
        requestedTransfers[id] = key
        val sent = sendJson(JSONObject().put("type", "original_request").put("key", key).put("transferId", id))
        if (!sent) requestedTransfers.remove(id) else onStatus("Meminta file asli dari HP anak…")
        return sent
    }

    fun cancelOriginal() {
        if (role != GalleryP2pManager.Role.PARENT) return
        requestedTransfers.keys.toList().forEach { id ->
            sendJson(JSONObject().put("type", "original_cancel").put("transferId", id))
            requestedTransfers.remove(id)
        }
        incomingTransfer?.let { closeIncoming(it, true) }
        incomingTransfer = null
    }

    fun onFrame(bytes: ByteArray, binary: Boolean) {
        if (closed || binary || bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) return
        val message = try {
            JSONObject(bytes.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "Frame galeri bukan JSON valid", error)
            return
        }
        when (message.optString("type")) {
            "index_request" -> if (role == GalleryP2pManager.Role.CHILD) {
                sendIndex(message.optInt("offset", 0).coerceIn(0, MAX_INDEX_ITEMS))
            }
            "index_response" -> if (role == GalleryP2pManager.Role.PARENT) receiveIndex(message)
            "index_error" -> if (role == GalleryP2pManager.Role.PARENT) {
                onStatus(message.optString("message", "Daftar galeri tidak dapat dibaca di HP anak"))
            }
            "thumbnail_request" -> if (role == GalleryP2pManager.Role.CHILD) {
                sendThumbnail(message.optString("key"))
            }
            "thumbnail_response" -> if (role == GalleryP2pManager.Role.PARENT) receiveThumbnail(message)
            "original_request" -> if (role == GalleryP2pManager.Role.CHILD) {
                sendOriginal(message.optString("key"), message.optString("transferId"))
            }
            "original_cancel" -> if (role == GalleryP2pManager.Role.CHILD) {
                cancelledTransfers.add(message.optString("transferId"))
            }
            "original_start" -> if (role == GalleryP2pManager.Role.PARENT) receiveOriginalStart(message)
            "original_chunk" -> if (role == GalleryP2pManager.Role.PARENT) receiveOriginalChunk(message)
            "original_end" -> if (role == GalleryP2pManager.Role.PARENT) receiveOriginalEnd(message)
            "original_error" -> if (role == GalleryP2pManager.Role.PARENT) {
                val id = message.optString("transferId")
                requestedTransfers.remove(id)
                incomingTransfer?.takeIf { it.transferId == id }?.let {
                    closeIncoming(it, true)
                    incomingTransfer = null
                }
                onStatus(message.optString("message", "File asli tidak dapat dikirim"))
            }
        }
    }

    private fun sharingAllowed(): Boolean {
        if (!appContext.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
                .getBoolean("child_gallery_sharing_enabled", false)
        ) return false

        val permissions = if (android.os.Build.VERSION.SDK_INT >= 34) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (android.os.Build.VERSION.SDK_INT >= 33) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.any {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun sendIndex(offset: Int) {
        worker.execute {
            if (!sharingAllowed()) {
                sendJson(JSONObject().put("type", "index_response").put("offset", offset)
                    .put("nextOffset", offset).put("hasMore", false).put("total", 0)
                    .put("items", JSONArray()))
                onStatus("Berbagi galeri nonaktif atau izin media tidak tersedia di HP anak")
                return@execute
            }
            try {
                if (offset == 0 || childItems.isEmpty()) {
                    childItems = repository.listMedia(MAX_INDEX_ITEMS).associateBy { it.stableKey }
                }
                val allItems = childItems.values.toList()
                val safeOffset = offset.coerceIn(0, allItems.size)
                val page = allItems.drop(safeOffset).take(INDEX_PAGE_SIZE)
                val array = JSONArray()
                page.forEach { item ->
                    array.put(
                        JSONObject()
                            .put("key", item.stableKey)
                            .put("name", item.displayName.take(120))
                            .put("mime", item.mimeType.take(100))
                            .put("size", item.sizeBytes)
                            .put("modified", item.modifiedEpochSeconds)
                            .put("kind", item.kind.name)
                    )
                }
                val nextOffset = safeOffset + page.size
                val hasMore = nextOffset < allItems.size
                val sent = sendJson(
                    JSONObject()
                        .put("type", "index_response")
                        .put("offset", safeOffset)
                        .put("nextOffset", nextOffset)
                        .put("hasMore", hasMore)
                        .put("total", allItems.size)
                        .put("truncated", allItems.size >= MAX_INDEX_ITEMS)
                        .put("items", array)
                )
                if (!sent) {
                    onStatus("Halaman daftar galeri gagal dikirim; periksa koneksi P2P")
                } else {
                    onStatus("Mengirim daftar galeri ${minOf(nextOffset, allItems.size)}/${allItems.size} • tanpa file asli")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Gagal membaca indeks media", error)
                sendJson(JSONObject().put("type", "index_error")
                    .put("message", "Tidak dapat membaca daftar media. Periksa izin foto dan video di HP anak."))
                onStatus("Tidak dapat membaca daftar media: " + (error.localizedMessage ?: "kesalahan"))
            }
        }
    }

    private fun receiveIndex(message: JSONObject) {
        val offset = message.optInt("offset", -1)
        if (offset < 0 || offset > MAX_INDEX_ITEMS) return
        val array = message.optJSONArray("items") ?: JSONArray()
        if (array.length() > INDEX_PAGE_SIZE) return

        val page = ArrayList<RemoteMedia>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val key = item.optString("key")
            if (key.isBlank() || key.length > 160) continue
            val kind = item.optString("kind")
            if (kind != "PHOTO" && kind != "VIDEO") continue
            page += RemoteMedia(
                key = key,
                name = item.optString("name").take(120),
                mime = item.optString("mime").take(100),
                sizeBytes = item.optLong("size").coerceAtLeast(0),
                modifiedSeconds = item.optLong("modified").coerceAtLeast(0),
                kind = kind
            )
        }

        synchronized(receivedIndex) {
            if (offset == 0) receivedIndex.clear()
            page.forEach { receivedIndex[it.key] = it }
            val hasMore = message.optBoolean("hasMore", false)
            val nextOffset = message.optInt("nextOffset", offset + page.size)
            if (hasMore && nextOffset > offset && nextOffset <= MAX_INDEX_ITEMS) {
                if (!sendJson(JSONObject().put("type", "index_request").put("offset", nextOffset))) {
                    onStatus("Daftar diterima sebagian; ketuk muat ulang setelah koneksi stabil")
                }
                return
            }
            onMediaIndex(receivedIndex.values.toList())
            onStatus(
                if (message.optBoolean("truncated", false)) {
                    "Daftar menampilkan 1.000 media terbaru. Jika media lebih banyak, sebagian belum dimuat."
                } else {
                    "Daftar galeri diterima • ${receivedIndex.size} item"
                }
            )
        }
    }

    private fun sendThumbnail(key: String) {
        if (key.isBlank() || key.length > 160) return
        worker.execute {
            if (!sharingAllowed()) {
                sendJson(JSONObject().put("type", "thumbnail_response").put("key", key).put("error", true))
                onStatus("Permintaan thumbnail ditolak: berbagi galeri tidak aktif")
                return@execute
            }
            val item = childItems[key] ?: run {
                sendJson(JSONObject().put("type", "thumbnail_response").put("key", key).put("error", true))
                onStatus("Media sudah berubah; minta daftar galeri terbaru")
                return@execute
            }
            try {
                val preview = repository.thumbnail(item, maxEdge = 240, quality = 60)
                if (preview.size > MAX_THUMB_BYTES) {
                    sendJson(JSONObject().put("type", "thumbnail_response").put("key", key).put("error", true))
                    onStatus("Thumbnail terlalu besar untuk koneksi ini")
                    return@execute
                }
                val message = JSONObject()
                    .put("type", "thumbnail_response")
                    .put("key", key)
                    .put("mime", "image/jpeg")
                    .put("data", Base64.encodeToString(preview, Base64.NO_WRAP))
                if (!sendJson(message)) onStatus("Thumbnail gagal dikirim; coba lagi")
            } catch (error: Exception) {
                Log.e(TAG, "Thumbnail gagal dibuat", error)
                sendJson(JSONObject().put("type", "thumbnail_response").put("key", key).put("error", true))
                onStatus("Thumbnail tidak tersedia: " + (error.localizedMessage ?: "kesalahan"))
            }
        }
    }


    private fun sendOriginal(key: String, transferId: String) {
        if (key.isBlank() || key.length > 160 || transferId.isBlank() || transferId.length > 64) return
        worker.execute {
            if (!sharingAllowed()) {
                sendJson(JSONObject().put("type", "original_error").put("transferId", transferId).put("message", "Persetujuan berbagi galeri sudah tidak aktif"))
                return@execute
            }
            val item = childItems[key]
            if (item == null) {
                sendJson(JSONObject().put("type", "original_error").put("transferId", transferId).put("message", "Media tidak ditemukan; muat ulang daftar galeri"))
                return@execute
            }
            if (item.sizeBytes <= 0 || item.sizeBytes > MAX_ORIGINAL_BYTES) {
                sendJson(JSONObject().put("type", "original_error").put("transferId", transferId).put("message", "Ukuran file melebihi batas transfer 200 MB"))
                return@execute
            }
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val start = JSONObject().put("type", "original_start").put("transferId", transferId)
                    .put("key", key).put("name", item.displayName.take(180))
                    .put("mime", item.mimeType.take(120)).put("size", item.sizeBytes)
                if (!sendJsonWithRetry(start, transferId)) return@execute
                var sequence = 0
                var sentBytes = 0L
                repository.openOriginal(item).use { input ->
                    val buffer = ByteArray(ORIGINAL_CHUNK_BYTES)
                    while (true) {
                        if (closed || cancelledTransfers.contains(transferId)) {
                            onStatus("Transfer file asli dibatalkan")
                            return@execute
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        sentBytes += count
                        if (sentBytes > item.sizeBytes || sentBytes > MAX_ORIGINAL_BYTES) throw IllegalStateException("Ukuran file berubah saat transfer")
                        digest.update(buffer, 0, count)
                        val chunk = JSONObject().put("type", "original_chunk").put("transferId", transferId)
                            .put("sequence", sequence++)
                            .put("data", Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP))
                        if (!sendJsonWithRetry(chunk, transferId)) return@execute
                        if (sentBytes % (1024L * 1024L) < count || sentBytes == item.sizeBytes) {
                            onStatus("Mengirim file asli: " + android.text.format.Formatter.formatFileSize(appContext, sentBytes) +
                                " / " + android.text.format.Formatter.formatFileSize(appContext, item.sizeBytes))
                        }
                    }
                }
                if (sentBytes != item.sizeBytes) throw IllegalStateException("Ukuran file tidak cocok")
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                sendJsonWithRetry(JSONObject().put("type", "original_end").put("transferId", transferId)
                    .put("totalBytes", sentBytes).put("sha256", hash), transferId)
            } catch (error: Exception) {
                Log.e(TAG, "Gagal mengirim file asli", error)
                sendJson(JSONObject().put("type", "original_error").put("transferId", transferId)
                    .put("message", "Transfer gagal: " + (error.localizedMessage ?: "kesalahan")))
            } finally {
                cancelledTransfers.remove(transferId)
            }
        }
    }

    private fun sendJsonWithRetry(message: JSONObject, transferId: String): Boolean {
        repeat(100) {
            if (closed || cancelledTransfers.contains(transferId)) return false
            if (sendJson(message)) return true
            try { Thread.sleep(100) } catch (_: InterruptedException) { return false }
        }
        onStatus("Koneksi P2P terlalu sibuk; transfer dihentikan")
        return false
    }

    private fun receiveOriginalStart(message: JSONObject) {
        val id = message.optString("transferId")
        val key = message.optString("key")
        val expected = message.optLong("size", -1)
        if (requestedTransfers[id] != key) return
        if (expected <= 0 || expected > MAX_ORIGINAL_BYTES) {
            requestedTransfers.remove(id)
            sendJson(JSONObject().put("type", "original_cancel").put("transferId", id))
            onStatus("Ukuran file tidak valid atau melebihi batas 200 MB")
            return
        }
        try {
            incomingTransfer?.let { closeIncoming(it, true) }
            val file = File.createTempFile("santethp-gallery-", ".part", appContext.cacheDir)
            incomingTransfer = IncomingTransfer(
                transferId = id, key = key, name = message.optString("name").take(180),
                mime = message.optString("mime").take(120).ifBlank { "application/octet-stream" },
                expectedBytes = expected, file = file, output = FileOutputStream(file),
                digest = MessageDigest.getInstance("SHA-256")
            )
            onStatus("Menerima file asli: 0 / " + android.text.format.Formatter.formatFileSize(appContext, expected))
        } catch (error: Exception) {
            requestedTransfers.remove(id)
            onStatus("Tidak dapat menyiapkan file sementara")
        }
    }

    private fun receiveOriginalChunk(message: JSONObject) {
        val transfer = incomingTransfer ?: return
        if (message.optString("transferId") != transfer.transferId) return
        val sequence = message.optInt("sequence", -1)
        val encoded = message.optString("data")
        if (sequence != transfer.nextSequence || encoded.isBlank() || encoded.length > ORIGINAL_CHUNK_BYTES * 2) {
            failIncoming(transfer, "Urutan potongan file tidak valid")
            return
        }
        try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            if (bytes.isEmpty() || bytes.size > ORIGINAL_CHUNK_BYTES || transfer.receivedBytes + bytes.size > transfer.expectedBytes) {
                failIncoming(transfer, "Potongan file melewati batas ukuran")
                return
            }
            transfer.output.write(bytes)
            transfer.digest.update(bytes)
            transfer.receivedBytes += bytes.size
            transfer.nextSequence++
            if (transfer.receivedBytes % (1024L * 1024L) < bytes.size ||
                transfer.receivedBytes == transfer.expectedBytes
            ) {
                onStatus("Menerima file asli: " + android.text.format.Formatter.formatFileSize(appContext, transfer.receivedBytes) +
                    " / " + android.text.format.Formatter.formatFileSize(appContext, transfer.expectedBytes))
            }
        } catch (error: Exception) {
            failIncoming(transfer, "Gagal menulis potongan file")
        }
    }

    private fun receiveOriginalEnd(message: JSONObject) {
        val transfer = incomingTransfer ?: return
        if (message.optString("transferId") != transfer.transferId) return
        try {
            transfer.output.flush()
            transfer.output.close()
            val hash = transfer.digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (transfer.receivedBytes != transfer.expectedBytes ||
                message.optLong("totalBytes", -1) != transfer.receivedBytes ||
                hash != message.optString("sha256")
            ) {
                failIncoming(transfer, "Verifikasi SHA-256 gagal; file sementara dihapus")
                return
            }
            incomingTransfer = null
            requestedTransfers.remove(transfer.transferId)
            onOriginalReady(transfer.key, transfer.file, transfer.name, transfer.mime)
            onStatus("File asli diterima dan lolos verifikasi SHA-256")
        } catch (error: Exception) {
            failIncoming(transfer, "Verifikasi file gagal")
        }
    }

    private fun failIncoming(transfer: IncomingTransfer, message: String) {
        closeIncoming(transfer, true)
        if (incomingTransfer?.transferId == transfer.transferId) incomingTransfer = null
        requestedTransfers.remove(transfer.transferId)
        sendJson(JSONObject().put("type", "original_cancel").put("transferId", transfer.transferId))
        onStatus(message)
    }

    private fun closeIncoming(transfer: IncomingTransfer, deleteFile: Boolean) {
        try { transfer.output.close() } catch (_: Exception) { }
        if (deleteFile) transfer.file.delete()
    }

    private fun receiveThumbnail(message: JSONObject) {
        val key = message.optString("key")
        val encoded = message.optString("data")
        if (key.isBlank() || key.length > 160) return
        if (message.optBoolean("error", false)) {
            onThumbnail(key, ByteArray(0))
            return
        }
        if (encoded.isBlank() || encoded.length > MAX_THUMB_BYTES * 2) return
        try {
            val decoded = Base64.decode(encoded, Base64.DEFAULT)
            if (decoded.isNotEmpty() && decoded.size <= MAX_THUMB_BYTES) {
                onThumbnail(key, decoded)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Thumbnail diterima tidak valid", error)
        }
    }

    private fun sendJson(message: JSONObject): Boolean {
        if (closed) return false
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        return bytes.isNotEmpty() && bytes.size <= MAX_FRAME_BYTES && sendFrame(bytes, false)
    }

    fun close() {
        if (role == GalleryP2pManager.Role.PARENT) cancelOriginal()
        closed = true
        observerHandler.removeCallbacks(mediaRefreshRunnable)
        mediaObserver?.let {
            try { appContext.contentResolver.unregisterContentObserver(it) } catch (_: Exception) { }
        }
        mediaObserver = null
        worker.shutdownNow()
        incomingTransfer?.let { closeIncoming(it, true) }
        incomingTransfer = null
        requestedTransfers.clear()
        cancelledTransfers.clear()
        childItems = emptyMap()
        synchronized(receivedIndex) { receivedIndex.clear() }
    }
}
