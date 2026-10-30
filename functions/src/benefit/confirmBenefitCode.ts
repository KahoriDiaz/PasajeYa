import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { carnePepper } from "../config/secrets.js";
import { FUNCTIONS_REGION } from "../config/region.js";
import {
  readString,
  requireRecord,
  requireVerifiedAuth
} from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import { computeHmac, safeCompare } from "./logic.js";
import { BENEFIT_VALID_DAYS, MAX_CODE_ATTEMPTS } from "../config/benefit.js";

export interface ConfirmCodeData {
  code: string;
  sessionId: string;
}

export async function confirmBenefitCodeHandler(
  request: CallableRequest<ConfirmCodeData>
): Promise<{ status: "approved"; validUntil: number }> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const code = readString(data, "code", "El código", 10);
  const sessionId = readString(data, "sessionId", "La sesión", 100);

  const uid = auth.uid;
  const db = getFirestore();

  await assertActiveSession(uid, sessionId);

  const requestRef = db.collection("benefitRequests").doc(uid);

  return db.runTransaction(async (transaction) => {
    const requestDoc = await transaction.get(requestRef);
    if (!requestDoc.exists || requestDoc.get("stage") !== "code_sent") {
      throw new HttpsError(
        "failed-precondition",
        "No tienes una solicitud de beneficio en espera de código."
      );
    }

    const attempts = requestDoc.get("attempts") || 0;
    if (attempts >= MAX_CODE_ATTEMPTS) {
      transaction.delete(requestRef);
      throw new HttpsError(
        "resource-exhausted",
        "Has superado el número de intentos permitidos. Empieza de nuevo."
      );
    }

    const expiresAt = requestDoc.get("codeExpiresAt") as Timestamp;
    if (expiresAt.toMillis() < Date.now()) {
      transaction.delete(requestRef);
      throw new HttpsError(
        "failed-precondition",
        "El código ha expirado. Empieza de nuevo."
      );
    }

    const storedHash = requestDoc.get("codeHash");
    const inputHash = computeHmac(code, carnePepper.value());

    if (!safeCompare(storedHash, inputHash)) {
      const newAttempts = attempts + 1;
      transaction.update(requestRef, {
        attempts: newAttempts,
        updatedAt: FieldValue.serverTimestamp()
      });
      throw new HttpsError(
        "invalid-argument",
        `Código incorrecto. Te quedan ${MAX_CODE_ATTEMPTS - newAttempts} intentos.`
      );
    }

    // Código correcto -> Activar beneficio
    const validUntil = new Date();
    validUntil.setDate(validUntil.getDate() + BENEFIT_VALID_DAYS);
    const validUntilTs = Timestamp.fromDate(validUntil);

    const carneCodeHash = requestDoc.get("carneCodeHash");

    // 1. Guardar en users
    transaction.update(db.collection("users").doc(uid), {
      benefit: {
        status: "approved",
        kind: "university",
        percent: 50,
        validUntil: validUntilTs,
        verifiedAt: FieldValue.serverTimestamp()
      }
    });

    // 2. Indexar carné para evitar duplicados
    transaction.set(db.collection("carneIndex").doc(carneCodeHash), {
      uid: uid,
      createdAt: FieldValue.serverTimestamp()
    });

    // 3. Opcional: Indexar correo institucional si se desea unicidad de correo
    // transaction.set(db.collection("institutionalEmailIndex").doc(emailHash), { uid });

    // 4. Borrar solicitud
    transaction.delete(requestRef);

    return {
      status: "approved",
      validUntil: validUntil.getTime()
    };
  });
}

export const confirmBenefitCode = onCall(
  { region: FUNCTIONS_REGION, secrets: [carnePepper] },
  confirmBenefitCodeHandler
);
