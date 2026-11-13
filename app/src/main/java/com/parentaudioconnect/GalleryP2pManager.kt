package com.santethp

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SdpObserver
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direct peer-to-peer transport bootstrap for the paired SantetHP gallery.
 * Firestore carries SDP/ICE signaling only; media bytes use the DataChannel.
 * This milestone is STUN-only and never silently falls back to a relay.
 */
class GalleryP2pManager(
    context: Context,
    private val pairingCode: String,
    private val localRole: Role,
    private val onState: (String) -> Unit,
    private val onData: (ByteArray, Boolean) -> Unit
) {
    enum class Role { PARENT, CHILD }

    companion object {
        private const val TAG = "SantetHP-GalleryP2P"
        private const val MAX_SIGNAL_TEXT = 64 * 1024
        private const val MAX_DATA_FRAME_BYTES = 96 * 1024
        private const val MAX_BUFFERED_DATA_BYTES = 512L * 1024L
        private val initialized = AtomicBoolean(false)
    }

    private val appContext = context.applicationContext
    private val pairingDocument = FirebaseFirestore.getInstance()
        .collection("pairings").document(pairingCode)
    private val signals = pairingDocument.collection("gallerySignals")
    private val localUid = com.google.firebase.auth.FirebaseAuth.getInstance()
        .currentUser?.uid

    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var channel: DataChannel? = null
    private var signalListener: ListenerRegistration? = null
    private var pairingListener: ListenerRegistration? = null
    @Volatile private var activeSessionId: String? = null
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    @Volatile private var closed = false

    fun start() {
        if (closed) return
        if (localUid.isNullOrBlank()) {
            onState("Autentikasi Firebase belum siap")
            return
        }
        if (localRole == Role.PARENT) {
            val sessionId = UUID.randomUUID().toString()
            activeSessionId = sessionId
            onState("Membuka sesi galeri P2P yang baru…")
            pairingDocument.update("galleryP2pSessionId", sessionId)
                .addOnSuccessListener {
                    if (closed) return@addOnSuccessListener
                    try {
                        initializePeer()
                        listenForSignals()
                        onState("Menyiapkan koneksi P2P langsung…")
                        createOffer()
                    } catch (error: Exception) {
                        Log.e(TAG, "Gagal memulai P2P", error)
                        fail("Gagal memulai P2P: ${error.localizedMessage ?: error.javaClass.simpleName}")
                        close()
                    }
                }
                .addOnFailureListener { error ->
                    fail("Tidak dapat membuka sesi P2P: ${error.localizedMessage ?: "periksa koneksi"}")
                    close()
                }
        } else {
            try {
                initializePeer()
                listenForPairingSession()
                onState("Menunggu permintaan galeri dari HP orang tua…")
            } catch (error: Exception) {
                Log.e(TAG, "Gagal memulai P2P", error)
                fail("Gagal memulai P2P: ${error.localizedMessage ?: error.javaClass.simpleName}")
                close()
            }
        }
    }

    private fun initializePeer() {
        if (initialized.compareAndSet(false, true)) {
            val options = PeerConnectionFactory.InitializationOptions
                .builder(appContext).createInitializationOptions()
            PeerConnectionFactory.initialize(options)
        }
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val servers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer()
        )
        peer = factory?.createPeerConnection(PeerConnection.RTCConfiguration(servers), peerObserver)
        if (peer == null) throw IllegalStateException("Mesin P2P tidak dapat dibuat")
    }

    private fun listenForPairingSession() {
        pairingListener?.remove()
        pairingListener = pairingDocument.addSnapshotListener { snapshot, error ->
            if (closed) return@addSnapshotListener
            if (error != null) {
                fail("Status sesi galeri tidak dapat dibaca")
                return@addSnapshotListener
            }
            val sessionId = snapshot?.getString("galleryP2pSessionId")
            if (sessionId.isNullOrBlank() || sessionId == activeSessionId) return@addSnapshotListener
            activeSessionId = sessionId
            remoteDescriptionSet = false
            pendingRemoteCandidates.clear()
            signalListener?.remove()
            listenForSignals()
            onState("Permintaan sesi galeri diterima • menunggu offer P2P…")
        }
    }

    private fun listenForSignals() {
        val sessionId = activeSessionId ?: return
        signalListener?.remove()
        signalListener = signals.whereEqualTo("sessionId", sessionId)
            .addSnapshotListener { snapshot, error ->
                if (closed) return@addSnapshotListener
                if (error != null) {
                    Log.e(TAG, "Signal listener error", error)
                    fail("Sinyal koneksi gagal dibaca")
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.REMOVED) return@forEach
                    val data = change.document.data
                    if (data["senderUid"] == localUid || data["sessionId"] != activeSessionId) return@forEach
                    when (data["kind"] as? String) {
                        "offer" -> if (localRole == Role.CHILD) receiveOffer(data["sdp"] as? String)
                        "answer" -> if (localRole == Role.PARENT) receiveAnswer(data["sdp"] as? String)
                        "parent-ice" -> if (localRole == Role.CHILD) receiveCandidate(data)
                        "child-ice" -> if (localRole == Role.PARENT) receiveCandidate(data)
                    }
                }
            }
    }

    private fun createOffer() {
        val connection = peer ?: return
        channel = connection.createDataChannel("santethp-gallery-v1", DataChannel.Init())
        observeChannel(channel)
        connection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) return fail("Offer P2P kosong")
                connection.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { publishSignal("offer", description.description) }
                    override fun onSetFailure(error: String?) = fail("Gagal menyimpan offer: $error")
                    override fun onCreateSuccess(p0: SessionDescription?) = Unit
                    override fun onCreateFailure(p0: String?) = Unit
                }, description)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String?) = fail("Gagal membuat offer: $error")
            override fun onSetFailure(error: String?) = fail("Gagal membuat offer: $error")
        }, MediaConstraints())
    }

    private fun receiveOffer(sdp: String?) {
        if (sdp.isNullOrBlank() || sdp.length > MAX_SIGNAL_TEXT || peer == null) return
        onState("HP anak menerima permintaan koneksi P2P…")
        peer?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingCandidates()
                peer?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription?) {
                        if (description == null) return fail("Answer P2P kosong")
                        peer?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() { publishSignal("answer", description.description) }
                            override fun onSetFailure(error: String?) = fail("Gagal menyimpan answer: $error")
                            override fun onCreateSuccess(p0: SessionDescription?) = Unit
                            override fun onCreateFailure(p0: String?) = Unit
                        }, description)
                    }
                    override fun onSetSuccess() = Unit
                    override fun onCreateFailure(error: String?) = fail("Gagal membuat answer: $error")
                    override fun onSetFailure(error: String?) = fail("Gagal membuat answer: $error")
                }, MediaConstraints())
            }
            override fun onSetFailure(error: String?) = fail("Offer ditolak: $error")
            override fun onCreateSuccess(p0: SessionDescription?) = Unit
            override fun onCreateFailure(p0: String?) = Unit
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun receiveAnswer(sdp: String?) {
        if (sdp.isNullOrBlank() || sdp.length > MAX_SIGNAL_TEXT) return
        peer?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingCandidates()
                onState("Jawaban diterima • menyelesaikan koneksi P2P…")
            }
            override fun onSetFailure(error: String?) = fail("Answer ditolak: $error")
            override fun onCreateSuccess(p0: SessionDescription?) = Unit
            override fun onCreateFailure(p0: String?) = Unit
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    private fun receiveCandidate(data: Map<String, Any>) {
        val text = data["candidate"] as? String ?: return
        if (text.length > 4096) return
        val line = (data["sdpMLineIndex"] as? Number)?.toInt() ?: return
        val candidate = IceCandidate(data["sdpMid"] as? String, line, text)
        if (remoteDescriptionSet) peer?.addIceCandidate(candidate)
        else pendingRemoteCandidates.add(candidate)
    }

    private fun flushPendingCandidates() {
        pendingRemoteCandidates.toList().forEach { peer?.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    private fun publishSignal(kind: String, sdp: String) {
        if (closed || sdp.length > MAX_SIGNAL_TEXT) return
        signals.document(UUID.randomUUID().toString()).set(
            mapOf("kind" to kind, "senderUid" to localUid, "sessionId" to activeSessionId,
                "sdp" to sdp, "createdAt" to FieldValue.serverTimestamp())
        ).addOnFailureListener { fail("Gagal mengirim sinyal $kind") }
    }

    private fun publishCandidate(candidate: IceCandidate) {
        if (closed) return
        val kind = if (localRole == Role.PARENT) "parent-ice" else "child-ice"
        signals.document(UUID.randomUUID().toString()).set(
            mapOf("kind" to kind, "senderUid" to localUid, "sessionId" to activeSessionId,
                "candidate" to candidate.sdp, "sdpMid" to (candidate.sdpMid ?: ""),
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "createdAt" to FieldValue.serverTimestamp())
        ).addOnFailureListener { Log.w(TAG, "ICE candidate gagal dikirim", it) }
    }

    private fun observeChannel(dataChannel: DataChannel?) {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                when (dataChannel.state()) {
                    DataChannel.State.OPEN -> onState("P2P TERHUBUNG • jalur data langsung aktif")
                    DataChannel.State.CLOSED -> onState("Koneksi galeri P2P ditutup")
                    else -> Unit
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                onData(bytes, buffer.binary)
            }
        })
    }

    /** Gallery protocol must chunk files and respect bufferedAmount before sending. */
    fun send(bytes: ByteArray, binary: Boolean = true): Boolean {
        val active = channel ?: return false
        if (closed || active.state() != DataChannel.State.OPEN || bytes.isEmpty() ||
            bytes.size > MAX_DATA_FRAME_BYTES ||
            active.bufferedAmount() > MAX_BUFFERED_DATA_BYTES
        ) return false
        return try { active.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), binary)) }
        catch (error: Exception) {
            Log.e(TAG, "Gagal mengirim frame P2P", error)
            false
        }
    }

    private val peerObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED ->
                    onState("P2P TERHUBUNG • jalur langsung aktif")
                PeerConnection.IceConnectionState.FAILED ->
                    onState("P2P gagal pada jaringan ini • belum memakai relay/TURN")
                PeerConnection.IceConnectionState.DISCONNECTED ->
                    onState("P2P terputus • menunggu jaringan kembali")
                PeerConnection.IceConnectionState.CHECKING ->
                    onState("Mencari jalur P2P langsung…")
                else -> Unit
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidate(candidate: IceCandidate?) { candidate?.let(::publishCandidate) }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
        override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
        override fun onDataChannel(dataChannel: DataChannel?) {
            channel = dataChannel
            observeChannel(dataChannel)
        }
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(
            receiver: org.webrtc.RtpReceiver?,
            mediaStreams: Array<out org.webrtc.MediaStream>?
        ) = Unit
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) = Unit
    }

    private fun fail(message: String) {
        if (!closed) {
            Log.w(TAG, message)
            onState(message)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        signalListener?.remove()
        signalListener = null
        pairingListener?.remove()
        pairingListener = null
        if (localRole == Role.PARENT && !activeSessionId.isNullOrBlank()) {
            pairingDocument.update("galleryP2pSessionId", FieldValue.delete())
        }
        activeSessionId = null
        channel?.unregisterObserver()
        channel?.close()
        channel?.dispose()
        channel = null
        peer?.close()
        peer?.dispose()
        peer = null
        factory?.dispose()
        factory = null
        pendingRemoteCandidates.clear()
        onState("Koneksi galeri P2P dihentikan")
    }
}
