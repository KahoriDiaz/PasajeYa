import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "../config/region.js";
import { readString, requireRecord, requireVerifiedAuth } from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";

export interface EndShiftData {
  sessionId: string;
  shiftId: string;
}

export interface EndShiftResult {
  endedAt: number;
  tripsRequiresReview: number;
}

export async function endShiftHandler(
  request: CallableRequest<EndShiftData>
): Promise<EndShiftResult> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = readString(data, "sessionId", "La sesión", 100);
  const shiftId = readString(data, "shiftId", "El turno", 100);
  const uid = auth.uid;
  const db = getFirestore();
  const shiftRef = db.collection("shifts").doc(shiftId);
  const endedAt = Timestamp.now();

  return db.runTransaction(async (transaction) => {
    await assertActiveSession(uid, sessionId, transaction);

    const shiftSnapshot = await transaction.get(shiftRef);
    if (!shiftSnapshot.exists || shiftSnapshot.get("operatorUid") !== uid) {
      throw new HttpsError("permission-denied", "Este turno no es tuyo.");
    }
    if (shiftSnapshot.get("status") !== "active") {
      const endedValue = shiftSnapshot.get("endedAt");
      return {
        endedAt:
          endedValue instanceof Timestamp
            ? endedValue.toMillis()
            : endedAt.toMillis(),
        tripsRequiresReview: 0
      };
    }

    const openTrips = await transaction.get(
      db
        .collection("trips")
        .where("shiftId", "==", shiftId)
        .where("status", "==", "open")
        .limit(400)
    );

    for (const trip of openTrips.docs) {
      transaction.update(trip.ref, {
        status: "requires_review",
        reviewReason: "shift_ended",
        endedAt
      });
    }

    const plate = shiftSnapshot.get("plate");
    transaction.update(shiftRef, {
      status: "ended",
      endedAt
    });
    if (typeof plate === "string") {
      transaction.delete(
        db.collection("shiftLocks").doc(`vehicle_${plate}`)
      );
    }
    transaction.delete(
      db.collection("shiftLocks").doc(`operator_${uid}`)
    );

    return {
      endedAt: endedAt.toMillis(),
      tripsRequiresReview: openTrips.size
    };
  });
}

export const endShift = onCall(
  { region: FUNCTIONS_REGION },
  endShiftHandler
);
