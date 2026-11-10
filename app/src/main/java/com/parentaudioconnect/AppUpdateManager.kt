package com.santethp

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors

object AppUpdateManager {
    private const val PREFS = "parent_audio_updates"
    private const val KEY_PENDING_URL = "pending_update_url"
    private const val KEY_DOWNLOAD_ID = "update_download_id"
    private const val KEY_DATA_VERSION = "data_version"
    private const val RELEASE_API =
        "https://api.github.com/repos/redy69/ParentAudioConnect/releases/latest"
    private const val DATA_MANIFEST_URL =
        "https://raw.githubusercontent.com/redy69/ParentAudioConnect/feature/modern-parent-ui/updates/manifest.json"
    private const val APK_NAME = "app-release.apk"
    private const val MAX_DATA_FILE_BYTES = 50L * 1024L * 1024L
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * This check is intentionally user-triggered. It never runs automatically on app launch.
     * Data packages contain non-executable app resources only.
     */
    fun checkForUpdates(activity: Activity, currentVersionCode: Int, onStatus: (String) -> Unit = {}) {
        onStatus("Memeriksa pembaruan SantetHP...")
        executor.execute {
            try {
                var dataUpdated = false
                var dataVersionMessage = "Data aplikasi sudah terbaru"

                try {
                    val manifest = fetchJson(DATA_MANIFEST_URL, github = false)
                    val remoteDataVersion = manifest.optInt("dataVersion", 0)
                    val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val localDataVersion = prefs.getInt(KEY_DATA_VERSION, 0)

                    if (remoteDataVersion > localDataVersion) {
                        applyDataUpdate(activity, manifest)
                        prefs.edit().putInt(KEY_DATA_VERSION, remoteDataVersion).apply()
                        dataUpdated = true
                        dataVersionMessage = "Data aplikasi berhasil diperbarui"
                    }
                } catch (error: Exception) {
                    android.util.Log.w("AppUpdateManager", "Data update check failed", error)
                    dataVersionMessage = "Pembaruan data tidak dapat diperiksa"
                }

                val release = fetchJson(RELEASE_API, github = true)
                val tag = release.optString("tag_name")
                val remoteVersion = tag.substringAfterLast("-", "").toIntOrNull()
                if (remoteVersion != null && remoteVersion > currentVersionCode) {
                    val assets = release.optJSONArray("assets") ?: throw IllegalStateException("APK pembaruan tidak ditemukan")
                    var apkUrl: String? = null
                    for (index in 0 until assets.length()) {
                        val asset = assets.optJSONObject(index) ?: continue
                        if (asset.optString("name") == APK_NAME) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                    val downloadUrl = apkUrl ?: throw IllegalStateException("File APK pembaruan tidak ditemukan")
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        onStatus("Pembaruan aplikasi tersedia: build $remoteVersion")
                        AlertDialog.Builder(activity)
                            .setTitle("Pembaruan SantetHP tersedia")
                            .setMessage(
                                "Versi baru build $remoteVersion tersedia. " +
                                    (if (dataUpdated) "Data aplikasi juga sudah diperbarui. " else "") +
                                    "Unduh APK dan lanjutkan instalasi Android?"
                            )
                            .setNegativeButton("Nanti", null)
                            .setPositiveButton("Unduh APK") { _, _ ->
                                beginInstallFlow(activity, downloadUrl)
                            }
                            .show()
                    }
                } else {
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        val message = if (dataUpdated) {
                            "$dataVersionMessage. APK SantetHP sudah versi terbaru."
                        } else if (dataVersionMessage == "Data aplikasi sudah terbaru") {
                            "SantetHP sudah menggunakan versi terbaru."
                        } else {
                            "$dataVersionMessage. Versi APK tetap terbaru."
                        }
                        onStatus(message)
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (error: Exception) {
                android.util.Log.w("AppUpdateManager", "App update check failed", error)
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    val message = "Gagal memeriksa pembaruan: ${error.localizedMessage ?: "koneksi bermasalah"}"
                    onStatus(message)
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchJson(url: String, github: Boolean): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", if (github) "application/vnd.github+json" else "application/json")
            connection.setRequestProperty("User-Agent", "SantetHP-Android")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Server pembaruan merespons HTTP ${connection.responseCode}")
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun applyDataUpdate(context: Context, manifest: JSONObject) {
        val files = manifest.optJSONArray("dataFiles")
        val root = File(context.filesDir, "remote-data")
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Folder data tidak dapat dibuat")
        val staging = File(context.cacheDir, "update-staging-${System.currentTimeMillis()}")
        if (!staging.mkdirs()) throw IllegalStateException("Folder sementara tidak dapat dibuat")
        val stagedFiles = mutableListOf<Pair<File, File>>()

        try {
            if (files != null) {
                for (index in 0 until files.length()) {
                    val item = files.optJSONObject(index) ?: throw IllegalStateException("Data file ke-${index + 1} tidak valid")
                    val relativePath = item.optString("path")
                    val downloadUrl = item.optString("url")
                    val expectedHash = item.optString("sha256").lowercase()

                    if (relativePath.isBlank() || relativePath.startsWith("/") ||
                        relativePath.contains("..") || relativePath.contains("\\") ||
                        !downloadUrl.startsWith("https://") ||
                        !expectedHash.matches(Regex("[a-f0-9]{64}"))) {
                        throw IllegalStateException("Manifest pembaruan berisi lokasi atau hash file yang tidak aman")
                    }

                    val destination = File(root, relativePath)
                    if (!destination.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                        throw IllegalStateException("Lokasi file pembaruan tidak diizinkan")
                    }
                    if (destination.isFile && sha256(destination) == expectedHash) continue

                    val staged = File(staging, relativePath)
                    staged.parentFile?.mkdirs()
                    downloadFile(downloadUrl, staged)
                    if (sha256(staged) != expectedHash) {
                        throw IllegalStateException("Verifikasi file gagal: $relativePath")
                    }
                    stagedFiles.add(staged to destination)
                }
            }

            for ((staged, destination) in stagedFiles) {
                destination.parentFile?.mkdirs()
                Files.move(
                    staged.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun downloadFile(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "SantetHP-Android")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Unduhan data gagal (HTTP ${connection.responseCode})")
            }
            val length = connection.contentLengthLong
            if (length > MAX_DATA_FILE_BYTES) throw IllegalStateException("File data terlalu besar")
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_DATA_FILE_BYTES) throw IllegalStateException("File data melebihi batas ukuran")
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun resumePendingInstall(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_PENDING_URL, null) ?: return
        if (!activity.packageManager.canRequestPackageInstalls()) return
        prefs.edit().remove(KEY_PENDING_URL).apply()
        downloadApk(activity, url)
    }

    private fun beginInstallFlow(activity: Activity, url: String) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PENDING_URL, url).apply()
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            try {
                activity.startActivity(settingsIntent)
                Toast.makeText(
                    activity,
                    "Izinkan pemasangan aplikasi dari sumber ini, lalu kembali ke aplikasi.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: Exception) {
                Toast.makeText(activity, "Tidak dapat membuka pengaturan pemasangan.", Toast.LENGTH_LONG).show()
            }
            return
        }
        downloadApk(activity, url)
    }

    private fun downloadApk(context: Context, url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("SantetHP")
                .setDescription("Mengunduh pembaruan aplikasi")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context, Environment.DIRECTORY_DOWNLOADS, APK_NAME
                )
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_DOWNLOAD_ID, id).apply()
            Toast.makeText(context, "Pembaruan APK sedang diunduh.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(context, "Gagal mengunduh pembaruan: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    internal fun handleDownloadComplete(context: Context, completedId: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_DOWNLOAD_ID, -1L) != completedId) return

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(completedId)
        manager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                Toast.makeText(context, "Unduhan pembaruan gagal (kode $reason).", Toast.LENGTH_LONG).show()
                prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                return
            }
        }

        val apkUri = manager.getUriForDownloadedFile(completedId) ?: return
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(installIntent)
        } catch (error: Exception) {
            Toast.makeText(context, "Tidak dapat membuka pemasang APK: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id >= 0L) AppUpdateManager.handleDownloadComplete(context, id)
        }
    }
}
