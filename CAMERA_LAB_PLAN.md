# SantetHP Camera Lab

This is an isolated Android application module for controlled media-path comparisons. It is not the production SantetHP app and must not replace or modify it.

## Application identity
- Label: SantetHP Camera
- Application ID: `com.santethp.cameralab`
- Module: `:cameraLab`
- Branch: `feature/santethp-camera-lab`

## Scope
- Live Camera with microphone audio in the same session.
- Audio-only mode.
- Selectable LiveKit SFU and WebRTC P2P test paths.
- Explicit camera/microphone permission and user-initiated sessions.
- Later diagnostics: connection state, selected ICE candidate type, RTT, packet loss, jitter, decoded/rendered FPS, bitrate and reconnect count.

## Stages
1. App shell, isolated package, mode selection and permission flow.
2. LiveKit media session with camera + microphone and audio-only mode.
3. WebRTC P2P media session with signaling and STUN/TURN support.
4. Diagnostics and repeatable A/B test procedure on the same devices and networks.

The first stage intentionally does not capture, transmit, or stream camera/microphone media. No signaling credentials or TURN secrets are committed to the repository.
