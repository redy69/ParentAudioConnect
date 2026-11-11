package com.santethp

import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Connects to LiveKit projects in the same deterministic order on both paired devices.
 * The Worker returns short-lived, room-scoped tokens; API secrets never leave Cloudflare.
 */
object LiveKitFailover {
    suspend fun connect(
        response: JSONObject,
        createRoom: () -> Room,
        prepareRoom: (Room) -> Unit = {}
    ): Room {
        val candidates = response.optJSONArray("servers") ?: JSONArray().apply {
            put(JSONObject()
                .put("token", response.optString("token"))
                .put("serverUrl", response.optString("serverUrl"))
                .put("id", response.optString("serverId", "main")))
        }
        var lastError: Exception? = null

        for (index in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(index) ?: continue
            val token = candidate.optString("token")
            val serverUrl = candidate.optString("serverUrl")
            if (token.isBlank() || serverUrl.isBlank()) continue

            val candidateRoom = createRoom()
            try {
                prepareRoom(candidateRoom)
                candidateRoom.connect(url = serverUrl, token = token)
                return candidateRoom
            } catch (error: Exception) {
                if (error is CancellationException) {
                    try { candidateRoom.disconnect() } catch (_: Exception) { }
                    try { candidateRoom.release() } catch (_: Exception) { }
                    throw error
                }
                lastError = error
                try { candidateRoom.disconnect() } catch (_: Exception) { }
                try { candidateRoom.release() } catch (_: Exception) { }
            }
        }

        throw IllegalStateException(
            "Semua server LiveKit gagal dihubungi (${candidates.length()} server)",
            lastError
        )
    }
}
