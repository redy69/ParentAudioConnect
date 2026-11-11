package com.santethp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

/**
 * Keeps a local copy of the parent's transparent rest schedule.
 * Local reminders continue when internet is unavailable; this feature never locks the phone.
 */
object ChildUsageScheduleReporter {
    private const val TAG = "ChildUsageSchedule"
    private const val PREFS = "parent_audio"
    private const val CHANNEL_ID = "santethp_usage_schedule"
    private const val NOTIFICATION_ID = 2510

    suspend fun syncAndNotify(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        if (prefs.getString("pair_role", null) != "child" || code.isNullOrBlank()) return

        try {
            val snapshot = withContext(Dispatchers.IO) {
                Tasks.await(
                    FirebaseFirestore.getInstance().collection("pairings").document(code).get()
                )
            }
            if (!snapshot.exists()) return
            val enabled = snapshot.getBoolean("usageScheduleEnabled") == true
            val start = snapshot.getLong("usageScheduleStartMinutes")?.toInt() ?: 21 * 60
            val end = snapshot.getLong("usageScheduleEndMinutes")?.toInt() ?: 6 * 60
            val days = (snapshot.get("usageScheduleDays") as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }?.toSet().orEmpty()
            val zoneId = snapshot.getString("usageScheduleTimeZone")
                ?.takeIf { it.isNotBlank() } ?: TimeZone.getDefault().id

            prefs.edit()
                .putBoolean("child_schedule_config_enabled", enabled)
                .putInt("child_schedule_start", start)
                .putInt("child_schedule_end", end)
                .putStringSet("child_schedule_days", days.map { it.toString() }.toSet())
                .putString("child_schedule_zone", zoneId)
                .apply()

            evaluateCachedAndNotify(context, code, publishStatus = true)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to sync child usage schedule", error)
            throw error
        }
    }

    /** Evaluates the last synchronized schedule locally, without requiring a network connection. */
    suspend fun evaluateCachedAndNotify(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child") return
        val code = prefs.getString("pair_code", null)
        evaluateCachedAndNotify(context, code, publishStatus = false)
    }

    private suspend fun evaluateCachedAndNotify(
        context: Context,
        code: String?,
        publishStatus: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("child_schedule_config_enabled", false)
        val start = prefs.getInt("child_schedule_start", 21 * 60)
        val end = prefs.getInt("child_schedule_end", 6 * 60)
        val days = prefs.getStringSet("child_schedule_days", emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty()
        val zoneId = prefs.getString("child_schedule_zone", null)
            ?.takeIf { it.isNotBlank() } ?: TimeZone.getDefault().id
        val quietNow = enabled && start != end && isQuietTime(
            System.currentTimeMillis(), start, end, days, TimeZone.getTimeZone(zoneId)
        )
        val previousKnown = prefs.getBoolean("child_schedule_state_known", false)
        val previousQuiet = prefs.getBoolean("child_schedule_quiet", false)

        prefs.edit()
            .putBoolean("child_schedule_state_known", true)
            .putBoolean("child_schedule_quiet", quietNow)
            .putBoolean("child_schedule_enabled", enabled)
            .apply()

        if (!enabled) {
            notificationManager(context).cancel(NOTIFICATION_ID)
        } else if (!previousKnown) {
            if (quietNow) showScheduleNotification(context, true, start, end)
        } else if (previousQuiet != quietNow) {
            showScheduleNotification(context, quietNow, start, end)
        }

        if (publishStatus && !code.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                Tasks.await(
                    FirebaseFirestore.getInstance().collection("pairings").document(code)
                        .update(
                            mapOf(
                                "childScheduleQuietNow" to quietNow,
                                "childScheduleCheckedAt" to FieldValue.serverTimestamp()
                            )
                        )
                )
            }
        }
    }

    private fun isQuietTime(
        nowMillis: Long,
        start: Int,
        end: Int,
        selectedDays: Set<Int>,
        zone: TimeZone
    ): Boolean {
        if (selectedDays.isEmpty()) return false
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val today = calendar.get(Calendar.DAY_OF_WEEK)
        if (start < end) return today in selectedDays && minute >= start && minute < end
        if (minute >= start) return today in selectedDays
        if (minute < end) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            return calendar.get(Calendar.DAY_OF_WEEK) in selectedDays
        }
        return false
    }

    private fun showScheduleNotification(
        context: Context,
        quietNow: Boolean,
        start: Int,
        end: Int
    ) {
        val manager = notificationManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Jadwal penggunaan SantetHP",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Pengingat waktu istirahat sesuai jadwal yang disepakati keluarga"
                }
            )
        }
        val timeRange = "%02d:%02d–%02d:%02d".format(
            start / 60, start % 60, end / 60, end % 60
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (quietNow) "Waktunya istirahat" else "Waktu istirahat selesai")
            .setContentText(
                if (quietNow) "Jadwal SantetHP aktif ($timeRange). Saatnya beristirahat."
                else "Jadwal istirahat SantetHP ($timeRange) sudah selesai."
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (quietNow) "Jadwal istirahat yang diatur orang tua sedang berlangsung ($timeRange)."
                    else "Waktu istirahat yang diatur orang tua sudah selesai. Selamat beraktivitas kembali."
                )
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
