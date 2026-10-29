import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "../config/region.js";
import { readString, requireRecord, requireVerifiedAuth } from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import {
  assertOperatorRole,
  readOperatorProfile,
  readOptionalString
} from "./shared.js";

export interface StartShiftData {
  sessionId: string;
  routeId: string;
  vehiclePlate?: string;
}

export interface StartShiftResult {
  shiftId: string;
  startedAt: number;
}

export async function startShiftHandler(
  request: CallableRequest<StartShiftData>
): Promise<StartShiftResult> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = readString(data, "sessionId", "La sesión", 100);
  const routeId = readString(data, "routeId", "La ruta", 100);
  const vehiclePlate = readOptionalString(data, "vehiclePlate", 10);
  const uid = auth.uid;
  const db = getFirestore();
  const shiftRef = db.collection("shifts").doc();
  const operatorLockRef = db.collection("shiftLocks").doc(`operator_${uid}`);
  const startedAt = Timestamp.now();

  return db.runTransaction(async (transaction) => {
    await assertActiveSession(uid, sessionId, transaction);
    await assertOperatorRole(uid, db, transaction);
    const operator = await readOperatorProfile(uid, db, transaction);

    if (
      vehiclePlate !== undefined &&
      vehiclePlate.toUpperCase() !== operator.plate.toUpperCase()
    ) {
      throw new HttpsError(
        "permission-denied",
        "El vehículo no está registrado a tu nombre."
      );
    }

    if (!operator.routeIds.includes(routeId)) {
      throw new HttpsError(
        "permission-denied",
        "La ruta no está asignada a este operador."
      );
    }

    const routeSnapshot = await transaction.get(
      db.collection("routes").doc(routeId)
    );
    if (!routeSnapshot.exists || routeSnapshot.get("active") !== true) {
      throw new HttpsError(
        "failed-precondition",
        "La ruta ya no está disponible."
      );
    }

    const vehicleLockRef = db
      .collection("shiftLocks")
      .doc(`vehicle_${operator.plate}`);
    const locks = await transaction.getAll(operatorLockRef, vehicleLockRef);

    for (const lock of locks) {
      if (!lock.exists) continue;
      const lockedShiftId = lock.get("shiftId");
      if (typeof lockedShiftId !== "string") {
        throw new HttpsError(
          "failed-precondition",
          "El turno anterior no se cerró correctamente."
        );
      }

      const lockedShift = await transaction.get(
        db.collection("shifts").doc(lockedShiftId)
      );
      if (lockedShift.exists && lockedShift.get("status") === "active") {
        throw new HttpsError(
          "failed-precondition",
          "Ya existe un turno activo."
        );
      }
      transaction.delete(lock.ref);
    }

    transaction.create(shiftRef, {
      operatorUid: uid,
      plate: operator.plate,
      routeId,
      startedAt,
      endedAt: null,
      status: "active"
    });
    transaction.create(operatorLockRef, {
      shiftId: shiftRef.id,
      kind: "operator",
      uid
    });
    transaction.create(vehicleLockRef, {
      shiftId: shiftRef.id,
      kind: "vehicle",
      plate: operator.plate
    });

    return { shiftId: shiftRef.id, startedAt: startedAt.toMillis() };
  });
}

export const startShift = onCall(
  { region: FUNCTIONS_REGION },
  startShiftHandler
);
