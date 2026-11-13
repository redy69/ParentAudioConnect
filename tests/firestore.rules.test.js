import fs from "node:fs";
import { after, before, beforeEach, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  deleteField,
  doc,
  getDoc,
  setDoc,
  updateDoc
} from "firebase/firestore";

const PROJECT_ID = "demo-santethp";
const PAIR_CODE = "ABCDEFGH";
let testEnv;

const basePairing = (overrides = {}) => ({
  parentUid: "parent-user",
  childUid: "child-user",
  createdAt: Timestamp.now(),
  expiresAt: Timestamp.fromMillis(Date.now() + 10 * 60 * 1000),
  appBlockConsent: true,
  appBlockInstalledApps: [{ packageName: "com.example.app", appName: "Example" }],
  blockedApps: [],
  ...overrides
});

async function seedPairing(overrides = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc(`pairings/${PAIR_CODE}`).set(basePairing(overrides));
  });
}

async function seedUnclaimedPairing(overrides = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc(`pairings/${PAIR_CODE}`).set({
      parentUid: "parent-user",
      childUid: null,
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000),
      ...overrides
    });
  });
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(new URL("../firestore.rules", import.meta.url), "utf8")
    }
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await seedPairing();
});

after(async () => {
  await testEnv.cleanup();
});

test("parent can save a valid schedule using Sunday=1 through Saturday=7", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    usageScheduleEnabled: true,
    usageScheduleStartMinutes: 21 * 60,
    usageScheduleEndMinutes: 6 * 60,
    usageScheduleDays: [1, 2, 7],
    usageScheduleTimeZone: "Asia/Jakarta",
    usageScheduleUpdatedAt: Timestamp.now()
  }));
});

test("parent cannot save an invalid schedule day", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    usageScheduleDays: [0, 8]
  }));
});

test("parent can save up to ten safe zones with custom radius", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  const zones = Array.from({ length: 10 }, (_, index) => ({
    id: "zone-" + index,
    name: "Safe place " + index,
    latitude: -6.9 + index / 1000,
    longitude: 110.4 + index / 1000,
    radiusM: 250 + index
  }));
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    safeZones: zones,
    geofenceEnabled: false,
    geofenceUpdatedAt: Timestamp.now()
  }));
});

test("parent cannot save more than ten safe zones", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  const zones = Array.from({ length: 11 }, (_, index) => ({
    id: "zone-" + index,
    name: "Safe place " + index,
    latitude: -6.9,
    longitude: 110.4,
    radiusM: 250
  }));
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    safeZones: zones
  }));
});

test("child cannot change the parent's safe zones", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    safeZones: [{
      id: "unauthorized",
      name: "Changed",
      latitude: -6.9,
      longitude: 110.4,
      radiusM: 250
    }]
  }));
});

test("parent can register its own FCM token", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    parentFcmToken: "parent-fcm-token-".padEnd(40, "p")
  }));
});

test("child can register its own FCM token", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    childFcmToken: "child-fcm-token-".padEnd(40, "c")
  }));
});

test("parent cannot overwrite the child's FCM token", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    childFcmToken: "attacker-token-".padEnd(40, "x")
  }));
});

test("child cannot overwrite the parent's FCM token", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    parentFcmToken: "attacker-token-".padEnd(40, "x")
  }));
});

test("paired device cannot register an empty or oversized FCM token", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    childFcmToken: ""
  }));
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    childFcmToken: "x".repeat(4097)
  }));
});

test("child can publish a bounded safe-zone event history", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    safeZoneEvents: [{
      eventId: "event-12345678",
      zoneId: "zone-home",
      zoneName: "Rumah",
      transition: "ENTER",
      occurredAt: Timestamp.now(),
      latitude: -6.9,
      longitude: 110.4
    }]
  }));
});

test("child cannot publish more than thirty safe-zone events", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  const events = Array.from({ length: 31 }, (_, i) => ({
    eventId: "event-" + i,
    zoneId: "zone-home",
    zoneName: "Rumah",
    transition: "ENTER",
    occurredAt: Timestamp.now()
  }));
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), { safeZoneEvents: events }));
});

test("parent cannot forge child safe-zone events", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    safeZoneEvents: [{ eventId: "forged-event", transition: "EXIT" }]
  }));
});

test("parent cannot write child telemetry fields", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    deviceBatteryPercent: 88
  }));
});

test("child can publish app usage history and opt-in status", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    appUsageEnabled: true,
    appUsagePermissionGranted: true,
    appUsageSummary: [{
      packageName: "com.example.app",
      appName: "Example",
      foregroundMinutes: 42
    }],
    appUsageAt: Timestamp.now()
  }));
});

test("child can clear app usage history when sharing is disabled", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    appUsageEnabled: false,
    appUsagePermissionGranted: false,
    appUsageSummary: deleteField(),
    appUsageAt: Timestamp.now()
  }));
});

test("child can publish consented foreground app activity", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    appActivityConsent: true,
    appActivityPackage: "com.example.app",
    appActivityName: "Example",
    appActivityAt: Timestamp.now()
  }));
});

test("parent cannot publish foreground app activity for the child", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    appActivityConsent: true,
    appActivityPackage: "com.example.app",
    appActivityName: "Example",
    appActivityAt: Timestamp.now()
  }));
});

test("parent cannot write child app usage history", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    appUsageEnabled: true,
    appUsagePermissionGranted: true,
    appUsageSummary: [{
      packageName: "com.example.app",
      appName: "Example",
      foregroundMinutes: 42
    }],
    appUsageAt: Timestamp.now()
  }));
});

test("child can publish valid battery and network telemetry", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    deviceBatteryPercent: 78,
    deviceCharging: false,
    deviceNetworkType: "Wi-Fi",
    deviceInternetValidated: true,
    deviceStatusAt: Timestamp.now()
  }));
});

test("child cannot add or change the parent's blocked-app selection", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    blockedApps: ["com.example.app"]
  }));
});

test("child can revoke app-block consent and clear the blocked-app list", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    appBlockConsent: false,
    appBlockInstalledApps: [],
    blockedApps: [],
    appBlockUpdatedAt: Timestamp.now()
  }));
});

test("parent can save a time-limited app block only after child consent", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    blockedApps: [{
      packageName: "com.example.app",
      appName: "Example",
      blockedUntilMillis: Date.now() + 60 * 60 * 1000
    }],
    appBlockUpdatedAt: Timestamp.now()
  }));
});

test("unrelated signed-in user cannot read an already claimed pairing", async () => {
  const db = testEnv.authenticatedContext("unrelated-user").firestore();
  await assertFails(getDoc(doc(db, "pairings", PAIR_CODE)));
});

test("unrelated signed-in user cannot update an already claimed pairing", async () => {
  const db = testEnv.authenticatedContext("unrelated-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    deviceBatteryPercent: 99
  }));
});


test("parent can create an eight-character pairing code with a maximum five-minute expiry", async () => {
  const db = testEnv.authenticatedContext("new-parent").firestore();
  await assertSucceeds(setDoc(doc(db, "pairings", "23456789"), {
    parentUid: "new-parent",
    childUid: null,
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000)
  }));
});

test("parent cannot create a sixteen-character pairing code", async () => {
  const db = testEnv.authenticatedContext("new-parent").firestore();
  await assertFails(setDoc(doc(db, "pairings", "ABCDEFGHJKLMNPQR"), {
    parentUid: "new-parent",
    childUid: null,
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000)
  }));
});

test("parent cannot create a pairing code that expires after five minutes", async () => {
  const db = testEnv.authenticatedContext("new-parent").firestore();
  await assertFails(setDoc(doc(db, "pairings", "ABCDEFGH"), {
    parentUid: "new-parent",
    childUid: null,
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 6 * 60 * 1000)
  }));
});

test("child can claim an active unclaimed pairing exactly once", async () => {
  await seedUnclaimedPairing();
  const childDb = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(childDb, "pairings", PAIR_CODE), {
    childUid: "child-user"
  }));

  const otherDb = testEnv.authenticatedContext("other-user").firestore();
  await assertFails(updateDoc(doc(otherDb, "pairings", PAIR_CODE), {
    childUid: "other-user"
  }));
});

test("parent cannot claim their own pairing code as the child", async () => {
  await seedUnclaimedPairing();
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    childUid: "parent-user"
  }));
});

test("expired unclaimed pairing cannot be read", async () => {
  await seedUnclaimedPairing({
    expiresAt: Timestamp.fromMillis(Date.now() - 60 * 1000)
  });
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertFails(getDoc(doc(db, "pairings", PAIR_CODE)));
});

test("expired unclaimed pairing cannot be claimed", async () => {
  await seedUnclaimedPairing({
    expiresAt: Timestamp.fromMillis(Date.now() - 60 * 1000)
  });
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    childUid: "child-user"
  }));
});

test("claim cannot change the parent UID or other pairing fields", async () => {
  await seedUnclaimedPairing();
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    childUid: "child-user",
    parentUid: "attacker-parent"
  }));
});

// This documents the current limitation: the first signed-in account that
// knows an active code can claim it. Firestore Rules cannot identify the
// intended child unless the parent pre-authorizes that child's UID.
test("any signed-in account can claim an active unclaimed code first (known limitation)", async () => {
  await seedUnclaimedPairing();
  const db = testEnv.authenticatedContext("unexpected-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    childUid: "unexpected-user"
  }));
});

test.todo("Open unclaimed pairing codes can be guessed without server-side rate limiting; the five-minute expiry limits the exposure window.");


test("parent can save an allowed LiveKit audio server selection", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    audioLiveKitServerId: "backup-2"
  }));
});

test("parent can save an allowed LiveKit camera server selection", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    cameraLiveKitServerId: "backup-5"
  }));
});

test("child cannot change either LiveKit server selection", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    audioLiveKitServerId: "backup-2"
  }));
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    cameraLiveKitServerId: "backup-3"
  }));
});

test("parent cannot save an unknown LiveKit server ID", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    audioLiveKitServerId: "backup-6"
  }));
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    cameraLiveKitServerId: "unknown"
  }));
});


// Media-consent and P2P signaling coverage for Galeri Anak, Drive backup,
// and parent-to-child media receiving. These tests use the Firestore emulator.
test("child can update the three independent media consent flags", async () => {
  const db = testEnv.authenticatedContext("child-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    gallerySharingEnabled: true,
    gallerySharingUpdatedAt: Timestamp.now(),
    driveBackupEnabled: true,
    driveBackupUpdatedAt: Timestamp.now(),
    parentMediaReceiveEnabled: true,
    parentMediaReceiveUpdatedAt: Timestamp.now()
  }));
});

test("parent cannot change child-controlled media consent flags", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    driveBackupEnabled: true,
    driveBackupUpdatedAt: Timestamp.now()
  }));
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    parentMediaReceiveEnabled: true,
    parentMediaReceiveUpdatedAt: Timestamp.now()
  }));
});

for (const [consentField, label] of [
  ["gallerySharingEnabled", "gallery sharing"],
  ["driveBackupEnabled", "Drive backup"],
  ["parentMediaReceiveEnabled", "parent-to-child media receiving"]
]) {
  test(`paired parent and child can exchange P2P signaling when ${label} consent is enabled`, async () => {
    await seedPairing({
      [consentField]: true,
      galleryP2pSessionId: "media-session-123"
    });

    const parentDb = testEnv.authenticatedContext("parent-user").firestore();
    const childDb = testEnv.authenticatedContext("child-user").firestore();
    const offer = doc(parentDb, "pairings", PAIR_CODE, "gallerySignals", "offer-1");
    await assertSucceeds(setDoc(offer, {
      kind: "offer",
      senderUid: "parent-user",
      sessionId: "media-session-123",
      sdp: "valid-test-sdp",
      createdAt: Timestamp.now()
    }));

    await assertSucceeds(getDoc(doc(childDb, "pairings", PAIR_CODE, "gallerySignals", "offer-1")));

    const answer = doc(childDb, "pairings", PAIR_CODE, "gallerySignals", "answer-1");
    await assertSucceeds(setDoc(answer, {
      kind: "answer",
      senderUid: "child-user",
      sessionId: "media-session-123",
      sdp: "valid-test-answer",
      createdAt: Timestamp.now()
    }));
  });
}

test("P2P signaling is denied when all three media consents are disabled", async () => {
  await seedPairing({
    gallerySharingEnabled: false,
    driveBackupEnabled: false,
    parentMediaReceiveEnabled: false,
    galleryP2pSessionId: "media-session-123"
  });
  const parentDb = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(setDoc(doc(parentDb, "pairings", PAIR_CODE, "gallerySignals", "blocked-offer"), {
    kind: "offer",
    senderUid: "parent-user",
    sessionId: "media-session-123",
    sdp: "should-be-denied",
    createdAt: Timestamp.now()
  }));
});

test("unpaired user cannot read P2P signaling even when media consent is enabled", async () => {
  await seedPairing({
    driveBackupEnabled: true,
    galleryP2pSessionId: "media-session-123"
  });
  const outsiderDb = testEnv.authenticatedContext("unrelated-user").firestore();
  await assertFails(getDoc(doc(outsiderDb, "pairings", PAIR_CODE, "gallerySignals", "offer-1")));
});
