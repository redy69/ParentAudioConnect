package com.santethp

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors

/** Consent-gated child-to-parent transport for newly discovered Drive-backup media. */
class GalleryBackupP2pProtocol(
    context: Context,
    private val role: GalleryP2pManager.Role,
    private val sendFrame: (ByteArray, Boolean) -> Boolean,
    private val onStatus: (String) -> Unit,
    private val canReceiveBackup: () -> Boolean = { true },
    private val onFileReady: (String, File, String, String, (Boolean) -> Unit) -> Unit = { _, file, _, _, done ->
        file.delete()
        done(false)
    }
) {
    companion object {
        private const val TAG = "SantetHP-BackupP2P"
        private const val MAX_FRAME_BYTES = 96 * 1024
        private const val MAX_FILE_BYTES = 200L * 1024L * 1024L
        private const val CHUNK_BYTES = 16 * 1024
    }

    private data class Incoming(
        val id: String, val key: String, val name: String, val mime: String,
        val size: Long, val file: File, val output: FileOutputStream,
        val digest: MessageDigest, var sequence: Int = 0, var received: Long = 0
    )

    private val app = context.applicationContext
    private val tracker = GalleryBackupTracker(app)
    private val repository = GalleryMediaRepository(app)
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    @Volatile private var sendingId: String? = null
    @Volatile private var sendingKey: String? = null
    @Volatile private var incoming: Incoming? = null
    @Volatile private var requestOutstanding = false
    @Volatile private var awaitingAckKey: String? = null

    /** Parent calls this only after Drive authorization is available. Avoid overlapping requests. */
    @Synchronized
    fun requestNext(): Boolean {
        if (role != GalleryP2pManager.Role.PARENT || closed || !canReceiveBackup() ||
            requestOutstanding || incoming != null || awaitingAckKey != null
        ) return false
        val sent = send(JSONObject().put("type", "backup_next_request"))
        if (sent) requestOutstanding = true
        return sent
    }

    /** Child announces that its persistent queue has at least one pending item. */
    fun notifyPending() {
        if (role == GalleryP2pManager.Role.CHILD && !closed && tracker.isEnabled() && tracker.pendingItems().isNotEmpty()) {
            send(JSONObject().put("type", "backup_queue_available"))
        }
    }

    /** Returns true when this frame belongs to the backup protocol. */
    fun onFrame(bytes: ByteArray, binary: Boolean): Boolean {
        if (closed || binary || bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) return false
        val message = try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { return false }
        val type = message.optString("type")
        if (!type.startsWith("backup_")) return false
        when (type) {
            "backup_next_request" -> if (role == GalleryP2pManager.Role.CHILD) sendNextPending()
            "backup_queue_available" -> if (role == GalleryP2pManager.Role.PARENT) requestNext()
            "backup_queue_empty" -> if (role == GalleryP2pManager.Role.PARENT) requestOutstanding = false
            "backup_start" -> if (role == GalleryP2pManager.Role.PARENT) receiveStart(message)
            "backup_chunk" -> if (role == GalleryP2pManager.Role.PARENT) receiveChunk(message)
            "backup_end" -> if (role == GalleryP2pManager.Role.PARENT) receiveEnd(message)
            "backup_ack" -> if (role == GalleryP2pManager.Role.CHILD) receiveAck(message)
            "backup_ack_received" -> if (role == GalleryP2pManager.Role.PARENT) {
                val key = message.optString("key")
                if (key.isNotBlank() && key == awaitingAckKey) {
                    awaitingAckKey = null
                    requestNext()
                }
            }
            "backup_error" -> {
                val transferId = message.optString("transferId")
                if (role == GalleryP2pManager.Role.CHILD && transferId.isNotBlank() && transferId == sendingId) {
                    sendingId = null
                    sendingKey = null
                }
                if (role == GalleryP2pManager.Role.PARENT) {
                    requestOutstanding = false
                    incoming?.let { failIncoming(it, message.optString("message", "Backup P2P gagal")) }
                }
                onStatus(message.optString("message", "Backup P2P gagal"))
            }
        }
        return true
    }

    private fun sendNextPending() {
        if (!tracker.isEnabled() || !repository.hasFullMediaAccess()) {
            send(JSONObject().put("type", "backup_error")
                .put("message", "Persetujuan backup atau izin media anak tidak aktif"))
            return
        }
        if (sendingId != null) return
        worker.execute {
            if (closed || sendingId != null || !tracker.isEnabled()) return@execute
            val pending = tracker.pendingItems().firstOrNull()
            if (pending == null) {
                onStatus("Antrean backup kosong • menunggu foto/video baru")
                send(JSONObject().put("type", "backup_queue_empty"))
                return@execute
            }
            if (pending.sizeBytes <= 0 || pending.sizeBytes > MAX_FILE_BYTES) {
                send(JSONObject().put("type", "backup_error").put("key", pending.stableKey)
                    .put("message", "File " + pending.displayName + " melewati batas backup 200 MB"))
                return@execute
            }
            val item = GalleryMediaRepository.MediaItem(
                stableKey = pending.stableKey,
                contentUri = pending.contentUri,
                displayName = pending.displayName,
                mimeType = pending.mimeType,
                sizeBytes = pending.sizeBytes,
                modifiedEpochSeconds = pending.modifiedEpochSeconds,
                kind = if (pending.mimeType.startsWith("video/")) GalleryMediaRepository.Kind.VIDEO
                    else GalleryMediaRepository.Kind.PHOTO
            )
            val transferId = UUID.randomUUID().toString()
            sendingId = transferId
            sendingKey = item.stableKey
            try {
                val start = JSONObject().put("type", "backup_start").put("transferId", transferId)
                    .put("key", item.stableKey).put("name", item.displayName.take(180))
                    .put("mime", item.mimeType.take(120)).put("size", item.sizeBytes)
                if (!sendWithRetry(start, transferId)) throw IllegalStateException("Koneksi P2P tidak siap")
                val digest = MessageDigest.getInstance("SHA-256")
                var sent = 0L
                var sequence = 0
                repository.openOriginal(item).use { input ->
                    val buffer = ByteArray(CHUNK_BYTES)
                    while (true) {
                        if (closed || sendingId != transferId || !tracker.isEnabled()) {
                            throw InterruptedException("Persetujuan backup dicabut atau koneksi ditutup")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        sent += count
                        if (sent > item.sizeBytes || sent > MAX_FILE_BYTES) throw IllegalStateException("Ukuran media berubah")
                        digest.update(buffer, 0, count)
                        val chunk = JSONObject().put("type", "backup_chunk").put("transferId", transferId)
                            .put("sequence", sequence++)
                            .put("data", Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP))
                        if (!sendWithRetry(chunk, transferId)) throw IllegalStateException("Pengiriman potongan terputus")
                        if (sent % (1024L * 1024L) < count || sent == item.sizeBytes) {
                            onStatus("Mengirim backup: " +
                                android.text.format.Formatter.formatFileSize(app, sent) + " / " +
                                android.text.format.Formatter.formatFileSize(app, item.sizeBytes))
                        }
                    }
                }
                if (sent != item.sizeBytes) throw IllegalStateException("Ukuran file tidak cocok")
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!sendWithRetry(JSONObject().put("type", "backup_end").put("transferId", transferId)
                        .put("totalBytes", sent).put("sha256", hash), transferId)) {
                    throw IllegalStateException("Konfirmasi akhir transfer gagal dikirim")
                }
                onStatus("File terkirim ke HP orang tua • menunggu konfirmasi upload Google Drive")
            } catch (error: Exception) {
                Log.w(TAG, "Backup P2P gagal", error)
                send(JSONObject().put("type", "backup_error").put("transferId", transferId)
                    .put("key", item.stableKey).put("message", error.localizedMessage ?: "Transfer gagal"))
                sendingId = null
                sendingKey = null
            }
        }
    }

    @Synchronized
    private fun receiveStart(message: JSONObject) {
        val id = message.optString("transferId")
        val key = message.optString("key")
        val size = message.optLong("size", -1)
        if (!requestOutstanding || id.isBlank() || id.length > 64 || key.isBlank() || key.length > 160 ||
            size <= 0 || size > MAX_FILE_BYTES || incoming != null
        ) {
            requestOutstanding = false
            send(JSONObject().put("type", "backup_error").put("transferId", id).put("message", "Metadata backup tidak valid"))
            return
        }
        requestOutstanding = false
        try {
            val file = File.createTempFile("santethp-drive-backup-", ".part", app.cacheDir)
            incoming = Incoming(id, key, message.optString("name").take(180),
                message.optString("mime").take(120).ifBlank { "application/octet-stream" },
                size, file, FileOutputStream(file), MessageDigest.getInstance("SHA-256"))
            onStatus("Menerima file backup: 0 / " + android.text.format.Formatter.formatFileSize(app, size))
        } catch (_: Exception) {
            send(JSONObject().put("type", "backup_error").put("transferId", id)
                .put("message", "Tidak dapat membuat file sementara"))
        }
    }

    private fun receiveChunk(message: JSONObject) {
        val transfer = incoming ?: return
        if (message.optString("transferId") != transfer.id) return
        val encoded = message.optString("data")
        if (message.optInt("sequence", -1) != transfer.sequence ||
            encoded.isBlank() || encoded.length > CHUNK_BYTES * 2
        ) {
            failIncoming(transfer, "Urutan potongan backup tidak valid")
            return
        }
        try {
            val data = Base64.decode(encoded, Base64.DEFAULT)
            if (data.isEmpty() || data.size > CHUNK_BYTES || transfer.received + data.size > transfer.size) {
                failIncoming(transfer, "Ukuran potongan backup tidak valid")
                return
            }
            transfer.output.write(data)
            transfer.digest.update(data)
            transfer.received += data.size
            transfer.sequence++
            if (transfer.received % (1024L * 1024L) < data.size || transfer.received == transfer.size) {
                onStatus("Menerima backup: " +
                    android.text.format.Formatter.formatFileSize(app, transfer.received) + " / " +
                    android.text.format.Formatter.formatFileSize(app, transfer.size))
            }
        } catch (_: Exception) {
            failIncoming(transfer, "Gagal menyimpan potongan backup")
        }
    }

    private fun receiveEnd(message: JSONObject) {
        val transfer = incoming ?: return
        if (message.optString("transferId") != transfer.id) return
        try {
            transfer.output.flush()
            transfer.output.close()
            val hash = transfer.digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (transfer.received != transfer.size || message.optLong("totalBytes", -1) != transfer.size ||
                hash != message.optString("sha256")
            ) {
                failIncoming(transfer, "Verifikasi SHA-256 backup gagal")
                return
            }
            incoming = null
            awaitingAckKey = transfer.key
            onStatus("File backup lolos verifikasi • mengunggah ke Google Drive")
            onFileReady(transfer.key, transfer.file, transfer.name, transfer.mime) { uploaded ->
                if (uploaded) {
                    send(JSONObject().put("type", "backup_ack").put("transferId", transfer.id)
                        .put("key", transfer.key).put("success", true))
                } else {
                    if (awaitingAckKey == transfer.key) awaitingAckKey = null
                    transfer.file.delete()
                    send(JSONObject().put("type", "backup_ack").put("transferId", transfer.id)
                        .put("key", transfer.key).put("success", false))
                }
            }
        } catch (_: Exception) {
            failIncoming(transfer, "Penyelesaian file backup gagal")
        }
    }

    private fun receiveAck(message: JSONObject) {
        val id = message.optString("transferId")
        if (id.isBlank() || id != sendingId) return
        val key = message.optString("key")
        if (key.isBlank() || key != sendingKey) return
        val uploaded = message.optBoolean("success", false)
        if (uploaded) {
            tracker.markUploaded(key)
            onStatus("Backup Google Drive berhasil • " + tracker.pendingItems().size + " file masih dalam antrean")
        } else {
            onStatus("Upload Google Drive gagal • file tetap dalam antrean; hubungkan ulang Drive untuk mencoba lagi")
        }
        sendingId = null
        sendingKey = null
        if (uploaded) send(JSONObject().put("type", "backup_ack_received").put("key", key))
    }

    private fun failIncoming(transfer: Incoming, message: String) {
        try { transfer.output.close() } catch (_: Exception) { }
        transfer.file.delete()
        if (incoming?.id == transfer.id) incoming = null
        requestOutstanding = false
        if (awaitingAckKey == transfer.key) awaitingAckKey = null
        send(JSONObject().put("type", "backup_error").put("transferId", transfer.id)
            .put("key", transfer.key).put("message", message))
        onStatus(message)
    }

    private fun sendWithRetry(message: JSONObject, id: String): Boolean {
        repeat(100) {
            if (closed || sendingId != id || !tracker.isEnabled()) return false
            if (send(message)) return true
            try { Thread.sleep(100) } catch (_: InterruptedException) { return false }
        }
        return false
    }

    private fun send(message: JSONObject): Boolean {
        if (closed) return false
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        return bytes.isNotEmpty() && bytes.size <= MAX_FRAME_BYTES && sendFrame(bytes, false)
    }

    fun close() {
        if (closed) return
        closed = true
        sendingId = null
        sendingKey = null
        requestOutstanding = false
        awaitingAckKey = null
        incoming?.let {
            try { it.output.close() } catch (_: Exception) { }
            it.file.delete()
        }
        incoming = null
        worker.shutdownNow()
    }
}
