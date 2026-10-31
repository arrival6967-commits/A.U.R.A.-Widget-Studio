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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function createValidWidgetPayload(userId, now) {
  return {
    userId: userId,
    title: "Погода Москва",
    subtitle: "Ощущается как +18°C • Ясно",
    primaryValue: "+21°C",
    secondaryValue: "Ветер 3 м/с • Влажность 45%",
    prompt: "Сделай виджет погоды в стиле Liquid Glass",
    stylePreset: "LIQUID_GLASS",
    category: "WEATHER",
    sizePreset: "WIDE_4X2",
    primaryColorHex: "#38BDF8",
    secondaryColorHex: "#A855F7",
    surfaceColorHex: "#0F172A",
    glassOpacity: 0.35,
    blurRadiusDp: 24.0,
    specularIntensity: 0.75,
    cornerRadiusDp: 28.0,
    iconName: "WbSunny",
    fontFamily: "GOOGLE_SANS",
    autoDarkAdapt: true,
    isPinned: true,
    createdAt: now,
    updatedAt: now,
  };
}

function createValidSettingsPayload(userId, now) {
  return {
    userId: userId,
    activeWallpaperPreset: "AURORA_BOREALIS",
    extractedPaletteHex: ["#38BDF8", "#A855F7", "#F472B6", "#0F172A"],
    darkThemeMode: "SYSTEM",
    globalGlassOpacity: 0.4,
    globalBlurDp: 22.0,
    syncDeviceName: "Pixel 9 Pro",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot read or write widgets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("widgets").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("widgets")
      .doc("w1")
      .set(createValidWidgetPayload(ALICE_UID, new Date()))
  );
});

test("Authenticated owner: can create, read, list, and update own widget", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("widgets").doc("widget_1");

  await assertSucceeds(docRef.set(createValidWidgetPayload(ALICE_UID, now)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("widgets")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    docRef.update({
      glassOpacity: 0.65,
      primaryColorHex: "#EC4899",
      fontFamily: "PLAYFAIR_DISPLAY",
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice's widget", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("widgets")
      .doc("alice_widget")
      .set(createValidWidgetPayload(ALICE_UID, now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("widgets").doc("alice_widget").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("widgets")
      .doc("alice_widget")
      .update({ title: "Hacked" })
  );
});

test("Shadow update & value poisoning: rejects unknown ghost fields or invalid opacity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("widgets").doc("widget_sec");
  await assertSucceeds(docRef.set(createValidWidgetPayload(ALICE_UID, now)));

  // Ghost field attack
  await assertFails(
    docRef.update({
      isAdmin: true,
      updatedAt: new Date(),
    })
  );

  // Out-of-bounds glassOpacity (> 1.0)
  await assertFails(
    docRef.update({
      glassOpacity: 2.5,
      updatedAt: new Date(),
    })
  );
});

test("Authenticated owner: can create and update cloud workspace settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("workspace");

  await assertSucceeds(prefRef.set(createValidSettingsPayload(ALICE_UID, now)));
  await assertSucceeds(
    prefRef.update({
      darkThemeMode: "DARK",
      globalGlassOpacity: 0.55,
      updatedAt: new Date(),
    })
  );
});
