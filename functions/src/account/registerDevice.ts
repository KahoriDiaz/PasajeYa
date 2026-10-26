import { randomUUID } from "node:crypto";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "../config/region.js";
import { readString, requireAuth, requireRecord } from "../http/auth.js";
import { isUserRole, type UserRole } from "../types/user.js";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export interface RegisterDeviceData {
  deviceId: string;
}

export interface RegisterDeviceResult {
  sessionId: string;
  profileComplete: boolean;
  emailVerified: boolean;
  role: UserRole | null;
  operatorComplete: boolean;
}

export async function registerDeviceHandler(
  request: CallableRequest<RegisterDeviceData>
): Promise<RegisterDeviceResult> {
  const auth = requireAuth(request);
  const data = requireRecord(request.data);
  const deviceId = readString(data, "deviceId", "El dispositivo", 64);

  if (!UUID_PATTERN.test(deviceId)) {
    throw new HttpsError("invalid-argument", "El dispositivo no es válido.");
  }

  const uid = auth.uid;
  const sessionId = randomUUID();
  const db = getFirestore();
  const userRef = db.collection("users").doc(uid);
  const operatorRef = db.collection("operators").doc(uid);
  const sessionRef = db.collection("sessions").doc(uid);

  const account = await db.runTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
    const operatorSnapshot = await transaction.get(operatorRef);

    const rawRole = userSnapshot.exists ? userSnapshot.get("role") : null;
    const role = isUserRole(rawRole) ? rawRole : null;

    transaction.set(sessionRef, {
      sessionId,
      deviceId,
      updatedAt: FieldValue.serverTimestamp()
    });

    return {
      profileComplete: userSnapshot.exists,
      role,
      operatorComplete: operatorSnapshot.exists
    };
  });

  return {
    sessionId,
    profileComplete: account.profileComplete,
    emailVerified: auth.token.email_verified === true,
    role: account.role,
    operatorComplete: account.operatorComplete
  };
}

export const registerDevice = onCall(
  { region: FUNCTIONS_REGION },
  registerDeviceHandler
);
