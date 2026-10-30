const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const serviceAccount = require("./serviceAccountKey.json");

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

async function run() {
  const versionId = "v1";

  await db.collection("fareVersions").doc(versionId).set({
    baseFarePerKmCents: 35,
    fuelIndex: 1.0,
    minFareCents: 100,
    maxFareCents: 350,
    roundingStepCents: 10,
    updatedAt: FieldValue.serverTimestamp()
  }, { merge: true });

  console.log("Tarifa actualizada con éxito: baseFarePerKmCents = 35");
}

run().then(() => process.exit(0)).catch(err => { console.error(err); process.exit(1); });
