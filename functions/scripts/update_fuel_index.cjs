const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

// Toma la clave desde las variables de entorno de GitHub
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

// Precio base histórico de referencia para el diésel en Arequipa (S/ 15.50)
const BASE_FUEL_PRICE = 15.50;

async function fetchArequipaDieselPrice() {
  try {
    // Consulta a endpoint / fuente de precios de combustible
    const res = await fetch("https://api.osinergmin.gob.pe/hidrocarburos/precios/arequipa/diesel", {
      headers: { "Accept": "application/json" }
    });
    if (res.ok) {
      const data = await res.json();
      if (data && data.averagePrice) return data.averagePrice;
    }
  } catch (e) {
    console.warn("Fallo al conectar con la API, usando precio de respaldo:", e.message);
  }
  // Precio de respaldo en caso de que el portal esté caído
  return 17.50;
}

async function main() {
  const currentPrice = await fetchArequipaDieselPrice();
  // Calcula el factor proporcional (ej: 17.50 / 15.50 = 1.13 -> 13% de incremento)
  const fuelIndex = Math.round((currentPrice / BASE_FUEL_PRICE) * 100) / 100;

  console.log(`Precio detectado: S/ ${currentPrice} | Nuevo fuelIndex: ${fuelIndex}`);

  await db.collection("fareVersions").doc("v1").update({
    fuelIndex: fuelIndex,
    lastFuelPrice: currentPrice,
    updatedAt: FieldValue.serverTimestamp()
  });

  console.log("¡Firestore fareVersions/v1 actualizado correctamente!");
}

main().then(() => process.exit(0)).catch(err => { console.error(err); process.exit(1); });