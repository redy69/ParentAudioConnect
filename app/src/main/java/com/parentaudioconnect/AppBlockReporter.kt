package com.santethp

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Publishes launcher app names only after explicit child opt-in. */
object AppBlockReporter {
    private const val TAG = "AppBlockReporter"

    suspend fun publish(context: Context) {
        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        if (prefs.getString("pair_role", null) != "child" || code.isNullOrBlank()) return
        val enabled = prefs.getBoolean("child_app_block_consent", false)
        val doc = FirebaseFirestore.getInstance().collection("pairings").document(code)
        if (!enabled) {
            withContext(Dispatchers.IO) {
                Tasks.await(doc.set(mapOf(
                    "appBlockConsent" to false,
                    "appBlockInstalledApps" to emptyList<Map<String, String>>(),
                    "blockedApps" to emptyList<String>(),
                    "appBlockUpdatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge()))
            }
            return
        }
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it != context.packageName }
                .distinct()
                .map { pkg ->
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { pkg }
                    mapOf("packageName" to pkg, "appName" to label)
                }
                .sortedBy { it["appName"]?.lowercase() ?: "" }
                .take(100)
            withContext(Dispatchers.IO) {
                Tasks.await(doc.set(mapOf(
                    "appBlockConsent" to true,
                    "appBlockInstalledApps" to apps,
                    "appBlockUpdatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge()))
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to publish launcher app list", error)
            throw error
        }
    }
}
