package com.santethp

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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

    fun refreshLiveAudioHeartbeat(context: Context) {
        heartbeat(context, "liveAudioActivityAt")
    }

    fun refreshMp3AudioHeartbeat(context: Context) {
        heartbeat(context, "mp3AudioActivityAt")
    }

    private fun heartbeat(context: Context, timestampField: String) {
        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        val role = prefs.getString("pair_role", null)
        if (code.isNullOrBlank() || role != "child") return

        FirebaseFirestore.getInstance()
            .collection("pairings")
            .document(code)
            .update(
                mapOf(
                    timestampField to FieldValue.serverTimestamp(),
                    "audioActivityAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to refresh $timestampField for pairing $code", error)
            }
    }

    private fun update(context: Context, field: String, active: Boolean) {
        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        val role = prefs.getString("pair_role", null)
        if (code.isNullOrBlank() || role != "child") {
            Log.w(TAG, "Playback state not sent: child pairing is missing")
            return
        }

        val timestampField = if (field == "liveAudioActive") {
            "liveAudioActivityAt"
        } else {
            "mp3AudioActivityAt"
        }

        // Each audio type has its own heartbeat so a stale flag can expire independently.
        FirebaseFirestore.getInstance()
            .collection("pairings")
            .document(code)
            .set(
                mapOf(
                    field to active,
                    timestampField to FieldValue.serverTimestamp(),
                    "audioActivityAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                Log.d(TAG, "Published $field=$active")
            }
            .addOnFailureListener { error ->
                // A PERMISSION_DENIED here means the deployed Firestore rules
                // must allow the paired child to update these two status fields.
                Log.e(TAG, "Unable to publish $field=$active for pairing $code", error)
            }
    }
}
