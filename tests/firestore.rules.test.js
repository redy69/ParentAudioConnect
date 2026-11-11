import fs from "node:fs";
import { after, before, beforeEach, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
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

test("parent cannot write child telemetry fields", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertFails(updateDoc(doc(db, "pairings", PAIR_CODE), {
    deviceBatteryPercent: 88
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

test("parent can select blocked apps only after child consent", async () => {
  const db = testEnv.authenticatedContext("parent-user").firestore();
  await assertSucceeds(updateDoc(doc(db, "pairings", PAIR_CODE), {
    blockedApps: ["com.example.app"]
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


test("parent can create a 16-character pairing code with a maximum five-minute expiry", async () => {
  const db = testEnv.authenticatedContext("new-parent").firestore();
  await assertSucceeds(setDoc(doc(db, "pairings", "ABCDEFGHJKLMNPQR"), {
    parentUid: "new-parent",
    childUid: null,
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000)
  }));
});

test("parent cannot create a legacy eight-character pairing code", async () => {
  const db = testEnv.authenticatedContext("new-parent").firestore();
  await assertFails(setDoc(doc(db, "pairings", "ABCDEFGH"), {
    parentUid: "new-parent",
    childUid: null,
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000)
  }));
});

test("parent cannot create a pairing code that expires after five minutes", async () => {
  const db = testEnv.authenticatedContext("new-parent").firestore();
  await assertFails(setDoc(doc(db, "pairings", "ABCDEFGHJKLMNPQR"), {
    parentUid: "new-parent",
    childUid: null,
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 6 * 60 * 1000)
  }));
});

test.todo("Open unclaimed pairing codes can still be read and claimed by any signed-in user; high-entropy codes reduce guessing risk but do not provide server-side rate limiting.");
