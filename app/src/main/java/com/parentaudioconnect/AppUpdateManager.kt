package com.santethp

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import android.app.AlertDialog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AppUpdateManager {
    private const val PREFS = "parent_audio_updates"
    private const val KEY_PENDING_URL = "pending_update_url"
    private const val KEY_DOWNLOAD_ID = "update_download_id"
    private const val RELEASE_API =
        "https://api.github.com/repos/redy69/ParentAudioConnect/releases/latest"
    private const val APK_NAME = "app-release.apk"
    private val executor = Executors.newSingleThreadExecutor()

    fun checkForUpdates(activity: Activity, currentVersionCode: Int) {
        executor.execute {
            try {
                val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
                val release = try {
                    connection.connectTimeout = 12000
                    connection.readTimeout = 12000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "SantetHP-Android")
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("GitHub update check failed")
                    }
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }

                val tag = release.optString("tag_name")
                val remoteVersion = tag.substringAfterLast("-", "").toIntOrNull() ?: return@execute
                if (remoteVersion <= currentVersionCode) return@execute

                val assets = release.optJSONArray("assets") ?: return@execute
                var apkUrl: String? = null
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name") == APK_NAME) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                val downloadUrl = apkUrl ?: return@execute

                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    AlertDialog.Builder(activity)
                        .setTitle("Pembaruan tersedia")
                        .setMessage(
                            "Versi baru SantetHP tersedia (build $remoteVersion). " +
                                "Unduh dan pasang sekarang?"
                        )
                        .setNegativeButton("Nanti", null)
                        .setPositiveButton("Unduh & pasang") { _, _ ->
                            beginInstallFlow(activity, downloadUrl)
                        }
                        .show()
                }
            } catch (error: Exception) {
                android.util.Log.w("AppUpdateManager", "Update check failed", error)
            }
        }
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
            Toast.makeText(context, "Pembaruan sedang diunduh.", Toast.LENGTH_LONG).show()
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

class UpdateInstallReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id >= 0L) AppUpdateManager.handleDownloadComplete(context, id)
        }
    }
}
