const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

const BASE_FUEL_PRICE = 15.50;

async function fetchArequipaDieselPrice() {
  try {
    const res = await fetch("https://api.osinergmin.gob.pe/hidrocarburos/precios/arequipa/diesel", {
      headers: { "Accept": "application/json" }
    });
    if (res.ok) {
      const data = await res.json();
      if (data && data.averagePrice) return data.averagePrice;
    }
  } catch (e) {
    console.warn("Fallo API Osinergmin, usando fallback:", e.message);
  }
  return 17.50; // Fallback diésel Arequipa
}

async function main() {
  const currentPrice = await fetchArequipaDieselPrice();
  const fuelIndex = Math.round((currentPrice / BASE_FUEL_PRICE) * 100) / 100;

  // 1. Obtener el último precio registrado para calcular la variación
  const lastSnap = await db.collection("fareVersions").doc("v1").get();
  const previousPrice = lastSnap.data()?.lastFuelPrice || BASE_FUEL_PRICE;
  const changePercent = Math.round(((currentPrice - previousPrice) / previousPrice) * 1000) / 10;

  // 2. Actualizar la tarifa activa
  await db.collection("fareVersions").doc("v1").update({
    fuelIndex: fuelIndex,
    lastFuelPrice: currentPrice,
    updatedAt: FieldValue.serverTimestamp()
  });

  // 3. Registrar el evento en el historial
  await db.collection("fuelHistory").add({
    price: currentPrice,
    fuelIndex: fuelIndex,
    changePercent: changePercent,
    source: "Osinergmin Facilito - Arequipa",
    recordedAt: FieldValue.serverTimestamp()
  });

  console.log(`Historial registrado: S/ ${currentPrice} (${changePercent > 0 ? "+" : ""}${changePercent}%)`);
}

main().then(() => process.exit(0)).catch(err => { console.error(err); process.exit(1); });