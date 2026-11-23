const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "owner_admin_uid";
const ADMIN_EMAIL = "tumusiimeryanpro@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 1000) {
  return new Date(Date.now() - msOffset);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// 1. Unauthenticated access must fail across all collections
test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("predictions").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("reviews").where("hidden", "==", false).get());
  await assertFails(unauthDb.collection("comments").where("hidden", "==", false).get());
  await assertFails(unauthDb.collection("feedback").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("admin_audit_logs").get());
  await assertFails(unauthDb.collection("admin_config").doc("security").get());
});

// 2. Users collection: PII isolation, self-create, no self-unrestrict, no list by non-admin
test("User profile isolation and restriction enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();

  const created = pastDate(5000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      notificationsEnabled: true,
      matchAlertsEnabled: true,
      resultAlertsEnabled: true,
      restricted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Alice can update her permitted settings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice M",
      notificationsEnabled: false,
      updatedAt: pastDate(1000),
    })
  );

  // Alice cannot set restricted=false if she was restricted or elevate/change restricted
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      restricted: true,
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot read Alice's PII or list users
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());

  // Admin can list users and restrict Alice
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      restricted: true,
      updatedAt: pastDate(200),
    })
  );
});

// 3. Predictions: creation, terminal-state lock, query alignment
test("Predictions snapshot immutability, settlement, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();

  const created = pastDate(10000);
  await assertSucceeds(
    aliceDb.collection("predictions").doc("pred_1").set({
      userId: ALICE_UID,
      matchId: "openligadb_101",
      homeTeam: "Bayern Munich",
      awayTeam: "Borussia Dortmund",
      competition: "Bundesliga",
      country: "Germany",
      kickoffEpochMs: Date.now() + 86400000,
      modelVersion: "v1.0-dc-poisson",
      dataSources: "OpenLigaDB",
      homeXg: 1.92,
      awayXg: 1.28,
      probHomeWin: 52,
      probDraw: 24,
      probAwayWin: 24,
      probBttsYes: 61,
      probOver25: 59,
      topCorrectScore: "2-1",
      topCorrectScoreProb: 11,
      confidenceScore: 76,
      status: "PENDING",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob cannot read Alice's prediction or run unfiltered list
  await assertFails(bobDb.collection("predictions").doc("pred_1").get());
  await assertFails(aliceDb.collection("predictions").get());
  await assertSucceeds(aliceDb.collection("predictions").where("userId", "==", ALICE_UID).get());

  // Settle prediction
  await assertSucceeds(
    aliceDb.collection("predictions").doc("pred_1").update({
      status: "SETTLED",
      actualHomeGoals: 2,
      actualAwayGoals: 1,
      settledResult1X2: "HOME",
      settledBtts: "YES",
      settledOver25: "OVER",
      outcomeCorrect: true,
      settledAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state lock: Alice cannot tamper with settled prediction
  await assertFails(
    aliceDb.collection("predictions").doc("pred_1").update({
      actualHomeGoals: 3,
      updatedAt: pastDate(500),
    })
  );

  // Admin can adjust if necessary
  await assertSucceeds(
    adminDb.collection("predictions").doc("pred_1").update({
      actualHomeGoals: 2,
      updatedAt: pastDate(400),
    })
  );
});

// 4. Community reviews, comments, private feedback, and reports
test("Community reviews, comments, private feedback isolation, and moderation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();

  const created = pastDate(8000);

  // One active review per account (reviewId == uid)
  await assertSucceeds(
    aliceDb.collection("reviews").doc(ALICE_UID).set({
      userId: ALICE_UID,
      authorName: "Alice",
      rating: 5,
      reviewText: "Accurate Bundesliga coverage and crisp probability breakdown.",
      helpfulCount: 0,
      hidden: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cannot create review under another user's doc ID
  await assertFails(
    bobDb.collection("reviews").doc("arbitrary_id").set({
      userId: BOB_UID,
      authorName: "Bob",
      rating: 4,
      reviewText: "Should fail because reviewId != uid",
      helpfulCount: 0,
      hidden: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Filtered list of non-hidden reviews succeeds
  await assertSucceeds(bobDb.collection("reviews").where("hidden", "==", false).get());
  // Unfiltered list fails
  await assertFails(bobDb.collection("reviews").get());

  // Private feedback: Bob cannot read Alice's private feedback; Admin can read and reply
  await assertSucceeds(
    aliceDb.collection("feedback").doc("fb_1").set({
      userId: ALICE_UID,
      senderName: "Alice",
      category: "DATA_CORRECTION",
      subject: "Kickoff timezone check",
      message: "Please verify match 402 kickoff display.",
      status: "OPEN",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(bobDb.collection("feedback").doc("fb_1").get());
  await assertFails(bobDb.collection("feedback").get());
  await assertSucceeds(aliceDb.collection("feedback").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(
    adminDb.collection("feedback").doc("fb_1").update({
      status: "REPLIED",
      ownerReply: "Verified and updated.",
      updatedAt: pastDate(1000),
    })
  );
});

// 5. Email spoofing and admin privilege boundary tests
test("Admin spoofing without verified email or wrong email is denied", async () => {
  const unverifiedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const ordinaryUserDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "ordinary@example.com",
    email_verified: true,
  }).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const now = pastDate(1000);
  const logPayload = {
    actorEmail: ADMIN_EMAIL,
    action: "MODEL_RECALIBRATE",
    targetPath: "model_versions/v1_1",
    details: "Approved v1.1 calibration",
    createdAt: now,
  };

  await assertFails(unverifiedAdminDb.collection("admin_audit_logs").doc("log_1").set(logPayload));
  await assertFails(ordinaryUserDb.collection("admin_audit_logs").doc("log_1").set(logPayload));
  await assertSucceeds(realAdminDb.collection("admin_audit_logs").doc("log_1").set(logPayload));

  // Audit logs are immutable even for admin
  await assertFails(realAdminDb.collection("admin_audit_logs").doc("log_1").update({ details: "tampered" }));
  await assertFails(realAdminDb.collection("admin_audit_logs").doc("log_1").delete());
});
