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

## Network test matrix

Record each run separately; do not infer a carrier-wide block from one failed attempt.

| Network path | Current observation | Camera Lab check |
|---|---|---|
| Wi-Fi | SantetHP Live Camera recently ran smoothly for about five minutes in one test; earlier tests were intermittent. | Repeat camera + microphone and audio-only runs; record Wi-Fi type and stability. |
| Smartfren cellular | Device status/battery updates were visible, but media did not work in the latest test. Earlier Smartfren-to-Smartfren use had worked. | Separate token request, LiveKit connect, publish, subscribe and render outcomes. |
| XL cellular | Media-path failures have also been reported; earlier Worker access showed carrier interception/block-page symptoms. | Check Worker/token reachability separately from LiveKit WebSocket/media transport; compare VPN off/on only if useful. |

For every attempt capture: timestamp, network/operator, transport, mode, token HTTP result and latency, server ID, WebSocket/room state, publish/subscribe state, first-frame time, rendered FPS, reconnect count and sanitized error. Never record ID tokens, LiveKit tokens, API secrets or TURN credentials.

## Isolation and safety
- Keep the Camera Lab package and pairing codes separate from the production SantetHP app.
- Use only explicit, visible sessions started and stopped by the user on the relevant phone.
- The production SantetHP app and its existing pairings remain untouched.
