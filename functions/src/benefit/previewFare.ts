import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "../config/region.js";
import {
  readString,
  requireRecord,
  requireVerifiedAuth
} from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import { getCurrentFareVersion } from "../fare/getCurrentFareVersion.js";
import { computeFare, buildFareBreakdown } from "../fare/computeFare.js";

export interface PreviewFareData {
  routeId: string;
  sessionId: string;
}

export async function previewFareHandler(
  request: CallableRequest<PreviewFareData>
) {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const routeId = readString(data, "routeId", "La ruta", 100);
  const sessionId = readString(data, "sessionId", "La sesión", 100);

  const uid = auth.uid;
  const db = getFirestore();

  await assertActiveSession(uid, sessionId);

  const routeSnap = await db.collection("routes").doc(routeId).get();
  if (!routeSnap.exists) {
    throw new HttpsError("not-found", "La ruta no existe.");
  }

  // Usamos una distancia de referencia (ej: 5km o la distancia p75 si existiera)
  // Para la previsualización comparativa, usamos 5000m.
  const referenceDistanceMeters = 5000;
  const fareVersion = await getCurrentFareVersion();

  const regular = computeFare({
    distanceMeters: referenceDistanceMeters,
    version: fareVersion,
    hasUniversityBenefit: false
  });

  const withBenefit = computeFare({
    distanceMeters: referenceDistanceMeters,
    version: fareVersion,
    hasUniversityBenefit: true
  });

  return {
    distanceMeters: referenceDistanceMeters,
    regular: buildFareBreakdown(regular, fareVersion.id, routeSnap.get("name")),
    withBenefit: buildFareBreakdown(withBenefit, fareVersion.id, routeSnap.get("name"))
  };
}

export const previewFare = onCall(
  { region: FUNCTIONS_REGION },
  previewFareHandler
);
