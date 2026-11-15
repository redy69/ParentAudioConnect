package com.santethp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

/**
 * Captures notifications only after the child has explicitly opted in locally
 * and enabled Android's Notification Access setting.
 */
class ChildNotificationListenerService : NotificationListenerService() {

    private var lastStatusCodeReported: String? = null
    private var observedPrefs: android.content.SharedPreferences? = null

    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pair_role" || key == "pair_code" ||
                key == "child_notification_monitoring_enabled"
            ) {
                // A pairing document can be removed and recreated with the same code
                // while Android keeps this listener service bound. Never trust the old
                // in-memory confirmation after pairing/consent state changes.
                lastStatusCodeReported = null
                publishConnectedStatusIfEligible()
            }
        }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser != null) {
            // Retry after anonymous Firebase authentication finishes.
            publishConnectedStatusIfEligible()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Android may reconnect the same service instance after a settings toggle.
        // Force a fresh server confirmation for the current pairing.
        lastStatusCodeReported = null
        observedPrefs = getSharedPreferences("parent_audio", MODE_PRIVATE).also {
            it.registerOnSharedPreferenceChangeListener(preferenceListener)
        }
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        publishConnectedStatusIfEligible()
    }

    override fun onListenerDisconnected() {
        updateListenerConnectionStatus(false)
        observedPrefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        observedPrefs = null
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        observedPrefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        observedPrefs = null
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        super.onDestroy()
    }

    private fun publishConnectedStatusIfEligible() {
        updateListenerConnectionStatus(true)
    }

    private fun updateListenerConnectionStatus(connected: Boolean, force: Boolean = false) {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child") return
        if (connected && !prefs.getBoolean("child_notification_monitoring_enabled", false)) return
        val code = prefs.getString("pair_code", null)?.takeIf { it.isNotBlank() } ?: return

        val firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser == null) {
            Log.d(TAG, "Waiting for Firebase authentication before publishing listener status")
            return
        }
        if (connected && lastStatusCodeReported == code && !force) return

        FirebaseFirestore.getInstance()
            .collection("pairings")
            .document(code)
            .update(
                mapOf(
                    "notificationListenerConnected" to connected,
                    "notificationListenerUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                lastStatusCodeReported = if (connected) code else null
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to publish notification listener status", error)
            }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child" ||
            !prefs.getBoolean("child_notification_monitoring_enabled", false)
        ) return

        val code = prefs.getString("pair_code", null)?.takeIf { it.isNotBlank() } ?: return
        // Any real notification proves the listener is active. Refresh the server status
        // even when this service instance previously reported the same pairing code.
        updateListenerConnectionStatus(true, force = true)
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

            // Hide only WhatsApp's aggregate/count-only notifications.
            // Actual message previews and notifications from other apps remain untouched.
            if (isWhatsAppSummary(packageName, sbn, body) ||
                isInputMethodPrompt(title, body)
            ) return

            val eventId = UUID.nameUUIDFromBytes(
                (packageName + "|" + sbn.key + if (isMtManagerProgress(packageName, sbn)) "" else "|" + sbn.postTime).toByteArray(Charsets.UTF_8)
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

    private fun isInputMethodPrompt(title: String, body: String): Boolean {
        val text = (title + " " + body).lowercase(java.util.Locale.ROOT)
        return text.contains("pilih metode masukan") ||
            text.contains("choose input method") ||
            text.contains("select input method")
    }

    private fun isMtManagerProgress(
        packageName: String,
        sbn: StatusBarNotification
    ): Boolean {
        if (packageName != MT_MANAGER_PACKAGE) return false
        // FTP progress notifications are updated in place by Android. Use a stable
        // event ID for ongoing MT Manager notifications so updates replace, not stack.
        return (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
    }

    private fun isWhatsAppSummary(
        packageName: String,
        sbn: StatusBarNotification,
        body: String
    ): Boolean {
        val isWhatsApp = packageName == WHATSAPP_PACKAGE ||
            packageName == WHATSAPP_BUSINESS_PACKAGE
        if (!isWhatsApp) return false

        val isGroupSummary =
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return true

        return WHATSAPP_COUNT_SUMMARY.matches(body.trim())
    }

    companion object {
        private const val TAG = "SantetHPNotifications"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"\n        private const val MT_MANAGER_PACKAGE = "bin.mt.plus"
        private val WHATSAPP_COUNT_SUMMARY =
            Regex("^\\d+\\s+(?:pesan(?:\\s+baru)?|new\\s+messages?)$", RegexOption.IGNORE_CASE)
    }
}
