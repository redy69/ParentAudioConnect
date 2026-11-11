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
    @Volatile private var blockedPackages: Set<String> = emptySet()
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt = 0L

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
            blockedPackages = emptySet()
            return
        }
        if (boundPairingCode == code && pairingListener != null) return

        pairingListener?.remove()
        pairingListener = null
        boundPairingCode = code
        blockedPackages = emptySet()
        pairingListener = FirebaseFirestore.getInstance()
            .collection("pairings").document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    blockedPackages = emptySet()
                    return@addSnapshotListener
                }
                val childConsent = getSharedPreferences("parent_audio", MODE_PRIVATE)
                    .getBoolean("child_app_block_consent", false)
                if (!childConsent || snapshot.getBoolean("appBlockConsent") != true) {
                    blockedPackages = emptySet()
                    return@addSnapshotListener
                }
                blockedPackages = (snapshot.get("blockedApps") as? List<*>)
                    ?.filterIsInstance<String>()?.toSet().orEmpty()
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        bindToCurrentPairing()
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child" ||
            !prefs.getBoolean("child_app_block_consent", false)) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName || packageName == "com.android.systemui") return
        if (packageName !in blockedPackages) return
        val now = System.currentTimeMillis()
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
        blockedPackages = emptySet()
        super.onDestroy()
    }
}
