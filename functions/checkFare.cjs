const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const serviceAccount = require("./serviceAccountKey.json");

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

async function run() {
  const currentFareSnap = await db.collection("config").doc("currentFare").get();
  console.log("=== config/currentFare ===");
  console.log("Existe:", currentFareSnap.exists);
  if (currentFareSnap.exists) {
    console.log("Datos:", currentFareSnap.data());
    const vId = currentFareSnap.data().versionId;
    if (vId) {
      const vSnap = await db.collection("fareVersions").doc(vId).get();
      console.log("=== fareVersions/" + vId + " ===");
      console.log("Existe:", vSnap.exists);
      if (vSnap.exists) console.log("Datos:", vSnap.data());
    } else {
      console.log("versionId NO EXISTE en config/currentFare");
    }
  }

  const routesSnap = await db.collection("routes").limit(1).get();
  console.log("=== Coleccion routes ===");
  if (routesSnap.empty) {
    console.log("Coleccion routes VACIA");
  } else {
    const doc = routesSnap.docs[0];
    const d = doc.data();
    console.log("ID:", doc.id);
    console.log("Nombre:", d.name);
    console.log("Cant paraderos:", Array.isArray(d.stops) ? d.stops.length : "NO ES ARRAY");
    if (Array.isArray(d.stops) && d.stops.length > 0) {
      console.log("Muestra paradero 0:", JSON.stringify(d.stops[0]));
    }
  }
}

run().then(() => process.exit(0)).catch(err => { console.error(err); process.exit(1); });
