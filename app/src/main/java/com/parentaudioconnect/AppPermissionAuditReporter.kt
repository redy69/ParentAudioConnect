package com.santethp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shares a limited, consent-based summary of sensitive permissions for launcher apps. */
object AppPermissionAuditReporter {
    private const val TAG = "AppPermissionAudit"
    private const val PREFS = "parent_audio"

    private val permissionLabels = linkedMapOf(
        "android.permission.CAMERA" to "Kamera",
        "android.permission.RECORD_AUDIO" to "Mikrofon",
        "android.permission.ACCESS_FINE_LOCATION" to "Lokasi presisi",
        "android.permission.ACCESS_COARSE_LOCATION" to "Lokasi perkiraan",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "Lokasi latar belakang",
        "android.permission.POST_NOTIFICATIONS" to "Notifikasi"
    )

    suspend fun publish(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        if (prefs.getString("pair_role", null) != "child" || code.isNullOrBlank()) return
        val document = FirebaseFirestore.getInstance().collection("pairings").document(code)

        if (!prefs.getBoolean("child_permission_audit_enabled", false)) {
            withContext(Dispatchers.IO) {
                Tasks.await(document.update(
                    mapOf(
                        "appPermissionAuditEnabled" to false,
                        "appPermissionAuditSummary" to emptyList<Map<String, Any>>(),
                        "appPermissionAuditAt" to FieldValue.serverTimestamp()
                    )))
            }
            return
        }

        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchables = pm.queryIntentActivities(intent, 0)
            val seen = mutableSetOf<String>()
            val summary = launchables.mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (!seen.add(packageName) || packageName == context.packageName) return@mapNotNull null
                val packageInfo = try {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                val requested = packageInfo.requestedPermissions ?: return@mapNotNull null
                val flags = packageInfo.requestedPermissionsFlags ?: IntArray(requested.size)
                val granted = mutableListOf<String>()
                val notGranted = mutableListOf<String>()
                requested.forEachIndexed { index, permission ->
                    val label = permissionLabels[permission] ?: return@forEachIndexed
                    if (permission == "android.permission.POST_NOTIFICATIONS" &&
                        Build.VERSION.SDK_INT < 33
                    ) return@forEachIndexed
                    val isGranted = index < flags.size &&
                        (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (isGranted) granted.add(label) else notGranted.add(label)
                }
                if (granted.isEmpty() && notGranted.isEmpty()) return@mapNotNull null
                val appName = try {
                    pm.getApplicationLabel(packageInfo.applicationInfo!!).toString()
                } catch (_: Exception) {
                    packageName
                }
                mapOf(
                    "packageName" to packageName,
                    "appName" to appName,
                    "granted" to granted,
                    "notGranted" to notGranted
                )
            }.sortedBy { it["appName"] as? String ?: "" }.take(30)

            withContext(Dispatchers.IO) {
                Tasks.await(document.update(
                    mapOf(
                        "appPermissionAuditEnabled" to true,
                        "appPermissionAuditSummary" to summary,
                        "appPermissionAuditAt" to FieldValue.serverTimestamp()
                    )))
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to publish permission audit", error)
            throw error
        }
    }
}
