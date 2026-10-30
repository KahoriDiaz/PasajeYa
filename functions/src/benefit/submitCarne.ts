import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
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
  compareNames,
  computeHmac,
  containsUniversityKeywords,
  extractStudentCode,
  isExpired
} from "./logic.js";
import { validateImageQuality } from "./image.js";
import { CloudVisionRecognizer } from "./vision.js";
import { MAX_CARNE_SUBMISSIONS_PER_DAY } from "../config/benefit.js";

export interface SubmitCarneData {
  requestId: string;
  sessionId: string;
}

export async function submitCarneHandler(
  request: CallableRequest<SubmitCarneData>
): Promise<{ status: "ok" }> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const requestId = readString(data, "requestId", "El ID de solicitud", 100);
  const sessionId = readString(data, "sessionId", "La sesión", 100);

  const uid = auth.uid;
  const db = getFirestore();

  await assertActiveSession(uid, sessionId);

  // 1. Verificar límites (3 por día)
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const attemptsSnap = await db.collection("benefitAttempts")
    .where("uid", "==", uid)
    .where("type", "==", "carne")
    .where("createdAt", ">=", Timestamp.fromDate(today))
    .get();

  if (attemptsSnap.size >= MAX_CARNE_SUBMISSIONS_PER_DAY) {
    throw new HttpsError(
      "resource-exhausted",
      "Has alcanzado el límite de intentos por hoy. Inténtalo mañana."
    );
  }

  // 2. Descargar imagen de Storage
  const bucket = getStorage().bucket();
  const filePath = `benefitUploads/${uid}/${requestId}.jpg`;
  const file = bucket.file(filePath);

  const [exists] = await file.exists();
  if (!exists) {
    throw new HttpsError("not-found", "No se encontró la imagen del carné.");
  }

  let imageBuffer: Buffer;
  try {
    const [content] = await file.download();
    imageBuffer = content;
  } catch (error) {
    throw new HttpsError("internal", "No se pudo leer la imagen subida.");
  }

  const recognizer = new CloudVisionRecognizer();
  let resultStatus: "success" | "error" = "error";
  let rejectionReason: string | null = null;

  try {
    // 3. Comprobar calidad
    const quality = await validateImageQuality(imageBuffer);
    if (!quality.isValid) {
      rejectionReason = quality.reason ?? "Calidad de imagen insuficiente.";
      throw new Error(rejectionReason);
    }

    // 4. OCR
    const ocrText = await recognizer.recognizeText(imageBuffer);

    // 5. Análisis del texto
    if (!containsUniversityKeywords(ocrText)) {
      rejectionReason = "No se reconoce la universidad autorizada en el carné.";
      throw new Error(rejectionReason);
    }

    const studentCode = extractStudentCode(ocrText);
    if (!studentCode) {
      rejectionReason = "No se pudo leer un código de estudiante válido.";
      throw new Error(rejectionReason);
    }

    const userSnap = await db.collection("users").doc(uid).get();
    const userData = userSnap.data();
    if (!userData) throw new Error("Usuario no encontrado.");

    if (!compareNames(userData.firstName, userData.lastName, ocrText)) {
      rejectionReason = "El nombre en el carné no coincide con tu cuenta.";
      throw new Error(rejectionReason);
    }

    if (isExpired(ocrText)) {
      rejectionReason = "El carné parece estar vencido.";
      throw new Error(rejectionReason);
    }

    // 6. Verificar duplicidad de carné
    const hmacCode = computeHmac(studentCode, carnePepper.value());
    const existingCarne = await db.collection("carneIndex").doc(hmacCode).get();
    if (existingCarne.exists && existingCarne.get("uid") !== uid) {
      rejectionReason = "Este carné ya está registrado en otra cuenta.";
      throw new Error(rejectionReason);
    }

    // Todo OK -> Guardar solicitud
    await db.runTransaction(async (transaction) => {
      transaction.set(db.collection("benefitRequests").doc(uid), {
        stage: "carne_ok",
        carneCodeHash: hmacCode,
        carneVerifiedAt: FieldValue.serverTimestamp(),
        expiresAt: Timestamp.fromMillis(Date.now() + 24 * 60 * 60 * 1000), // 24h para completar
        attempts: 0,
        updatedAt: FieldValue.serverTimestamp()
      });
    });

    resultStatus = "success";
  } catch (error: any) {
    rejectionReason = error.message;
  } finally {
    // 7. Borrar imagen SIEMPRE
    await file.delete().catch(console.error);

    // Registrar intento
    await db.collection("benefitAttempts").add({
      uid,
      type: "carne",
      status: resultStatus,
      reason: rejectionReason,
      createdAt: FieldValue.serverTimestamp()
    });
  }

  if (resultStatus === "error") {
    throw new HttpsError("invalid-argument", rejectionReason ?? "Error validando carné.");
  }

  return { status: "ok" };
}

import { FieldValue } from "firebase-admin/firestore";

export const submitCarne = onCall(
  { region: FUNCTIONS_REGION, secrets: [carnePepper] },
  submitCarneHandler
);
