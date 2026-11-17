# Remote Camera Worker Deployment

This Worker revision adds three camera operations while leaving the existing audio token endpoint intact.

## Deploy

Deploy `worker/parent-audio-token.js` to the existing Cloudflare Worker used by SantetHP:

`parent-audio-token.yudhis-r15.workers.dev`

Keep the existing Worker variables/secrets that the audio feature already uses:
- `FIREBASE_WEB_API_KEY`
- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`
- `LIVEKIT_URL`

Add one new **secret** (not a plain text variable):

- Name: `FCM_SERVICE_ACCOUNT_JSON`
- Value: the complete JSON content of a Firebase service-account key for project `parentaudioconnect`.

The service account must have permission to send Firebase Cloud Messaging messages (Firebase Cloud Messaging API enabled). Do not commit the JSON key to GitHub and do not paste it into chat.

## Endpoints

- `POST /camera-token`: issues a short-lived LiveKit token for the separate camera room.
- `POST /request-camera` with `action=start`: sends the start command to the paired child's registered FCM token.
- `POST /request-camera` with `action=switch` and `facing=front|back`: changes the active camera.
- `POST /request-camera` with `action=stop`: stops camera capture while leaving standby armed.

The child must first open SantetHP, grant Android camera permission, and enable **AKTIFKAN SIAGA KAMERA**. The camera is never started by a remote message unless that visible standby service is already armed and connected. Android's camera privacy indicator and SantetHP's ongoing notification remain visible.

## Important Android limitation

If Android or the device manufacturer kills the standby service, a later FCM message cannot bypass Android's background camera restrictions. The child must reopen SantetHP and enable standby again. The camera session automatically ends after ten minutes.
