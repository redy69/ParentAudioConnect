
package com.santethp

import android.content.Context
import android.net.Uri

import io.livekit.android.room.Room
import io.livekit.android.room.datastream.StreamBytesOptions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class Mp3TransferManager(
    private val context: Context,
    private val room: Room,
    private val scope: CoroutineScope,
    private val onStatus: (String) -> Unit,
    private val onReceived: (File) -> Unit
) {

    companion object {
        const val TOPIC = "parent-audio-mp3"
    }

    init {
        room.registerByteStreamHandler(TOPIC) { reader, identity ->

            scope.launch {
                try {
                    onStatus("Menerima file MP3...")

                    val chunks = reader.readAll()
                    val output = ByteArrayOutputStream()

                    chunks.forEach { chunk ->
                        output.write(chunk)
                    }

                    val originalName =
                        reader.info.name ?: "audio-anak.mp3"

                    val safeName = originalName
                        .substringAfterLast("/")
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")

                    val fileName = if (
                        safeName.endsWith(".mp3", true)
                    ) {
                        safeName
                    } else {
                        "$safeName.mp3"
                    }

                    val destination = File(
                        context.filesDir,
                        fileName
                    )

                    withContext(Dispatchers.IO) {
                        FileOutputStream(destination).use {
                            output.writeTo(it)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onReceived(destination)
                        onStatus("MP3 berhasil diterima!")
                    }

                } catch (error: Exception) {
                    withContext(Dispatchers.Main) {
                        onStatus(
                            "Gagal menerima MP3: ${error.message}"
                        )
                    }
                }
            }
        }
    }

    fun send(uri: Uri) {
        scope.launch {
            var temporaryFile: File? = null

            try {
                onStatus("Menyiapkan file MP3...")

                temporaryFile = withContext(Dispatchers.IO) {
                    val file = File(
                        context.cacheDir,
                        "audio-kirim-${System.currentTimeMillis()}.mp3"
                    )

                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) {
                            "File tidak dapat dibuka"
                        }

                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }

                    file
                }

                require(temporaryFile.length() > 0) {
                    "File MP3 kosong"
                }

                val result = room.localParticipant.sendFile(
                    temporaryFile,
                    StreamBytesOptions(
                        topic = TOPIC,
                        mimeType = "audio/mpeg",
                        name = temporaryFile.name,
                        totalSize = temporaryFile.length()
                    )
                )

                result.onSuccess {
                    onStatus("MP3 berhasil dikirim!")
                }.onFailure { error ->
                    onStatus(
                        "Gagal mengirim MP3: ${error.message}"
                    )
                }

            } catch (error: Exception) {
                onStatus(
                    "Gagal menyiapkan MP3: ${error.message}"
                )
            } finally {
                temporaryFile?.delete()
            }
        }
    }
}
