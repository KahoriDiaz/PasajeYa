import {
  getFirestore,
  type DocumentSnapshot,
  type Transaction
} from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export async function assertActiveSession(
  uid: string,
  sessionId: string,
  transaction?: Transaction
): Promise<DocumentSnapshot> {
  if (typeof sessionId !== "string" || sessionId.length === 0) {
    throw new HttpsError("invalid-argument", "La sesión no es válida.");
  }

  const sessionRef = getFirestore().collection("sessions").doc(uid);
  const sessionSnapshot = transaction
    ? await transaction.get(sessionRef)
    : await sessionRef.get();

  if (
    !sessionSnapshot.exists ||
    sessionSnapshot.get("sessionId") !== sessionId
  ) {
    throw new HttpsError(
      "permission-denied",
      "La sesión ya no está activa."
    );
  }

  return sessionSnapshot;
}
