package com.santethp

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Minimal Drive v3 client for app-created backup files; uses resumable uploads. */
class GoogleDriveUploader(private val accessToken: String) {
    data class UploadedFile(val id: String, val name: String, val mimeType: String, val sizeBytes: Long)
    fun interface ProgressListener { fun onProgress(sentBytes: Long, totalBytes: Long) }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val BACKUP_FOLDER = "SantetHP Backup"
        private const val CHUNK_BYTES = 1024 * 1024
    }

    /** Creates or reuses the app-owned backup folder in this Google account. */
    fun ensureBackupFolder(): String {
        val escapedName = BACKUP_FOLDER.replace("'", "\\'")
        val query = "name='$escapedName' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val listUrl = "$API/files?q=${encode(query)}&pageSize=10&fields=files(id,name)"
        val list = request("GET", listUrl, null)
        val existing = list.optJSONArray("files")
        if (existing != null && existing.length() > 0) {
            val id = existing.optJSONObject(0)?.optString("id").orEmpty()
            if (id.isNotBlank()) return id
        }
        val metadata = JSONObject()
            .put("name", BACKUP_FOLDER)
            .put("mimeType", "application/vnd.google-apps.folder")
        val created = request("POST", "$API/files?fields=id,name", metadata.toString().toByteArray(StandardCharsets.UTF_8))
        return created.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive tidak mengembalikan ID folder backup.")
    }

    /**
     * Creates or reuses a child-specific folder using an immutable app property key.
     * The display name may be changed by the parent without changing this folder identity.
     */
    fun ensureChildFolder(childKey: String, displayName: String, rootFolderId: String): String {
        require(childKey.isNotBlank()) { "Identitas perangkat anak kosong." }
        require(rootFolderId.isNotBlank()) { "Folder utama backup belum tersedia." }
        val stableKey = childKey.replace("'", "\\\\'")
        val query = "appProperties has { key='santethpChildKey' and value='$stableKey' }" +
            " and '$rootFolderId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val listUrl = "$API/files?q=${encode(query)}&pageSize=10&fields=files(id,name)"
        val files = request("GET", listUrl, null).optJSONArray("files")
        if (files != null && files.length() > 0) {
            val id = files.optJSONObject(0)?.optString("id").orEmpty()
            if (id.isNotBlank()) return id
        }
        val metadata = JSONObject()
            .put("name", displayName.trim().ifBlank { "HP Anak" })
            .put("mimeType", "application/vnd.google-apps.folder")
            .put("parents", org.json.JSONArray().put(rootFolderId))
            .put("appProperties", JSONObject().put("santethpChildKey", childKey))
        val created = request("POST", "$API/files?fields=id,name", metadata.toString().toByteArray(StandardCharsets.UTF_8))
        return created.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive tidak mengembalikan ID folder anak.")
    }

    /** Creates or reuses the Foto/Video subfolder under one child's folder. */
    fun ensureMediaFolder(childKey: String, mediaType: String, childFolderId: String): String {
        require(mediaType == "photo" || mediaType == "video") { "Jenis folder media tidak valid." }
        require(childKey.isNotBlank() && childFolderId.isNotBlank()) { "Identitas/folder anak tidak valid." }
        val stableKey = "${childKey}_$mediaType"
        val escapedKey = stableKey.replace("'", "\\\\'")
        val query = "appProperties has { key='santethpMediaFolderKey' and value='$escapedKey' }" +
            " and '$childFolderId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val listUrl = "$API/files?q=${encode(query)}&pageSize=10&fields=files(id,name)"
        val files = request("GET", listUrl, null).optJSONArray("files")
        if (files != null && files.length() > 0) {
            val id = files.optJSONObject(0)?.optString("id").orEmpty()
            if (id.isNotBlank()) return id
        }
        val folderName = if (mediaType == "photo") "Foto" else "Video"
        val metadata = JSONObject()
            .put("name", folderName)
            .put("mimeType", "application/vnd.google-apps.folder")
            .put("parents", org.json.JSONArray().put(childFolderId))
            .put("appProperties", JSONObject().put("santethpMediaFolderKey", stableKey))
        val created = request("POST", "$API/files?fields=id,name", metadata.toString().toByteArray(StandardCharsets.UTF_8))
        return created.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive tidak mengembalikan ID folder $folderName.")
    }

    /** Renames an app-created folder by ID; its stable identity and contents remain unchanged. */
    fun renameFolder(folderId: String, newName: String): String {
        require(folderId.isNotBlank()) { "ID folder tidak tersedia." }
        val safeName = newName.trim()
        require(safeName.isNotBlank() && safeName.length <= 80) { "Nama folder harus 1–80 karakter." }
        val body = JSONObject().put("name", safeName)
        val updated = request(
            "PATCH",
            "$API/files/${encodePath(folderId)}?fields=id,name",
            body.toString().toByteArray(StandardCharsets.UTF_8)
        )
        return updated.optString("name", safeName)
    }

    /** Uploads one file with Drive resumable upload and reports byte progress. */
    fun uploadFile(
        file: File,
        displayName: String,
        mimeType: String,
        parentFolderId: String,
        stableKey: String? = null,
        progress: ProgressListener = ProgressListener { _, _ -> },
        canContinue: () -> Boolean = { true }
    ): UploadedFile {
        require(file.isFile && file.canRead()) { "File backup tidak dapat dibaca." }
        if (!stableKey.isNullOrBlank()) {
            findExistingBackup(stableKey, parentFolderId)?.let { return it }
        }
        require(parentFolderId.isNotBlank()) { "Folder backup Google Drive belum tersedia." }
        val total = file.length()
        require(total > 0L) { "File backup kosong." }

        val metadata = JSONObject()
            .put("name", displayName)
            .put("mimeType", mimeType)
            .put("parents", org.json.JSONArray().put(parentFolderId))
        if (!stableKey.isNullOrBlank()) {
            metadata.put("appProperties", JSONObject().put("santethpStableKey", stableKey))
        }
        val init = open("POST", "$UPLOAD_API/files?uploadType=resumable&fields=id,name,mimeType,size",
            mapOf(
                "Content-Type" to "application/json; charset=UTF-8",
                "X-Upload-Content-Type" to mimeType,
                "X-Upload-Content-Length" to total.toString()
            ))
        init.outputStream.use { it.write(metadata.toString().toByteArray(StandardCharsets.UTF_8)) }
        val initCode = init.responseCode
        val sessionUrl = init.getHeaderField("Location")
        val initError = if (initCode !in 200..299) readError(init) else null
        init.disconnect()
        if (initCode !in 200..299 || sessionUrl.isNullOrBlank()) {
            throw IllegalStateException(initError ?: "Google Drive gagal membuat sesi upload ($initCode).")
        }

        var offset = 0L
        RandomAccessFile(file, "r").use { input ->
            while (offset < total) {
                if (!canContinue()) throw java.io.IOException("Jaringan backup tidak diizinkan saat ini • file tetap dalam antrean")
                val length = minOf(CHUNK_BYTES.toLong(), total - offset).toInt()
                val bytes = ByteArray(length)
                input.seek(offset)
                input.readFully(bytes)
                val end = offset + length - 1
                val connection = open("PUT", sessionUrl, mapOf(
                    "Content-Type" to mimeType,
                    "Content-Length" to length.toString(),
                    "Content-Range" to "bytes $offset-$end/$total"
                ))
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                if (code == 308) {
                    val range = connection.getHeaderField("Range")
                    val receivedEnd = range?.substringAfterLast('-')?.toLongOrNull()
                    offset = (receivedEnd?.plus(1) ?: (end + 1)).coerceAtMost(total)
                    connection.disconnect()
                    progress.onProgress(offset, total)
                } else if (code in 200..299) {
                    val response = readJson(connection)
                    connection.disconnect()
                    progress.onProgress(total, total)
                    val id = response.optString("id")
                    if (id.isBlank()) throw IllegalStateException("Upload selesai tetapi ID file Drive tidak ditemukan.")
                    return UploadedFile(id, response.optString("name", displayName), response.optString("mimeType", mimeType), response.optLong("size", total))
                } else {
                    val message = readError(connection)
                    connection.disconnect()
                    throw IllegalStateException("Upload Google Drive gagal ($code): $message")
                }
            }
        }
        throw IllegalStateException("Sesi upload Drive berakhir tanpa konfirmasi selesai.")
    }

    /** Finds a previous successful upload so a lost P2P acknowledgement cannot create duplicates. */
    private fun findExistingBackup(stableKey: String, parentFolderId: String): UploadedFile? {
        val query = "appProperties has { key='santethpStableKey' and value='${stableKey.replace("'", "\\'")}' }" +
            " and '$parentFolderId' in parents and trashed=false"
        val url = "$API/files?q=${encode(query)}&pageSize=5&fields=files(id,name,mimeType,size)"
        val files = request("GET", url, null).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        val item = files.optJSONObject(0) ?: return null
        val id = item.optString("id")
        if (id.isBlank()) return null
        return UploadedFile(
            id,
            item.optString("name"),
            item.optString("mimeType"),
            item.optString("size").toLongOrNull() ?: 0L
        )
    }

    private fun request(method: String, url: String, body: ByteArray?): JSONObject {
        val connection = open(method, url, mapOf("Content-Type" to "application/json; charset=UTF-8"))
        if (body != null) connection.outputStream.use { it.write(body) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val message = readError(connection)
            connection.disconnect()
            throw IllegalStateException("Google Drive API gagal ($code): $message")
        }
        val result = readJson(connection)
        connection.disconnect()
        return result
    }

    private fun open(method: String, url: String, headers: Map<String, String>): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 60000
            doInput = true
            doOutput = method == "POST" || method == "PUT" || method == "PATCH"
            setRequestProperty("Authorization", "Bearer $accessToken")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return connection
    }

    private fun readJson(connection: HttpURLConnection): JSONObject =
        JSONObject(connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })

    private fun readError(connection: HttpURLConnection): String = try {
        (connection.errorStream ?: connection.inputStream).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.take(1200)
    } catch (_: Exception) { "Respons server tidak dapat dibaca." }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun encodePath(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
