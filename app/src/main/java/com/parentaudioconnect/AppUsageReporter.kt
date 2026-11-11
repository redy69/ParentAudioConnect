package com.santethp

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
            val auditEnabled = prefs.getBoolean("child_usage_audit_enabled", false)
            val usageAccessGranted = auditEnabled && hasUsageAccess(context)
            if (!usageAccessGranted) {
                withContext(Dispatchers.IO) {
                    Tasks.await(
                        FirebaseFirestore.getInstance().collection("pairings").document(code)
                            .update(
                                mapOf(
                                    "appUsageEnabled" to auditEnabled,
                                    "appUsagePermissionGranted" to false,
                                    "appUsageSummary" to FieldValue.delete(),
                                    "appUsageAt" to FieldValue.serverTimestamp()
                                )
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

            var summary = usage
                .asSequence()
                .filter { it.totalTimeInForeground >= 60_000L }
                .filter { it.packageName != context.packageName }
                .groupBy { it.packageName }
                .map { (packageName, records) ->
                    val duration = records.sumOf { it.totalTimeInForeground }
                    val appName = resolveAppName(context, packageName)
                    mapOf(
                        "packageName" to packageName,
                        "appName" to appName,
                        "foregroundMinutes" to (duration / 60_000L).toInt()
                    )
                }
                .sortedByDescending { it["foregroundMinutes"] as Int }
                .take(12)

            // Some Android builds return empty/incomplete daily aggregates even when
            // Usage Access is granted. Fall back to foreground/background events.
            if (summary.isEmpty()) {
                summary = queryEventSummary(context, manager, now)
                Log.i(TAG, "Daily usage aggregate empty; event fallback returned ${summary.size} apps")
            } else {
                Log.i(TAG, "Daily usage aggregate returned ${summary.size} apps")
            }

            withContext(Dispatchers.IO) {
                Tasks.await(
                    FirebaseFirestore.getInstance()
                        .collection("pairings")
                        .document(code)
                        .update(
                            mapOf(
                                "appUsageSummary" to summary,
                                "appUsageEnabled" to true,
                                "appUsagePermissionGranted" to true,
                                "appUsageAt" to FieldValue.serverTimestamp()
                            ))
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to publish app usage summary", error)
            throw error
        }
    }
    private fun resolveAppName(context: Context, packageName: String): String {
        return try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun queryEventSummary(
        context: Context,
        manager: UsageStatsManager,
        now: Long
    ): List<Map<String, Any>> {
        val start = now - DAY_MS
        val events = manager.queryEvents(start, now)
        val event = android.app.usage.UsageEvents.Event()
        val totals = mutableMapOf<String, Long>()
        val foregroundSince = mutableMapOf<String, Long>()

        fun closeForeground(packageName: String, at: Long) {
            val began = foregroundSince.remove(packageName) ?: return
            val duration = (at - began).coerceAtLeast(0L)
            totals[packageName] = (totals[packageName] ?: 0L) + duration
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            if (packageName == context.packageName) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val previous = foregroundSince.keys.firstOrNull { it != packageName }
                    if (previous != null) closeForeground(previous, event.timeStamp)
                    if (packageName !in foregroundSince) {
                        foregroundSince[packageName] = event.timeStamp
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> closeForeground(packageName, event.timeStamp)
            }
        }

        foregroundSince.keys.toList().forEach { closeForeground(it, now) }
        return totals
            .filterValues { it >= 60_000L }
            .map { (packageName, duration) ->
                mapOf(
                    "packageName" to packageName,
                    "appName" to resolveAppName(context, packageName),
                    "foregroundMinutes" to (duration / 60_000L).toInt()
                )
            }
            .sortedByDescending { it["foregroundMinutes"] as Int }
            .take(12)
    }

}
