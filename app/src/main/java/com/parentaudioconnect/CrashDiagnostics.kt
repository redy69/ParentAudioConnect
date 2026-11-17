package com.santethp

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a small local record of fatal app errors so they can be shared after
 * SantetHP is opened again. It does not upload logs automatically.
 */
object CrashDiagnostics {
    private const val TAG = "SantetHPCrash"
    private const val REPORT_FILE = "santethp_crash_report.txt"
    private const val MAX_REPORT_BYTES = 256 * 1024
    private const val PREFS = "parent_audio"
    private const val LAST_EXIT_TIME_KEY = "crash_diag_last_exit_timestamp"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val role = prefs.getString("pair_role", "belum dipilih") ?: "belum dipilih"
                    val activity = prefs.getString("crash_diag_last_activity", "tidak diketahui")
                        ?: "tidak diketahui"
                    val stack = StringWriter().also { writer ->
                        throwable.printStackTrace(PrintWriter(writer))
                    }.toString()
                    val time = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS",
                        Locale.getDefault()
                    ).format(Date())
                    val version = try {
                        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                            ?: "tidak diketahui"
                    } catch (_: Exception) {
                        "tidak diketahui"
                    }
                    append(
                        appContext,
                        """
                        === FATAL APP ERROR ===
                        Waktu: $time
                        Versi SantetHP: $version
                        Perangkat: \${Build.MANUFACTURER} \${Build.MODEL}
                        Android API: \${Build.VERSION.SDK_INT}
                        Peran perangkat: $role
                        Layar terakhir: $activity
                        Thread: \${thread.name}
                        Penyebab:
                        $stack
                        """.trimIndent()
                    )
                    prefs.edit()
                        .putLong(LAST_EXIT_TIME_KEY, System.currentTimeMillis())
                        .apply()
                } catch (loggingError: Throwable) {
                    Log.e(TAG, "Tidak dapat menyimpan catatan crash", loggingError)
                }

                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }

            (appContext as? Application)?.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                    override fun onActivityStarted(activity: Activity) = Unit
                    override fun onActivityPaused(activity: Activity) = Unit
                    override fun onActivityStopped(activity: Activity) = Unit
                    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                    override fun onActivityDestroyed(activity: Activity) = Unit

                    override fun onActivityResumed(activity: Activity) {
                        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString("crash_diag_last_activity", activity.javaClass.simpleName)
                            .apply()
                    }
                }
            )
            installed = true
        }
    }

    /** Adds Android's own exit reason (for example crash, ANR, or low memory). */
    fun collectHistoricalExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastRecorded = prefs.getLong(LAST_EXIT_TIME_KEY, 0L)
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            val relevantReasons = setOf(
                android.app.ApplicationExitInfo.REASON_CRASH,
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                android.app.ApplicationExitInfo.REASON_ANR,
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
                android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED
            )

            exits.filter { it.timestamp > lastRecorded && it.reason in relevantReasons }
                .sortedBy { it.timestamp }
                .forEach { info ->
                    val trace = try {
                        info.traceInputStream?.use { input ->
                            input.bufferedReader().readText().takeLast(40_000)
                        }.orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    val time = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date(info.timestamp))
                    append(
                        context,
                        """
                        === ANDROID PROCESS EXIT ===
                        Waktu: $time
                        Perangkat: \${Build.MANUFACTURER} \${Build.MODEL}
                        Android API: \${Build.VERSION.SDK_INT}
                        Alasan: \${reasonLabel(info.reason)}
                        Keterangan: \${info.description ?: "tidak tersedia"}
                        Proses: \${info.processName}
                        Status: \${info.status}
                        Kepentingan proses: \${info.importance}
                        Jejak sistem:
                        $trace
                        """.trimIndent()
                    )
                    prefs.edit().putLong(LAST_EXIT_TIME_KEY, info.timestamp).apply()
                }

            val newestExit = exits.maxOfOrNull { it.timestamp } ?: return
            if (newestExit > prefs.getLong(LAST_EXIT_TIME_KEY, 0L)) {
                prefs.edit().putLong(LAST_EXIT_TIME_KEY, newestExit).apply()
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Tidak dapat membaca riwayat penghentian proses Android", error)
        }
    }

    fun readReport(context: Context): String? {
        val file = reportFile(context)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            file.readText(Charsets.UTF_8).takeLast(100_000)
        } catch (error: Exception) {
            Log.w(TAG, "Tidak dapat membaca catatan crash", error)
            null
        }
    }

    fun reportLastModified(context: Context): Long = reportFile(context).lastModified()

    private fun reportFile(context: Context): File = File(context.filesDir, REPORT_FILE)

    private fun append(context: Context, section: String) {
        synchronized(this) {
            val file = reportFile(context)
            file.appendText(section + "\n\n", Charsets.UTF_8)
            if (file.length() > MAX_REPORT_BYTES) {
                val tail = file.readText(Charsets.UTF_8).takeLast(MAX_REPORT_BYTES / 2)
                file.writeText(tail, Charsets.UTF_8)
            }
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        android.app.ApplicationExitInfo.REASON_CRASH -> "Aplikasi crash"
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Crash native"
        android.app.ApplicationExitInfo.REASON_ANR -> "Aplikasi tidak merespons (ANR)"
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Dihentikan karena memori rendah"
        android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Gagal saat inisialisasi"
        android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "Proses dependensi berhenti"
        else -> "Alasan Android $reason"
    }
}
