import { Timestamp, getFirestore, type DocumentSnapshot } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { qrSecret } from "../config/secrets.js";
import { FUNCTIONS_REGION } from "../config/region.js";
import {
  readString,
  requireRecord,
  requireVerifiedAuth
} from "../http/auth.js";
import {
  calculateDistance,
  type GpsPoint
} from "../geo/distance.js";
import { QrTokenError, verifyQrToken } from "../qr/token.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import {
  assertOperatorRole,
  readOptionalCoordinate,
  readOptionalNumber,
  readOptionalString,
  readTimestampMillis
} from "./shared.js";

export type TapOutStatus = "closed" | "requires_review";

export interface TapOutData {
  sessionId: string;
  qrToken?: string;
  tripId?: string;
  lat?: number;
  lng?: number;
  accuracy?: number;
}

export interface TapOutResult {
  tripId: string;
  status: TapOutStatus;
  distanceMeters: number;
  usedPoints: number;
  coverage: number;
  requiresReview: boolean;
}

export async function tapOutHandler(
  request: CallableRequest<TapOutData>
): Promise<TapOutResult> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = readString(data, "sessionId", "La sesión", 100);
  const qrToken = readOptionalString(data, "qrToken", 2_048);
  const tripId = readOptionalString(data, "tripId", 200);
  const endLocation = readOptionalCoordinate(data);
  const endAccuracy = readOptionalNumber(data, "accuracy");

  if (qrToken === undefined && tripId === undefined) {
    throw new HttpsError(
      "invalid-argument",
      "Envía el QR o el viaje que se va a cerrar."
    );
  }

  const now = new Date();
  let passengerUid: string | undefined;
  if (qrToken !== undefined) {
    try {
      passengerUid = verifyQrToken(
        qrToken,
        qrSecret.value(),
        now.getTime()
      ).uid;
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
  }

  const uid = auth.uid;
  const db = getFirestore();
  const endedAt = Timestamp.fromDate(now);

  return db.runTransaction(async (transaction) => {
    await assertActiveSession(uid, sessionId, transaction);
    await assertOperatorRole(uid, db, transaction);

    let trip: DocumentSnapshot;
    if (tripId !== undefined) {
      trip = await transaction.get(db.collection("trips").doc(tripId));
    } else {
      const shiftSnapshot = await transaction.get(
        db
          .collection("shifts")
          .where("operatorUid", "==", uid)
          .where("status", "==", "active")
          .limit(1)
      );
      if (shiftSnapshot.empty) {
        throw new HttpsError(
          "failed-precondition",
          "No tienes un turno activo."
        );
      }
      const activeShift = shiftSnapshot.docs[0];
      if (activeShift === undefined) {
        throw new HttpsError(
          "failed-precondition",
          "No tienes un turno activo."
        );
      }
      const openTrips = await transaction.get(
        db
          .collection("trips")
          .where("shiftId", "==", activeShift.id)
          .where("passengerUid", "==", passengerUid as string)
          .where("status", "==", "open")
          .limit(1)
      );
      if (openTrips.empty) {
        throw new HttpsError(
          "failed-precondition",
          "El pasajero no tiene un viaje activo en este turno."
        );
      }
      const openTrip = openTrips.docs[0];
      if (openTrip === undefined) {
        throw new HttpsError(
          "failed-precondition",
          "El pasajero no tiene un viaje activo en este turno."
        );
      }
      trip = openTrip;
    }

    if (!trip.exists) {
      throw new HttpsError("not-found", "El viaje no existe.");
    }
    if (trip.get("operatorUid") !== uid) {
      throw new HttpsError("permission-denied", "Este viaje no es tuyo.");
    }
    if (trip.get("status") !== "open") {
      throw new HttpsError(
        "failed-precondition",
        "El viaje ya está cerrado."
      );
    }
    if (
      passengerUid !== undefined &&
      trip.get("passengerUid") !== passengerUid
    ) {
      throw new HttpsError(
        "invalid-argument",
        "El QR no corresponde a este viaje."
      );
    }

    const shiftId = trip.get("shiftId");
    if (typeof shiftId !== "string") {
      throw new HttpsError(
        "failed-precondition",
        "El viaje no tiene un turno válido."
      );
    }
    const shiftSnapshot = await transaction.get(
      db.collection("shifts").doc(shiftId)
    );
    if (!shiftSnapshot.exists || shiftSnapshot.get("status") !== "active") {
      throw new HttpsError(
        "failed-precondition",
        "El turno ya terminó."
      );
    }

    const chunks = await transaction.get(
      db.collection("shifts").doc(shiftId).collection("chunks")
    );
    const points = collectPoints(chunks.docs);
    const startedAtMs = readTimestampMillis(trip.get("startedAt"));
    if (startedAtMs === null) {
      throw new HttpsError(
        "failed-precondition",
        "El viaje no tiene una fecha de inicio válida."
      );
    }

    const distance = calculateDistance(
      points,
      startedAtMs,
      now.getTime()
    );
    const requiresReview = distance.reviewReason !== undefined;
    const status: TapOutStatus = requiresReview ? "requires_review" : "closed";

    let fareBreakdown = null as any;
    if (!requiresReview && status === "closed") {
      const { getCurrentFareVersion } = await import("../fare/getCurrentFareVersion.js");
      const { computeFare, buildFareBreakdown } = await import("../fare/computeFare.js");
      const versionId = trip.get("fareVersionId") as string | undefined;
      const fareVersion = versionId
        ? await (async () => {
            const v = await db.collection("fareVersions").doc(versionId).get();
            if (!v.exists) return getCurrentFareVersion();
            return {
              id: versionId,
              baseFarePerKmCents: Number(v.get("baseFarePerKmCents") ?? 12),
              fuelIndex: Number(v.get("fuelIndex") ?? 1.0),
              fuelPriceCurrent: v.get("fuelPriceCurrent") ?? null,
              fuelPriceReference: v.get("fuelPriceReference") ?? null,
              minFareCents: Number(v.get("minFareCents") ?? 50),
              roundingStepCents: Number(v.get("roundingStepCents") ?? 5)
            };
          })()
        : await getCurrentFareVersion();
      const userSnap = await transaction.get(db.collection("users").doc(trip.get("passengerUid") as string));
      const benefit = userSnap.get("benefit");
      const hasUniversityBenefit = benefit?.status === "approved" && benefit?.kind === "university" && benefit?.validUntil;
      let validBenefit = false;
      if (hasUniversityBenefit && benefit.validUntil) {
        const validUntil = benefit.validUntil.toDate ? benefit.validUntil.toDate() : new Date(benefit.validUntil);
        if (validUntil.getTime() > now.getTime()) {
          validBenefit = true;
        }
      }
      const result = computeFare({
        distanceMeters: distance.distanceMeters,
        version: {
          baseFarePerKmCents: fareVersion.baseFarePerKmCents,
          fuelIndex: fareVersion.fuelIndex,
          minFareCents: fareVersion.minFareCents,
          roundingStepCents: fareVersion.roundingStepCents
        },
        hasUniversityBenefit: validBenefit
      });
      fareBreakdown = buildFareBreakdown(result, fareVersion.id, trip.get("routeFareSnapshot")?.concessionaireName ?? null);
    }

    transaction.update(trip.ref, {
      status,
      reviewReason: distance.reviewReason ?? null,
      endedAt,
      endLocation,
      endAccuracy: endAccuracy ?? null,
      distanceMeters: distance.distanceMeters,
      usedPoints: distance.usedPoints,
      coverage: distance.coverage,
      ...(fareBreakdown ? { fareBreakdown } : {})
    });

    return {
      tripId: trip.id,
      status,
      distanceMeters: distance.distanceMeters,
      usedPoints: distance.usedPoints,
      coverage: distance.coverage,
      requiresReview,
      ...(fareBreakdown ? { fareBreakdown } : {})
    };
  });
}

function collectPoints(
  chunks: DocumentSnapshot[]
): GpsPoint[] {
  const points: GpsPoint[] = [];

  for (const chunk of chunks) {
    const rawPoints = chunk.get("points");
    if (!Array.isArray(rawPoints)) continue;

    for (const rawPoint of rawPoints) {
      if (typeof rawPoint !== "object" || rawPoint === null) continue;
      const point = rawPoint as Record<string, unknown>;
      const t = readTimestampMillis(point.t);
      if (
        typeof point.lat !== "number" ||
        typeof point.lng !== "number" ||
        typeof point.acc !== "number" ||
        t === null
      ) {
        continue;
      }
      points.push({
        lat: point.lat,
        lng: point.lng,
        acc: point.acc,
        t
      });
    }
  }

  return points;
}

export const tapOut = onCall(
  { region: FUNCTIONS_REGION, secrets: [qrSecret] },
  tapOutHandler
);
