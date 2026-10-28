import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { qrSecret } from "../config/secrets.js";
import { FUNCTIONS_REGION } from "../config/region.js";
import {
  readString,
  requireRecord,
  requireVerifiedAuth
} from "../http/auth.js";
import { QrTokenError, verifyQrToken } from "../qr/token.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import {
  assertOperatorRole,
  readCoordinate,
  readOptionalNumber,
  readPassengerProfile
} from "./shared.js";
import { getCurrentFareVersion } from "../fare/getCurrentFareVersion.js";

export interface TapInData {
  sessionId: string;
  shiftId: string;
  qrToken: string;
  lat: number;
  lng: number;
  accuracy?: number;
}

export interface TapInResult {
  tripId: string;
  firstName: string;
  age: number;
}

export async function tapInHandler(
  request: CallableRequest<TapInData>
): Promise<TapInResult> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = readString(data, "sessionId", "La sesión", 100);
  const shiftId = readString(data, "shiftId", "El turno", 100);
  const qrToken = readString(data, "qrToken", "El c\u00f3digo QR", 2_048);
  const startLocation = readCoordinate(data);
  const startAccuracy = readOptionalNumber(data, "accuracy");

  const now = new Date();

  let passengerUid: string;
  let nonce: string;
  try {
    const payload = verifyQrToken(qrToken, qrSecret.value(), now.getTime());
    passengerUid = payload.uid;
    nonce = payload.nonce;
  } catch (error) {
    if (error instanceof QrTokenError) {
      throw new HttpsError(
        error.failure === "expired" ? "failed-precondition" : "invalid-argument",
        error.failure === "expired"
          ? "El código QR expiró. Pídele otro al pasajero."
          : "El código QR no es válido."
      );
    }
    throw error;
  }

  const uid = auth.uid;
  const db = getFirestore();
  const tripRef = db.collection("trips").doc();
  const nowTimestamp = Timestamp.fromDate(now);

  const fareVersion = await getCurrentFareVersion();

  return db.runTransaction(async (transaction) => {
    await assertActiveSession(uid, sessionId, transaction);
    await assertOperatorRole(uid, db, transaction);

    const shiftSnapshot = await transaction.get(
      db.collection("shifts").doc(shiftId)
    );
    if (
      !shiftSnapshot.exists ||
      shiftSnapshot.get("operatorUid") !== uid ||
      shiftSnapshot.get("status") !== "active"
    ) {
      throw new HttpsError(
        "failed-precondition",
        "Inicia un turno antes de escanear pasajeros."
      );
    }
    const routeId = shiftSnapshot.get("routeId");
    if (typeof routeId !== "string") {
      throw new HttpsError(
        "failed-precondition",
        "El turno no tiene una ruta v��lida."
      );
    }
    const routeSnapshot = await transaction.get(db.collection("routes").doc(routeId));
    const routeFareSnapshot = routeSnapshot.exists
      ? {
          officialFareCents: routeSnapshot.get("officialFareCents") ?? routeSnapshot.get("chargedFareCents") ?? null,
          officialUniversityFareCents: routeSnapshot.get("officialUniversityFareCents") ?? null,
          fareSourceUrl: routeSnapshot.get("fareSourceUrl") ?? null,
          fareCheckedAt: routeSnapshot.get("fareCheckedAt") ?? null,
          concessionaireName: routeSnapshot.get("concessionaireName") ?? routeSnapshot.get("name") ?? null
        }
      : null;

    const nonceRef = db.collection("qrNonces").doc(nonce);
    const nonceSnapshot = await transaction.get(nonceRef);
    if (
      !nonceSnapshot.exists ||
      nonceSnapshot.get("uid") !== passengerUid
    ) {
      throw new HttpsError(
        "failed-precondition",
        "El código QR ya fue utilizado. Pídele otro al pasajero."
      );
    }
    const nonceExpiresAt = nonceSnapshot.get("expiresAt");
    if (
      !(nonceExpiresAt instanceof Timestamp) ||
      nonceExpiresAt.toMillis() <= now.getTime()
    ) {
      throw new HttpsError(
        "failed-precondition",
        "El código QR expiró. Pídele otro al pasajero."
      );
    }

    const activePassengerTrip = await transaction.get(
      db
        .collection("trips")
        .where("passengerUid", "==", passengerUid)
        .where("status", "==", "open")
        .limit(1)
    );
    if (!activePassengerTrip.empty) {
      throw new HttpsError(
        "failed-precondition",
        "El pasajero ya tiene un viaje activo."
      );
    }

    const passenger = await readPassengerProfile(
      passengerUid,
      db,
      transaction,
      now
    );
    // fareVersion handled above

    transaction.create(tripRef, {
      passengerUid,
      operatorUid: uid,
      shiftId,
      routeId,
      status: "open",
      reviewReason: null,
      startedAt: nowTimestamp,
      endedAt: null,
      startLocation,
      startAccuracy: startAccuracy ?? null,
      endLocation: null,
      endAccuracy: null,
      fareVersionId: fareVersion.id,
      routeFareSnapshot,
      distanceMeters: 0,
      usedPoints: 0,
      coverage: 0,
      passengerFirstName: passenger.firstName,
      passengerAge: passenger.age
    });
    transaction.delete(nonceRef);

    return {
      tripId: tripRef.id,
      firstName: passenger.firstName,
      age: passenger.age
    };
  });
}

export const tapIn = onCall(
  { region: FUNCTIONS_REGION, secrets: [qrSecret] },
  tapInHandler
);
