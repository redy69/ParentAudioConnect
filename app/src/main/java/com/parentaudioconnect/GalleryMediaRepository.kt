package com.santethp

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

/**
 * Read-only index of media on the child device.
 * Call only after the child has explicitly enabled gallery sharing and Android
 * media permissions have been granted. Original files are opened only after a
 * parent request is received over the authenticated P2P data channel.
 */
class GalleryMediaRepository(private val context: Context) {

    enum class Kind { PHOTO, VIDEO }

    data class MediaItem(
        val stableKey: String,
        val contentUri: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val modifiedEpochSeconds: Long,
        val kind: Kind
    )

    /**
     * Backup baseline/discovery must see the complete library. Android 14's
     * user-selected visual-media grant is not sufficient because unseen older
     * items could otherwise be misclassified as newly added.
     */
    fun hasFullMediaAccess(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, it
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun listMedia(limit: Int? = 500): List<MediaItem> {
        // A null limit is reserved for complete consent-time baseline scans.
        // Normal gallery browsing keeps its existing 1,000-item safety cap.
        val safeLimit = limit?.coerceIn(1, 1000)
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        val selection = (
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        )
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext() && (safeLimit == null || result.size < safeLimit)) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Media-$id"
                val mime = cursor.getString(mimeCol) ?: continue
                val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                val modified = cursor.getLong(modifiedCol).coerceAtLeast(0L)
                val mediaType = cursor.getInt(typeCol)
                val kind = when (mediaType) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> Kind.PHOTO
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> Kind.VIDEO
                    else -> continue
                }
                val uri = ContentUris.withAppendedId(collection, id)
                val stableKey = "$id:$size:$modified"
                result += MediaItem(
                    stableKey = stableKey,
                    contentUri = uri.toString(),
                    displayName = name.take(180),
                    mimeType = mime.take(120),
                    sizeBytes = size,
                    modifiedEpochSeconds = modified,
                    kind = kind
                )
            }
        }
        return result
    }

    /** Returns a small JPEG preview; this never reads or sends the original media file. */
    fun thumbnail(item: MediaItem, maxEdge: Int = 320, quality: Int = 72): ByteArray {
        val uri = android.net.Uri.parse(item.contentUri)
        val edge = maxEdge.coerceIn(96, 640)
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(
                uri, Size(edge, edge), CancellationSignal()
            )
        } else {
            val id = uri.lastPathSegment?.toLongOrNull()
                ?: throw FileNotFoundException("ID media tidak valid")
            if (item.kind == Kind.PHOTO) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver, id,
                    MediaStore.Images.Thumbnails.MINI_KIND, null
                )
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver, id,
                    MediaStore.Video.Thumbnails.MINI_KIND, null
                )
            }
        } ?: throw FileNotFoundException("Thumbnail tidak tersedia")

        return ByteArrayOutputStream().use { output ->
            val scaled = scaleDown(bitmap, edge)
            try {
                scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(45, 90), output)
                output.toByteArray()
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }
    }

    /** Open the original only in response to a specific, consented parent request. */
    fun openOriginal(item: MediaItem) =
        context.contentResolver.openInputStream(android.net.Uri.parse(item.contentUri))
            ?: throw FileNotFoundException("File asli tidak dapat dibuka")

    private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxEdge) return source
        val ratio = maxEdge.toFloat() / largest.toFloat()
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
