package com.santethp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

/**
 * Captures notifications only after the child has explicitly opted in locally
 * and enabled Android's Notification Access setting.
 */
class ChildNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child" ||
            !prefs.getBoolean("child_notification_monitoring_enabled", false)
        ) return

        val code = prefs.getString("pair_code", null)?.takeIf { it.isNotBlank() } ?: return
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return

        try {
            val appName = try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                packageName
            }
            val extras = sbn.notification?.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.trim()?.take(300).orEmpty()
            val body = (
                extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras?.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
                )?.toString()?.trim()?.take(1200).orEmpty()

            val eventId = UUID.nameUUIDFromBytes(
                (packageName + "|" + sbn.key + "|" + sbn.postTime).toByteArray(Charsets.UTF_8)
            ).toString()
            val payload = hashMapOf<String, Any>(
                "packageName" to packageName.take(200),
                "appName" to appName.take(120),
                "title" to title,
                "text" to body,
                "postedAt" to FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance()
                .collection("pairings")
                .document(code)
                .collection("notifications")
                .document(eventId)
                .set(payload)
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to store notification from $packageName", error)
                }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to capture notification", error)
        }
    }

    companion object {
        private const val TAG = "SantetHPNotifications"
    }
}
