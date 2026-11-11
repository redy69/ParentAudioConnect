package com.santethp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Opt-in enforcement: reads only the foreground package name and never retrieves window content.
 */
class ChildAppBlockAccessibilityService : AccessibilityService() {
    private var pairingListener: ListenerRegistration? = null
    private var boundPairingCode: String? = null
    @Volatile private var blockedUntilByPackage: Map<String, Long> = emptyMap()
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt = 0L
    private var lastReportedActivityPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        bindToCurrentPairing()
    }

    /**
     * Rebind when the child unpairs or pairs with a different parent while
     * Android keeps this accessibility service enabled across sessions.
     */
    private fun bindToCurrentPairing() {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
            ?.takeIf { prefs.getString("pair_role", null) == "child" && it.isNotBlank() }

        if (code == null) {
            pairingListener?.remove()
            pairingListener = null
            boundPairingCode = null
            blockedUntilByPackage = emptyMap()
            lastReportedActivityPackage = null
            return
        }
        if (boundPairingCode == code && pairingListener != null) return

        pairingListener?.remove()
        pairingListener = null
        boundPairingCode = code
        blockedUntilByPackage = emptyMap()
        lastReportedActivityPackage = null
        pairingListener = FirebaseFirestore.getInstance()
            .collection("pairings").document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    blockedUntilByPackage = emptyMap()
                    return@addSnapshotListener
                }
                val childConsent = getSharedPreferences("parent_audio", MODE_PRIVATE)
                    .getBoolean("child_app_block_consent", false)
                if (!childConsent || snapshot.getBoolean("appBlockConsent") != true) {
                    blockedUntilByPackage = emptyMap()
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                blockedUntilByPackage = (snapshot.get("blockedApps") as? List<*>)
                    .orEmpty().mapNotNull { raw ->
                        val rule = raw as? Map<*, *> ?: return@mapNotNull null
                        val pkg = rule["packageName"] as? String ?: return@mapNotNull null
                        val until = (rule["blockedUntilMillis"] as? Number)?.toLong()
                            ?: return@mapNotNull null
                        if (until > now) pkg to until else null
                    }.toMap()
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        bindToCurrentPairing()
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child") return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName || packageName == "com.android.systemui") return

        // Separate, explicit child consent: report only the foreground app identity,
        // never window text, screenshots, keystrokes, or accessibility node contents.
        val activityConsent = prefs.getBoolean("child_app_activity_consent", false)
        val code = boundPairingCode
        if (activityConsent && !code.isNullOrBlank() &&
            packageName != lastReportedActivityPackage
        ) {
            lastReportedActivityPackage = packageName
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (_: Exception) {
                packageName
            }
            FirebaseFirestore.getInstance().collection("pairings").document(code)
                .update(
                    mapOf(
                        "appActivityConsent" to true,
                        "appActivityPackage" to packageName,
                        "appActivityName" to appName,
                        "appActivityAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                .addOnFailureListener {
                    android.util.Log.w("ChildAppActivity", "Unable to publish foreground app", it)
                }
        }

        // App blocking remains independently controlled by its own child consent.
        if (!prefs.getBoolean("child_app_block_consent", false)) return
        val now = System.currentTimeMillis()
        val blockedUntil = blockedUntilByPackage[packageName] ?: return
        if (blockedUntil <= now) return
        if (packageName == lastBlockedPackage && now - lastBlockedAt < 1200L) return
        lastBlockedPackage = packageName
        lastBlockedAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, "Aplikasi ini dibatasi oleh pengaturan orang tua.", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pairingListener?.remove()
        pairingListener = null
        boundPairingCode = null
        blockedUntilByPackage = emptyMap()
        lastReportedActivityPackage = null
        super.onDestroy()
    }
}
