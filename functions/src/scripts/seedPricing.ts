import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

const projectId = process.env.GCLOUD_PROJECT ?? "pasajeya-f150d";
const app = initializeApp({ projectId });
const db = getFirestore(app);

async function main(): Promise<void> {
  const pricingRef = db.collection("config").doc("pricing");
  const pricing = {
    versionId: "fare-v1",
    baseFarePerKmCents: 12,
    fuelIndex: 1.0,
    minFareCents: 50,
    roundingStepCents: 5,
    source: "official-fares-2026-08-05",
    note: "Tarifa base vigente. El tope sale del route.officialFareCents."
  };
  await pricingRef.set(pricing);
  console.log("Pricing guardado en config/pricing.");
}

main().catch((error) => {
  console.error("Error al sembrar pricing:", error);
  process.exit(1);
});
