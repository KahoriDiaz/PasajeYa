const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const serviceAccount = require("./serviceAccountKey.json");

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

async function run() {
  const versionId = "v1";

  // 1. Guardar la configuración de la versión de tarifa
  await db.collection("fareVersions").doc(versionId).set({
    baseFarePerKmCents: 20,
    fuelIndex: 1.0,
    minFareCents: 100,
    roundingStepCents: 10,
    updatedAt: FieldValue.serverTimestamp()
  }, { merge: true });

  // 2. Apuntar currentFare a la versión v1
  await db.collection("config").doc("currentFare").set({
    versionId: versionId,
    updatedAt: FieldValue.serverTimestamp()
  }, { merge: true });

  console.log("Tarifa v1 y config/currentFare creados con EXITO");
}

run().then(() => process.exit(0)).catch(err => { console.error(err); process.exit(1); });
