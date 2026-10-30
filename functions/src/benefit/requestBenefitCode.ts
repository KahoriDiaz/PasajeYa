import { getFirestore, Timestamp, FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { carnePepper } from "../config/secrets.js";
import { FUNCTIONS_REGION } from "../config/region.js";
import {
  readString,
  requireRecord,
  requireVerifiedAuth
} from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import {
  computeHmac,
  generate6DigitCode,
  isValidInstitutionalEmail
} from "./logic.js";
import { ConsoleEmailProvider } from "./email.js";
import {
  CODE_EXPIRY_MINUTES,
  EMAIL_MUST_MATCH_CODE,
  MAX_CODE_REQUESTS_PER_DAY,
  MAX_CODE_REQUESTS_PER_HOUR
} from "../config/benefit.js";

export interface RequestCodeData {
  institutionalEmail: string;
  sessionId: string;
}

export async function requestBenefitCodeHandler(
  request: CallableRequest<RequestCodeData>
): Promise<{ status: "ok" }> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const email = readString(data, "institutionalEmail", "El correo institucional", 100).toLowerCase();
  const sessionId = readString(data, "sessionId", "La sesión", 100);

  const uid = auth.uid;
  const db = getFirestore();

  await assertActiveSession(uid, sessionId);

  // 1. Validar que pasó el carné
  const requestDoc = await db.collection("benefitRequests").doc(uid).get();
  if (!requestDoc.exists || requestDoc.get("stage") !== "carne_ok") {
    throw new HttpsError(
      "failed-precondition",
      "Primero debes subir la foto de tu carné y esperar a que sea aprobada."
    );
  }

  // 2. Validar formato de correo y dominio
  if (!isValidInstitutionalEmail(email)) {
    throw new HttpsError("invalid-argument", "El correo no pertenece a una universidad autorizada.");
  }

  // 3. Validar que el correo coincida con el código (si aplica)
  if (EMAIL_MUST_MATCH_CODE) {
    const localPart = email.split("@")[0]!;
    const studentCodeHash = requestDoc.get("carneCodeHash");
    const emailCodeHash = computeHmac(localPart, carnePepper.value());
    if (studentCodeHash !== emailCodeHash) {
      throw new HttpsError(
        "invalid-argument",
        "El correo debe corresponder al código de estudiante detectado en el carné."
      );
    }
  }

  // 4. Límites de frecuencia
  const now = new Date();
  const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);
  const today = new Date(now.getTime());
  today.setHours(0, 0, 0, 0);

  const hourAttempts = await db.collection("benefitAttempts")
    .where("uid", "==", uid)
    .where("type", "==", "code_request")
    .where("createdAt", ">=", Timestamp.fromDate(oneHourAgo))
    .get();

  if (hourAttempts.size >= MAX_CODE_REQUESTS_PER_HOUR) {
    throw new HttpsError("resource-exhausted", "Demasiados códigos solicitados. Intenta en una hora.");
  }

  const dayAttempts = await db.collection("benefitAttempts")
    .where("uid", "==", uid)
    .where("type", "==", "code_request")
    .where("createdAt", ">=", Timestamp.fromDate(today))
    .get();

  if (dayAttempts.size >= MAX_CODE_REQUESTS_PER_DAY) {
    throw new HttpsError("resource-exhausted", "Límite diario de códigos alcanzado.");
  }

  // 5. Generar y enviar código
  const code = generate6DigitCode();
  const codeHash = computeHmac(code, carnePepper.value());
  const expiresAt = Timestamp.fromMillis(Date.now() + CODE_EXPIRY_MINUTES * 60 * 1000);

  // Usamos el proveedor de consola por ahora (Step 4 pide transporte de prueba)
  const emailProvider = new ConsoleEmailProvider();
  await emailProvider.sendVerificationCode(email, code);

  // 6. Guardar en la solicitud
  await db.collection("benefitRequests").doc(uid).update({
    stage: "code_sent",
    emailHash: computeHmac(email, carnePepper.value()),
    codeHash: codeHash,
    codeExpiresAt: expiresAt,
    attempts: 0,
    updatedAt: FieldValue.serverTimestamp()
  });

  // Registrar intento
  await db.collection("benefitAttempts").add({
    uid,
    type: "code_request",
    status: "success",
    createdAt: FieldValue.serverTimestamp()
  });

  return { status: "ok" };
}

export const requestBenefitCode = onCall(
  { region: FUNCTIONS_REGION, secrets: [carnePepper] },
  requestBenefitCodeHandler
);
