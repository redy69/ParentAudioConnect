package com.santethp

import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

data class LiveKitConnection(
    val room: Room,
    val serverId: String
)

/**
 * Tries the preferred paired server first, followed by every configured fallback
 * in the order returned by the Worker. The Worker keeps this order identical for
 * both paired devices; a saved preference must not suppress fallback candidates.
 */
object LiveKitFailover {
    suspend fun connect(
        response: JSONObject,
        createRoom: () -> Room,
        prepareRoom: (Room) -> Unit = {}
    ): Room = connectWithServer(response, createRoom, prepareRoom).room

    suspend fun connectWithServer(
        response: JSONObject,
        createRoom: () -> Room,
        prepareRoom: (Room) -> Unit = {}
    ): LiveKitConnection {
        val candidates = response.optJSONArray("servers") ?: JSONArray().apply {
            put(JSONObject()
                .put("token", response.optString("token"))
                .put("serverUrl", response.optString("serverUrl"))
                .put("id", response.optString("serverId", "main")))
        }
        val preferred = response.optString("preferredServerId")
        var lastError: Exception? = null

        for (index in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(index) ?: continue
            val id = candidate.optString("id")
            val token = candidate.optString("token")
            val serverUrl = candidate.optString("serverUrl")
            if (token.isBlank() || serverUrl.isBlank()) continue

            val candidateRoom = createRoom()
            try {
                prepareRoom(candidateRoom)
                candidateRoom.connect(url = serverUrl, token = token)
                return LiveKitConnection(candidateRoom, id)
            } catch (error: Exception) {
                if (error is CancellationException) {
                    try { candidateRoom.disconnect() } catch (_: Exception) { }
                    try { candidateRoom.release() } catch (_: Exception) { }
                    throw error
                }
                try { candidateRoom.disconnect() } catch (_: Exception) { }
                try { candidateRoom.release() } catch (_: Exception) { }
                lastError = error
            }
        }

        throw IllegalStateException(
            "Semua server LiveKit gagal dihubungi (${candidates.length()} server)",
            if (preferred.isNotBlank()) null else lastError
        )
    }
}
