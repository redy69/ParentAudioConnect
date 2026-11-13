package com.santethp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks only media created after the parent explicitly enables Drive backup.
 *
 * The first scan is a baseline: every item already present on the child device
 * is recorded as known and is never queued for automatic backup. Later scans
 * add only unseen stable media keys to a persistent pending queue.
 *
 * This class deliberately does not upload files or request Google credentials;
 * transport and Drive OAuth are handled by the backup coordinator.
 */
class GalleryBackupTracker(context: Context) {
    data class PendingMedia(
        val stableKey: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val modifiedEpochSeconds: Long
    )

    companion object {
        private const val PREFS = "parent_audio"
        private const val KEY_ENABLED = "gallery_drive_backup_enabled"
        private const val KEY_BASELINE_DONE = "gallery_drive_backup_baseline_done"
        private const val KEY_KNOWN = "gallery_drive_backup_known_keys"
        private const val KEY_PENDING = "gallery_drive_backup_pending"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /**
     * Called only after the child user explicitly enables backup.
     * Existing media is captured as the baseline, not queued for upload.
     */
    @Synchronized
    fun enableWithBaseline(currentItems: List<GalleryMediaRepository.MediaItem>) {
        val known = currentItems.map { it.stableKey }.toSet()
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_BASELINE_DONE, true)
            .putString(KEY_KNOWN, JSONArray(known.toList()).toString())
            .putString(KEY_PENDING, JSONArray().toString())
            .apply()
    }

    @Synchronized
    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
    }

    /**
     * Adds newly discovered items to a durable queue. Items present at the
     * activation baseline are excluded. Pending items survive process restarts.
     */
    @Synchronized
    fun discoverNewMedia(currentItems: List<GalleryMediaRepository.MediaItem>): List<PendingMedia> {
        if (!isEnabled() || !prefs.getBoolean(KEY_BASELINE_DONE, false)) return emptyList()

        val known = readKnownKeys().toMutableSet()
        val pending = readPending().associateBy { it.stableKey }.toMutableMap()
        var changed = false

        currentItems.forEach { item ->
            if (known.add(item.stableKey)) {
                pending[item.stableKey] = PendingMedia(
                    stableKey = item.stableKey,
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes,
                    modifiedEpochSeconds = item.modifiedEpochSeconds
                )
                changed = true
            }
        }

        if (changed) {
            prefs.edit()
                .putString(KEY_KNOWN, JSONArray(known.toList()).toString())
                .putString(KEY_PENDING, encodePending(pending.values))
                .apply()
        }
        return pending.values.toList()
    }

    @Synchronized
    fun pendingItems(): List<PendingMedia> = readPending()

    /** Remove an item only after the Drive upload is confirmed successful. */
    @Synchronized
    fun markUploaded(stableKey: String) {
        if (stableKey.isBlank()) return
        val pending = readPending().filterNot { it.stableKey == stableKey }
        prefs.edit().putString(KEY_PENDING, encodePending(pending)).apply()
    }

    private fun readKnownKeys(): Set<String> {
        val raw = prefs.getString(KEY_KNOWN, null) ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    val key = array.optString(i)
                    if (key.isNotBlank()) add(key)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun readPending(): List<PendingMedia> {
        val raw = prefs.getString(KEY_PENDING, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val key = item.optString("key")
                    if (key.isBlank()) continue
                    add(
                        PendingMedia(
                            stableKey = key,
                            displayName = item.optString("name"),
                            mimeType = item.optString("mime"),
                            sizeBytes = item.optLong("size").coerceAtLeast(0L),
                            modifiedEpochSeconds = item.optLong("modified").coerceAtLeast(0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodePending(items: Collection<PendingMedia>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("key", item.stableKey)
                    .put("name", item.displayName)
                    .put("mime", item.mimeType)
                    .put("size", item.sizeBytes)
                    .put("modified", item.modifiedEpochSeconds)
            )
        }
        return array.toString()
    }
}
