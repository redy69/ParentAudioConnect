package com.santethp

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shares app names and foreground duration only, after the child enables Usage Access. */
object AppUsageReporter {
    private const val TAG = "AppUsageReporter"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun publish(context: Context) {
        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        if (code.isNullOrBlank() || prefs.getString("pair_role", null) != "child") return

        try {
            if (!hasUsageAccess(context)) {
                withContext(Dispatchers.IO) {
                    Tasks.await(
                        FirebaseFirestore.getInstance().collection("pairings").document(code)
                            .set(
                                mapOf("appUsagePermissionGranted" to false, "appUsageAt" to FieldValue.serverTimestamp()),
                                SetOptions.merge()
                            )
                    )
                }
                return
            }
            val now = System.currentTimeMillis()
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usage = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - DAY_MS,
                now
            ).orEmpty()

            val summary = usage
                .asSequence()
                .filter { it.totalTimeInForeground >= 60_000L }
                .filter { it.packageName != context.packageName }
                .groupBy { it.packageName }
                .map { (packageName, records) ->
                    val duration = records.sumOf { it.totalTimeInForeground }
                    val appName = try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(packageName, 0)
                        ).toString()
                    } catch (_: Exception) {
                        packageName
                    }
                    mapOf(
                        "packageName" to packageName,
                        "appName" to appName,
                        "foregroundMinutes" to (duration / 60_000L).toInt()
                    )
                }
                .sortedByDescending { it["foregroundMinutes"] as Int }
                .take(12)

            withContext(Dispatchers.IO) {
                Tasks.await(
                    FirebaseFirestore.getInstance()
                        .collection("pairings")
                        .document(code)
                        .set(
                            mapOf(
                                "appUsageSummary" to summary,
                                "appUsagePermissionGranted" to true,
                                "appUsageAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to publish app usage summary", error)
            throw error
        }
    }
}
