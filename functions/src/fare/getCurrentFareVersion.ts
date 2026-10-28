import { getFirestore } from "firebase-admin/firestore";
import { DEFAULT_FARE_VERSION } from "../config/fare.js";

export interface CurrentFareVersion {
  id: string;
  baseFarePerKmCents: number;
  fuelIndex: number;
  fuelPriceCurrent?: number | null;
  fuelPriceReference?: number | null;
  minFareCents: number;
  roundingStepCents: number;
}

export async function getCurrentFareVersion(): Promise<CurrentFareVersion> {
  const db = getFirestore();
  const config = await db.collection("config").doc("currentFare").get();
  const versionId = config.exists ? (config.get("versionId") as string | undefined) : undefined;
  if (!versionId) {
    return {
      id: "default",
      baseFarePerKmCents: DEFAULT_FARE_VERSION.baseFarePerKmCents,
      fuelIndex: DEFAULT_FARE_VERSION.fuelIndex,
      minFareCents: DEFAULT_FARE_VERSION.minFareCents,
      roundingStepCents: DEFAULT_FARE_VERSION.roundingStepCents
    };
  }
  const version = await db.collection("fareVersions").doc(versionId).get();
  if (!version.exists) {
    return {
      id: versionId,
      baseFarePerKmCents: DEFAULT_FARE_VERSION.baseFarePerKmCents,
      fuelIndex: DEFAULT_FARE_VERSION.fuelIndex,
      minFareCents: DEFAULT_FARE_VERSION.minFareCents,
      roundingStepCents: DEFAULT_FARE_VERSION.roundingStepCents
    };
  }
  const baseFarePerKmCents = Number(version.get("baseFarePerKmCents") ?? DEFAULT_FARE_VERSION.baseFarePerKmCents);
  const fuelIndex = Number(version.get("fuelIndex") ?? DEFAULT_FARE_VERSION.fuelIndex);
  const minFareCents = Number(version.get("minFareCents") ?? DEFAULT_FARE_VERSION.minFareCents);
  const roundingStepCents = Number(version.get("roundingStepCents") ?? DEFAULT_FARE_VERSION.roundingStepCents);
  return {
    id: versionId,
    baseFarePerKmCents,
    fuelIndex,
    fuelPriceCurrent: version.get("fuelPriceCurrent") ?? null,
    fuelPriceReference: version.get("fuelPriceReference") ?? null,
    minFareCents,
    roundingStepCents
  };
}
