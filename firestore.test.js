const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_lyrix_123";
const BOB_UID = "bob_lyrix_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, createdAt, updatedAt) {
  return {
    userId: uid,
    title: "Midnight Rain Edit",
    audioDisplayName: "midnight_rain.mp3",
    localAudioPath: "media/midnight_rain.mp3",
    localBackgroundPath: "",
    backgroundPresetKey: "cinematic_rain",
    durationMs: 185000,
    bpm: 92,
    detectedMood: "sad",
    presetName: "Cinematic Sad",
    fontStyle: "Cormorant Garamond",
    fontSizeSp: 30.0,
    textColorHex: "#FFFFFF",
    highlightColorHex: "#38BDF8",
    textPosition: "CENTER",
    textAnimation: "BLUR_REVEAL",
    textShadowIntensity: 0.75,
    blurIntensity: 0.35,
    glowIntensity: 0.45,
    wordHighlightEnabled: true,
    syncOffsetMs: 0,
    slowMotionEnabled: true,
    beatFlashEnabled: true,
    lyricLines: [
      {
        startMs: 1000,
        endMs: 4200,
        sectionLabel: "Verse 1",
        text: "City lights blur in the rain",
        words: [
          { word: "City", startMs: 1000, endMs: 1600 },
          { word: "lights", startMs: 1650, endMs: 2200 }
        ]
      }
    ],
    sceneMarkers: [
      { timeMs: 0, transitionType: "FADE", isSlowMotion: false, backgroundKey: "cinematic_rain" }
    ],
    waveformBars: [0.2, 0.5, 0.85, 0.4],
    lastExportedPath: "",
    createdAt,
    updatedAt,
  };
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

test("Unauthenticated user: cannot read or write projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1")
      .set(validProjectPayload(ALICE_UID, now, now))
  );
});

test("Authenticated owner: valid create, list, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1")
      .set(validProjectPayload(ALICE_UID, created, created))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1")
      .update({
        title: "Midnight Rain V2",
        presetName: "Dark Emotional",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").delete()
  );
});

test("Authenticated owner: rejects ghost fields, @DocumentId id field, immutable createdAt change, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1")
      .set(validProjectPayload(ALICE_UID, created, created))
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  const withId = validProjectPayload(ALICE_UID, created, created);
  withId.id = "proj_2";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_2").set(withId)
  );

  const withGhost = validProjectPayload(ALICE_UID, created, created);
  withGhost.isAdmin = true;
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_3").set(withGhost)
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_4")
      .set(validProjectPayload(ALICE_UID, new Date(Date.now() + 60000), created))
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's projects", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("projects").doc("alice_proj")
      .set(validProjectPayload(ALICE_UID, created, created));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("alice_proj").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("alice_proj")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("alice_proj").delete());
});
