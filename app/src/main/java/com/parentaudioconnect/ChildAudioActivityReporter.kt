package com.parentaudioconnect

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Publishes child-side playback state to the paired document so the parent app
 * can show what is currently playing. This reports playback state only;
 * it does not enable or access the child's microphone.
 */
object ChildAudioActivityReporter {
    private const val TAG = "ChildAudioActivity"

    fun setLiveAudioActive(context: Context, active: Boolean) {
        update(context, "liveAudioActive", active)
    }

    fun setMp3AudioActive(context: Context, active: Boolean) {
        update(context, "mp3AudioActive", active)
    }

    private fun update(context: Context, field: String, active: Boolean) {
        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        val role = prefs.getString("pair_role", null)
        if (code.isNullOrBlank() || role != "child") return

        FirebaseFirestore.getInstance()
            .collection("pairings")
            .document(code)
            .update(
                mapOf(
                    field to active,
                    "audioActivityAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to publish playback state", error)
            }
    }
}
