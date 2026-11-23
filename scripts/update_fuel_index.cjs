const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

// Precio base de calibración histórica en Arequipa
const BASE_FUEL_PRICE = 15.50;

// Constantes base de referencia (cuando fuelIndex = 1.0)
const BASE_BOARDING_CENTS = 70;      // 70 céntimos de arranque base
const BASE_KM_RATE_CENTS = 7;        // 7 céntimos por km base
const BASE_MIN_FARE_CENTS = 90;      // Mínimo base: S/ 0.90
const BASE_MAX_FARE_CENTS = 140;     // Tope base: S/ 1.40
const ROUNDING_STEP = 10;            // Pasos comerciales de 10 céntimos

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
    console.warn("Fallo API Osinergmin, usando fallback de mercado local:", e.message);
  }
  return 17.50;
}

function roundToStep(cents, step) {
  return Math.ceil(cents / step) * step;
}

async function main() {
  const currentPrice = await fetchArequipaDieselPrice();
  const fuelIndex = Math.round((currentPrice / BASE_FUEL_PRICE) * 100) / 100;

  // Escalado dinámico e indexado de cada parámetro:
  const dynamicMinFare = roundToStep(BASE_MIN_FARE_CENTS * fuelIndex, ROUNDING_STEP);
  const dynamicMaxFare = roundToStep(BASE_MAX_FARE_CENTS * fuelIndex, ROUNDING_STEP);
  const dynamicKmRate = Math.round(BASE_KM_RATE_CENTS * fuelIndex);

  const lastSnap = await db.collection("fareVersions").doc("v1").get();
  const previousPrice = lastSnap.data()?.lastFuelPrice || BASE_FUEL_PRICE;
  const changePercent = Math.round(((currentPrice - previousPrice) / previousPrice) * 1000) / 10;

  // Actualización del contrato de tarifas en Firestore
  await db.collection("fareVersions").doc("v1").set({
    fuelIndex: fuelIndex,
    lastFuelPrice: currentPrice,
    baseFarePerKmCents: dynamicKmRate,
    minFareCents: dynamicMinFare,
    maxFareCents: dynamicMaxFare,
    roundingStepCents: ROUNDING_STEP,
    updatedAt: FieldValue.serverTimestamp()
  }, { merge: true });

  // Registro del historial para transparencia al usuario
  await db.collection("fuelHistory").add({
    price: currentPrice,
    fuelIndex: fuelIndex,
    changePercent: changePercent,
    source: "Osinergmin Facilito - Arequipa",
    recordedAt: FieldValue.serverTimestamp()
  });

  console.log(`Tarifas actualizadas a las 06:00 AM:`);
  console.log(`- Diésel: S/ ${currentPrice} (Factor: ${fuelIndex}x)`);
  console.log(`- Tasa por Km: S/ ${(dynamicKmRate / 100).toFixed(2)}`);
  console.log(`- Piso Mínimo Dinámico: S/ ${(dynamicMinFare / 100).toFixed(2)}`);
  console.log(`- Tope Máximo Dinámico: S/ ${(dynamicMaxFare / 100).toFixed(2)}`);
}

main().then(() => process.exit(0)).catch(err => { console.error(err); process.exit(1); });
