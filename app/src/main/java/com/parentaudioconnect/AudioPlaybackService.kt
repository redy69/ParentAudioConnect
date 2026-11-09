package com.parentaudioconnect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class AudioPlaybackService : Service() {

    companion object {
        const val ACTION_PLAY = "com.parentaudioconnect.action.PLAY"
        const val EXTRA_FILE_PATH = "extra_file_path"
        private const val CHANNEL_ID = "parent_audio_playback"
        private const val NOTIFICATION_ID = 4101
    }

    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_PLAY) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        val file = path?.let(::File)
        if (file == null || !file.isFile || !file.canRead()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Memutar pesan suara"))
        startPlayback(file, startId)
        return START_NOT_STICKY
    }

    private fun startPlayback(file: File, startId: Int) {
        // Keep media volume at maximum for every received MP3.
        releasePlayer()

        try {
            val nextPlayer = MediaPlayer()
            player = nextPlayer
            nextPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            nextPlayer.setDataSource(file.absolutePath)
            nextPlayer.setOnPreparedListener { prepared ->
                raiseMediaVolumeForPlayback()
                prepared.start()
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification("Pesan suara sedang diputar"))
            }
            nextPlayer.setOnCompletionListener {
                releasePlayer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            nextPlayer.setOnErrorListener { _, _, _ ->
                releasePlayer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                true
            }
            nextPlayer.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun buildNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Parent Audio Connect")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemutaran pesan suara",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** Raise the media stream to maximum whenever a received MP3 starts playing. */
    private fun raiseMediaVolumeForPlayback() {
        val audioManager = getSystemService(AudioManager::class.java)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            maximum,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun releasePlayer() {
        player?.setOnPreparedListener(null)
        player?.setOnCompletionListener(null)
        player?.setOnErrorListener(null)
        player?.release()
        player = null
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
