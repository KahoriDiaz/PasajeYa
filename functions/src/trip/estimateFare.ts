import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "../config/region.js";
import { requireRecord, requireVerifiedAuth } from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import { getCurrentFareVersion } from "../fare/getCurrentFareVersion.js";
import { computeFare } from "../fare/computeFare.js";

export interface EstimateFareData {
  sessionId: string;
  routeId: string;
  boardingStopIndex: number;
  alightingStopIndex: number;
}

function haversineMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000;
  const toRad = (v: number) => (v * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export async function estimateFareHandler(
  request: CallableRequest<EstimateFareData>
): Promise<any> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = typeof data.sessionId === "string" ? data.sessionId : "";
  const routeId = typeof data.routeId === "string" ? data.routeId : "";
  const boardingStopIndex = typeof data.boardingStopIndex === "number" ? data.boardingStopIndex : Number(data.boardingStopIndex);
  const alightingStopIndex = typeof data.alightingStopIndex === "number" ? data.alightingStopIndex : Number(data.alightingStopIndex);
  if (!Number.isFinite(boardingStopIndex) || !Number.isFinite(alightingStopIndex)) {
    throw new HttpsError("invalid-argument", "Los índices de paradero son inválidos.");
  }
  if (sessionId.length === 0) {
    throw new HttpsError("invalid-argument", "La sesión es obligatoria.");
  }
  const db = getFirestore();
  await assertActiveSession(auth.uid, sessionId);
  const routeSnap = await db.collection("routes").doc(routeId).get();
  if (!routeSnap.exists) {
    throw new HttpsError("not-found", "La ruta no existe.");
  }
  const stops = routeSnap.get("stops");
  if (!Array.isArray(stops) || stops.length === 0) {
    throw new HttpsError("failed-precondition", "La ruta no tiene paraderos con coordenadas.");
  }
  const startIdx = Math.min(boardingStopIndex, alightingStopIndex);
  const endIdx = Math.max(boardingStopIndex, alightingStopIndex);
  if (startIdx < 0 || endIdx >= stops.length) {
    throw new HttpsError("invalid-argument", "Los índices de paradero no son válidos.");
  }
  let distance = 0;
  for (let i = startIdx; i < endIdx; i += 1) {
    const a = stops[i];
    const b = stops[i + 1];
    if (typeof a?.lat !== "number" || typeof a?.lng !== "number" || typeof b?.lat !== "number" || typeof b?.lng !== "number") {
      throw new HttpsError("failed-precondition", "La ruta no tiene paraderos con coordenadas.");
    }
    distance += haversineMeters(a.lat, a.lng, b.lat, b.lng);
  }
  const version = await getCurrentFareVersion();
  const userSnap = await db.collection("users").doc(auth.uid).get();
  const benefit = userSnap.get("benefit");
  let validBenefit = false;
  if (benefit?.status === "approved" && benefit?.kind === "university" && benefit?.validUntil) {
    const validUntil = benefit.validUntil.toDate ? benefit.validUntil.toDate() : new Date(benefit.validUntil);
    if (validUntil.getTime() > Date.now()) validBenefit = true;
  }
  const result = computeFare({
    distanceMeters: Math.round(distance),
    version: {
      baseFarePerKmCents: version.baseFarePerKmCents,
      fuelIndex: version.fuelIndex,
      minFareCents: version.minFareCents,
      roundingStepCents: version.roundingStepCents
    },
    hasUniversityBenefit: validBenefit
  });
  return {
    distanceMeters: Math.round(distance),
    regularCents: result.regularCents,
    totalCents: result.totalCents,
    benefit: result.benefit,
    minApplied: result.minApplied,
    maxApplied: result.maxApplied,
    fareVersionId: version.id,
    isEstimate: true
  };
}

export const estimateFare = onCall(
  { region: FUNCTIONS_REGION },
  estimateFareHandler
);
