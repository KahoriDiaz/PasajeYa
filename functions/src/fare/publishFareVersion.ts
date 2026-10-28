import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "../config/region.js";
import { requireRecord, requireVerifiedAuth } from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";

export interface PublishFareVersionData {
  sessionId: string;
  baseFarePerKmCents: number;
  fuelType: string;
  fuelPriceCurrentCents: number;
  fuelPriceReferenceCents: number;
  fuelPriceSourceName: string;
  fuelPriceSourceUrl: string;
  fuelPriceDate: string;
  minFareCents: number;
  maxFareCents?: number;
  roundingStepCents: number;
  calibrationNote?: string;
}

const VALID_FUEL_TYPES = ["GNV", "GLP", "DIESEL", "GASOLINA_90", "GASOLINA_95"];

function isValidDate(value: string): boolean {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return false;
  const now = new Date();
  const diffDays = (now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24);
  if (diffDays > 45) return false;
  if (diffDays < -1) return false;
  return true;
}

export async function publishFareVersionHandler(
  request: CallableRequest<PublishFareVersionData>
): Promise<{ versionId: string }> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = typeof data.sessionId === "string" ? data.sessionId : "";
  if (sessionId.length === 0) {
    throw new HttpsError("invalid-argument", "La sesión es obligatoria.");
  }

  const baseFarePerKmCents = Number(data.baseFarePerKmCents);
  const minFareCents = Number(data.minFareCents);
  const roundingStepCents = Number(data.roundingStepCents);
  const fuelPriceCurrentCents = Number(data.fuelPriceCurrentCents);
  const fuelPriceReferenceCents = Number(data.fuelPriceReferenceCents);
  const fuelType = typeof data.fuelType === "string" ? data.fuelType : "";
  const fuelPriceSourceName = typeof data.fuelPriceSourceName === "string" ? data.fuelPriceSourceName.trim() : "";
  const fuelPriceSourceUrl = typeof data.fuelPriceSourceUrl === "string" ? data.fuelPriceSourceUrl.trim() : "";
  const fuelPriceDate = typeof data.fuelPriceDate === "string" ? data.fuelPriceDate.trim() : "";

  if (!Number.isInteger(baseFarePerKmCents) || baseFarePerKmCents <= 0) {
    throw new HttpsError("invalid-argument", "La tarifa base por km es inválida.");
  }
  if (!Number.isInteger(minFareCents) || minFareCents <= 0) {
    throw new HttpsError("invalid-argument", "La tarifa mínima es inválida.");
  }
  if (!Number.isInteger(roundingStepCents) || roundingStepCents <= 0) {
    throw new HttpsError("invalid-argument", "El paso de redondeo es inválido.");
  }
  if (!Number.isInteger(fuelPriceCurrentCents) || fuelPriceCurrentCents <= 0) {
    throw new HttpsError("invalid-argument", "El precio de combustible es inválido.");
  }
  if (!Number.isInteger(fuelPriceReferenceCents) || fuelPriceReferenceCents <= 0) {
    throw new HttpsError("invalid-argument", "El precio de referencia es inválido.");
  }
  if (!VALID_FUEL_TYPES.includes(fuelType)) {
    throw new HttpsError("invalid-argument", "El tipo de combustible no es válido.");
  }
  if (fuelPriceSourceName.length === 0 || fuelPriceSourceUrl.length === 0) {
    throw new HttpsError("invalid-argument", "Falta la fuente del combustible.");
  }
  if (!isValidDate(fuelPriceDate)) {
    throw new HttpsError("invalid-argument", "La fecha del combustible no es válida o es demasiado antigua.");
  }
  const fuelIndex = Number((fuelPriceCurrentCents / fuelPriceReferenceCents).toFixed(3));
  if (fuelIndex < 0.5 || fuelIndex > 2.0) {
    throw new HttpsError("invalid-argument", "El índice de combustible está fuera de rango.");
  }

  const db = getFirestore();
  const uid = auth.uid;
  return db.runTransaction(async (transaction) => {
    await assertActiveSession(uid, sessionId, transaction);
    const userSnap = await transaction.get(db.collection("users").doc(uid));
    const role = userSnap.get("role");
    if (role !== "operator" && role !== "passenger") {
      // allow admin-like ops only via admin SDK in practice; but callable requires auth
    }

    const ref = db.collection("fareVersions").doc();
    transaction.set(ref, {
      baseFarePerKmCents,
      fuelType,
      fuelPriceCurrent: fuelPriceCurrentCents,
      fuelPriceReference: fuelPriceReferenceCents,
      fuelPriceSourceName,
      fuelPriceSourceUrl,
      fuelPriceDate,
      fuelIndex,
      minFareCents,
      roundingStepCents,
      calibrationNote: typeof data.calibrationNote === "string" ? data.calibrationNote : null,
      createdAt: Timestamp.now()
    });
    transaction.set(db.collection("config").doc("currentFare"), { versionId: ref.id }, { merge: true });
    return { versionId: ref.id };
  });
}

export const publishFareVersion = onCall(
  { region: FUNCTIONS_REGION },
  publishFareVersionHandler
);
