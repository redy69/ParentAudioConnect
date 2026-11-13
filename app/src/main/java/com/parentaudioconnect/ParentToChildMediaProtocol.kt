package com.santethp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors

/** Consent-gated parent-initiated photo/video delivery over the paired P2P channel. */
class ParentToChildMediaProtocol(
    context: Context,
    private val role: GalleryP2pManager.Role,
    private val sendFrame: (ByteArray, Boolean) -> Boolean,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private const val TAG = "SantetHP-ParentSend"
        private const val MAX_FRAME = 96 * 1024
        private const val MAX_FILE = 200L * 1024L * 1024L
        private const val CHUNK = 16 * 1024
    }

    private data class Incoming(
        val id: String, val name: String, val mime: String, val size: Long,
        val file: File, val output: FileOutputStream, val digest: MessageDigest,
        var sequence: Int = 0, var received: Long = 0
    )

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    @Volatile private var activeId: String? = null
    @Volatile private var incoming: Incoming? = null

    fun sendFromUri(uri: Uri, displayName: String, mimeType: String): Boolean {
        if (role != GalleryP2pManager.Role.PARENT || closed || activeId != null) return false
        val mime = mimeType.lowercase()
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
            onStatus("Hanya foto dan video yang dapat dikirim")
            return false
        }
        val id = UUID.randomUUID().toString()
        activeId = id
        worker.execute {
            var temp: File? = null
            try {
                val sourceFile = File.createTempFile("santethp-parent-send-", ".tmp", app.cacheDir)
                temp = sourceFile
                app.contentResolver.openInputStream(uri)?.use { input ->
                    sourceFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("File pilihan tidak dapat dibaca")
                if (sourceFile.length() <= 0L || sourceFile.length() > MAX_FILE) {
                    throw IllegalStateException("Ukuran file kosong atau melebihi batas 200 MB")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                val start = JSONObject().put("type", "parent_send_start").put("transferId", id)
                    .put("name", sanitizeName(displayName)).put("mime", mime.take(120)).put("size", sourceFile.length())
                if (!sendWithRetry(start, id)) throw IllegalStateException("HP anak belum siap menerima file")
                sourceFile.inputStream().use { input ->
                    val buffer = ByteArray(CHUNK)
                    var seq = 0
                    var sent = 0L
                    while (true) {
                        if (closed || activeId != id) throw InterruptedException("Pengiriman dibatalkan")
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        sent += count
                        digest.update(buffer, 0, count)
                        val frame = JSONObject().put("type", "parent_send_chunk").put("transferId", id)
                            .put("sequence", seq++)
                            .put("data", Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP))
                        if (!sendWithRetry(frame, id)) throw IllegalStateException("Koneksi terputus saat mengirim file")
                        if (sent % (1024L * 1024L) < count || sent == sourceFile.length()) {
                            onStatus("Mengirim ke HP anak: " +
                                android.text.format.Formatter.formatFileSize(app, sent) + " / " +
                                android.text.format.Formatter.formatFileSize(app, sourceFile.length()))
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!sendWithRetry(JSONObject().put("type", "parent_send_end").put("transferId", id)
                        .put("totalBytes", sourceFile.length()).put("sha256", hash), id)) {
                    throw IllegalStateException("Konfirmasi akhir gagal dikirim")
                }
                onStatus("File terkirim • menunggu HP anak menyimpan ke galeri")
            } catch (error: Exception) {
                Log.w(TAG, "Pengiriman media ke anak gagal", error)
                send(JSONObject().put("type", "parent_send_error").put("transferId", id)
                    .put("message", error.localizedMessage ?: "Pengiriman gagal"))
                activeId = null
                onStatus("Pengiriman gagal: " + (error.localizedMessage ?: "kesalahan"))
            } finally {
                temp?.delete()
            }
        }
        return true
    }

    fun onFrame(bytes: ByteArray, binary: Boolean): Boolean {
        if (closed || binary || bytes.isEmpty() || bytes.size > MAX_FRAME) return false
        val msg = try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { return false }
        val type = msg.optString("type")
        if (!type.startsWith("parent_send_")) return false
        if (role == GalleryP2pManager.Role.CHILD) {
            when (type) {
                "parent_send_start" -> receiveStart(msg)
                "parent_send_chunk" -> receiveChunk(msg)
                "parent_send_end" -> receiveEnd(msg)
                "parent_send_error" -> {
                    val id = msg.optString("transferId")
                    incoming?.takeIf { it.id == id }?.let { failIncoming(it, "Pengiriman dibatalkan orang tua") }
                }
            }
        } else if (type == "parent_send_ack" || type == "parent_send_error") {
            val id = msg.optString("transferId")
            if (id.isNotBlank() && id == activeId) {
                activeId = null
                onStatus(if (msg.optBoolean("success", false)) {
                    "Berhasil disimpan di galeri HP anak"
                } else {
                    "HP anak menolak transfer: " + msg.optString("message", "persetujuan atau penyimpanan tidak tersedia")
                })
            }
        }
        return true
    }

    private fun receiveAllowed(): Boolean {
        val prefs = app.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        return role == GalleryP2pManager.Role.CHILD &&
            prefs.getBoolean("child_parent_media_receive_consent", false) &&
            prefs.getBoolean("child_parent_media_server_confirmed", false)
    }

    private fun receiveStart(msg: JSONObject) {
        val id = msg.optString("transferId")
        val size = msg.optLong("size", -1)
        val mime = msg.optString("mime").lowercase()
        if (!receiveAllowed()) {
            send(JSONObject().put("type", "parent_send_error").put("transferId", id)
                .put("message", "Penerimaan file belum diaktifkan dan disetujui di HP anak"))
            return
        }
        if (id.isBlank() || id.length > 64 || size <= 0 || size > MAX_FILE ||
            (!mime.startsWith("image/") && !mime.startsWith("video/")) || incoming != null
        ) {
            send(JSONObject().put("type", "parent_send_error").put("transferId", id)
                .put("message", "Metadata foto/video tidak valid"))
            return
        }
        try {
            val file = File.createTempFile("santethp-child-receive-", ".part", app.cacheDir)
            incoming = Incoming(id, sanitizeName(msg.optString("name")), mime, size, file,
                FileOutputStream(file), MessageDigest.getInstance("SHA-256"))
            onStatus("Menerima dari HP orang tua: 0 / " +
                android.text.format.Formatter.formatFileSize(app, size))
        } catch (_: Exception) {
            send(JSONObject().put("type", "parent_send_error").put("transferId", id)
                .put("message", "Tidak dapat menyiapkan penyimpanan sementara"))
        }
    }

    private fun receiveChunk(msg: JSONObject) {
        val item = incoming ?: return
        if (msg.optString("transferId") != item.id) return
        if (!receiveAllowed()) {
            failIncoming(item, "Persetujuan penerimaan dicabut selama transfer")
            return
        }
        val encoded = msg.optString("data")
        if (msg.optInt("sequence", -1) != item.sequence || encoded.isBlank() || encoded.length > CHUNK * 2) {
            failIncoming(item, "Urutan potongan file tidak valid")
            return
        }
        try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            if (bytes.isEmpty() || bytes.size > CHUNK || item.received + bytes.size > item.size) {
                failIncoming(item, "Ukuran potongan file tidak valid")
                return
            }
            item.output.write(bytes)
            item.digest.update(bytes)
            item.received += bytes.size
            item.sequence++
            if (item.received % (1024L * 1024L) < bytes.size || item.received == item.size) {
                onStatus("Menerima dari orang tua: " +
                    android.text.format.Formatter.formatFileSize(app, item.received) + " / " +
                    android.text.format.Formatter.formatFileSize(app, item.size))
            }
        } catch (_: Exception) {
            failIncoming(item, "Gagal menulis file sementara")
        }
    }

    private fun receiveEnd(msg: JSONObject) {
        val item = incoming ?: return
        if (msg.optString("transferId") != item.id) return
        try {
            item.output.flush()
            item.output.close()
            val hash = item.digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (item.received != item.size || msg.optLong("totalBytes", -1) != item.size ||
                hash != msg.optString("sha256")
            ) {
                failIncoming(item, "Verifikasi SHA-256 gagal; file tidak disimpan")
                return
            }
            if (!receiveAllowed()) {
                failIncoming(item, "Persetujuan penerimaan dicabut sebelum penyimpanan")
                return
            }
            incoming = null
            worker.execute {
                try {
                    if (!receiveAllowed()) throw SecurityException("Persetujuan penerimaan dicabut sebelum penyimpanan")
                    val saved = saveToMediaStore(item.file, item.name, item.mime)
                    item.file.delete()
                    send(JSONObject().put("type", "parent_send_ack").put("transferId", item.id)
                        .put("success", saved != null))
                    onStatus(if (saved != null) "Foto/video berhasil disimpan ke galeri HP ini" else "Gagal menyimpan file ke galeri")
                } catch (error: Exception) {
                    item.file.delete()
                    send(JSONObject().put("type", "parent_send_ack").put("transferId", item.id)
                        .put("success", false).put("message", error.localizedMessage ?: "Penyimpanan galeri gagal"))
                    onStatus(error.localizedMessage ?: "Penyimpanan galeri gagal")
                }
            }
        } catch (error: Exception) {
            failIncoming(item, error.localizedMessage ?: "Penyimpanan galeri gagal")
        }
    }

    private fun saveToMediaStore(file: File, name: String, mime: String): Uri? {
        if (Build.VERSION.SDK_INT < 29 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                app, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) throw SecurityException("Izin penyimpanan belum diberikan")
        val image = mime.startsWith("image/")
        val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizeName(name))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000L)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES) + "/SantetHP")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(
                    if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                )
                val directory = File(publicDir, "SantetHP")
                if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Folder galeri tidak dapat dibuat")
                val safeName = sanitizeName(name)
                val base = safeName.substringBeforeLast('.', safeName)
                val extension = safeName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
                var target = File(directory, safeName)
                var suffix = 1
                while (target.exists()) {
                    target = File(directory, "$base ($suffix)$extension")
                    suffix++
                }
                put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
                put(MediaStore.MediaColumns.DATA, target.absolutePath)
            }
        }
        val uri = app.contentResolver.insert(collection, values) ?: return null
        try {
            app.contentResolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: throw IllegalStateException("Penyimpanan galeri tidak dapat dibuka")
            if (Build.VERSION.SDK_INT >= 29) {
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                app.contentResolver.update(uri, ready, null, null)
            }
            return uri
        } catch (error: Exception) {
            try { app.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
            throw error
        }
    }

    private fun failIncoming(item: Incoming, message: String) {
        try { item.output.close() } catch (_: Exception) { }
        item.file.delete()
        if (incoming?.id == item.id) incoming = null
        send(JSONObject().put("type", "parent_send_ack").put("transferId", item.id)
            .put("success", false).put("message", message))
        onStatus(message)
    }

    private fun sendWithRetry(message: JSONObject, id: String): Boolean {
        repeat(100) {
            if (closed || activeId != id) return false
            if (send(message)) return true
            try { Thread.sleep(100) } catch (_: InterruptedException) { return false }
        }
        return false
    }

    private fun send(message: JSONObject): Boolean {
        if (closed) return false
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        return bytes.size <= MAX_FRAME && sendFrame(bytes, false)
    }

    private fun sanitizeName(value: String): String {
        val clean = value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return clean.take(160).ifBlank { "SantetHP-" + System.currentTimeMillis() }
    }

    fun close() {
        closed = true
        activeId = null
        incoming?.let {
            try { it.output.close() } catch (_: Exception) { }
            it.file.delete()
        }
        incoming = null
        worker.shutdownNow()
    }
}
