package com.santethp

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/** Requests user-approved access to files created by SantetHP in the selected parent's Drive. */
class GoogleDriveAuthorization(private val activity: Activity) {
    fun interface Callback { fun onResult(accessToken: String?, error: String?) }
    private var pendingCallback: Callback? = null
    companion object {
        const val REQUEST_CODE = 7419
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
    fun authorize(callback: Callback) {
        if (pendingCallback != null) {
            callback.onResult(null, "Permintaan izin Google Drive sebelumnya masih berjalan.")
            return
        }
        pendingCallback = callback
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { result -> handleAuthorizationResult(result) }
            .addOnFailureListener { error ->
                finish(null, error.localizedMessage ?: "Izin Google Drive gagal diminta.")
            }
    }
    private fun handleAuthorizationResult(result: AuthorizationResult) {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
            if (pendingIntent == null) { finish(null, "Google tidak memberikan layar persetujuan."); return }
            try { activity.startIntentSenderForResult(pendingIntent.intentSender, REQUEST_CODE, null, 0, 0, 0) }
            catch (error: Exception) { finish(null, error.localizedMessage ?: "Layar izin Google Drive tidak dapat dibuka.") }
        } else {
            val token = result.accessToken
            if (token.isNullOrBlank()) finish(null, "Google tidak mengembalikan token akses Drive.")
            else finish(token, null)
        }
    }
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        if (resultCode != Activity.RESULT_OK || data == null) { finish(null, "Persetujuan Google Drive dibatalkan."); return true }
        try {
            val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token.isNullOrBlank()) finish(null, "Google tidak mengembalikan token akses Drive.")
            else finish(token, null)
        } catch (error: Exception) {
            finish(null, error.localizedMessage ?: "Tidak dapat membaca hasil izin Google Drive.")
        }
        return true
    }
    private fun finish(accessToken: String?, error: String?) {
        val callback = pendingCallback
        pendingCallback = null
        callback?.onResult(accessToken, error)
    }
}
