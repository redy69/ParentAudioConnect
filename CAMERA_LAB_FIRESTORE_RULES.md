# Camera Lab Firestore permissions

Camera Lab uses the separate `cameraLabPairings` collection. Do not replace the project's existing Firestore rules with this snippet. Merge this `match` block inside the existing top-level `service cloud.firestore { match /databases/{database}/documents { ... } }` rules, alongside existing SantetHP rules.

```firestore
match /cameraLabPairings/{code} {
  // Only the authenticated sender may create a new, unclaimed pairing.
  allow create: if request.auth != null
    && request.resource.data.keys().hasOnly([
      'parentUid', 'childUid', 'createdAt', 'expiresAt', 'mediaMode'
    ])
    && request.resource.data.parentUid == request.auth.uid
    && request.resource.data.childUid == null
    && request.resource.data.createdAt is timestamp
    && request.resource.data.expiresAt is timestamp
    && request.resource.data.expiresAt > request.time
    && request.resource.data.expiresAt <= request.time + duration.value(6, 'm')
    && request.resource.data.mediaMode in ['CAMERA_AUDIO', 'AUDIO_ONLY'];

  // Sender and the one claimed receiver can read the pairing for token validation.
  allow get: if request.auth != null
    && (resource.data.parentUid == request.auth.uid
      || resource.data.childUid == request.auth.uid);

  // The receiver can claim once. No other field may be changed.
  allow update: if request.auth != null
    && resource.data.childUid == null
    && resource.data.parentUid != request.auth.uid
    && request.resource.data.childUid == request.auth.uid
    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['childUid'])
    && resource.data.expiresAt > request.time;

  allow list, delete: if false;
}
```

## Publish steps

1. Open Firebase Console → project `parentaudioconnect` → Firestore Database → Rules.
2. Keep all existing rules. Insert the `match /cameraLabPairings/{code}` block inside the existing `match /databases/{database}/documents` block.
3. Publish the rules.
4. Deploy the updated Worker source `worker/parent-audio-token.js` to the existing `parent-audio-token` Worker. It now reads from `cameraLabPairings` for the Camera Lab token route.

The Android app and Worker must both use `cameraLabPairings`. These rules do not grant access to the existing `pairings` collection and do not change SantetHP production pairing permissions.
