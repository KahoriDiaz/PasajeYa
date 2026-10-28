import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "../config/region.js";
import { requireRecord, requireVerifiedAuth } from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";

export interface CalibrateFareData {
  sessionId: string;
  routeId?: string;
}

function percentile75(values: number[]): number {
  if (values.length === 0) return 0;
    const sorted = [...values].sort((a, b) => a - b);
    const pos = (sorted.length - 1) * 0.75;
    const baseIdx = Math.floor(pos);
    const rest = pos - baseIdx;
    const sBase = sorted[baseIdx];
    if (sBase === undefined) return 0;
    const sBase2 = sorted[baseIdx + 1];
    if (sBase2 !== undefined) {
      return sBase + rest * (sBase2 - sBase);
    }
    return sBase;
}

export async function calibrateFareHandler(
  request: CallableRequest<CalibrateFareData>
): Promise<{ suggestions: Array<{ routeId: string; count: number; suggestedBaseFarePerKmCents: number | null; message: string }> }> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = typeof data.sessionId === "string" ? data.sessionId : "";
  if (sessionId.length === 0) {
    throw new HttpsError("invalid-argument", "La sesión es obligatoria.");
  }
  const db = getFirestore();
  const uid = auth.uid;
  await assertActiveSession(uid, sessionId);
  const routesSnap = await db.collection("routes").get();
  const suggestions: Array<{ routeId: string; count: number; suggestedBaseFarePerKmCents: number | null; message: string }> = [];
  for (const route of routesSnap.docs) {
    const tripsSnap = await db.collection("trips").where("routeId", "==", route.id).where("status", "==", "closed").get();
    const distances: number[] = [];
    for (const trip of tripsSnap.docs) {
      const dist = trip.get("distanceMeters");
      if (typeof dist === "number" && dist > 0) {
        distances.push(dist);
      }
    }
    if (distances.length < 30) {
      suggestions.push({ routeId: route.id, count: distances.length, suggestedBaseFarePerKmCents: null, message: "datos insuficientes" });
      continue;
    }
    const p75Meters = percentile75(distances);
    const p75KmVal = (p75Meters || 0) / 1000;
    let charged = 130;
    const c1 = route.get("officialFareCents");
    if (typeof c1 === "number") charged = c1;
    else {
      const c2 = route.get("chargedFareCents");
      if (typeof c2 === "number") charged = c2;
    }
    if (p75KmVal <= 0 || !Number.isFinite(charged)) {
      suggestions.push({ routeId: route.id, count: distances.length, suggestedBaseFarePerKmCents: null, message: "datos insuficientes" });
      continue;
    }
    const suggested = Math.round((charged / p75KmVal) * 10) / 10;
    suggestions.push({ routeId: route.id, count: distances.length, suggestedBaseFarePerKmCents: Math.round(suggested), message: "sugerido" });
  }
  return { suggestions };
}

export const calibrateFare = onCall(
  { region: FUNCTIONS_REGION },
  calibrateFareHandler
);
