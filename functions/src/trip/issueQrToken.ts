import { Timestamp, getFirestore } from "firebase-admin/firestore";
import {
  HttpsError,
  onCall,
  type CallableRequest
} from "firebase-functions/v2/https";
import { qrSecret } from "../config/secrets.js";
import { FUNCTIONS_REGION } from "../config/region.js";
import { readString, requireRecord, requireVerifiedAuth } from "../http/auth.js";
import { createQrToken } from "../qr/token.js";
import { assertActiveSession } from "../sessions/activeSession.js";

const TOKEN_TTL_SECONDS = 60;
const NONCE_TTL_MS = 10 * 60 * 1_000;

export interface IssueQrTokenData {
  sessionId: string;
}

export interface IssueQrTokenResult {
  qrToken: string;
  expiresAt: number;
}

export async function issueQrTokenHandler(
  request: CallableRequest<IssueQrTokenData>
): Promise<IssueQrTokenResult> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = readString(data, "sessionId", "La sesión", 100);

  await assertActiveSession(auth.uid, sessionId);

  const db = getFirestore();
  const userSnapshot = await db.collection("users").doc(auth.uid).get();
  if (!userSnapshot.exists) {
    throw new HttpsError("failed-precondition", "El perfil no está completo.");
  }
  if (userSnapshot.get("role") !== "passenger") {
    throw new HttpsError(
      "failed-precondition",
      "Esta cuenta no es de pasajero."
    );
  }

  const created = createQrToken(
    auth.uid,
    qrSecret.value(),
    Date.now(),
    TOKEN_TTL_SECONDS
  );

  await db.collection("qrNonces").doc(created.payload.nonce).create({
    uid: auth.uid,
    expiresAt: Timestamp.fromMillis(Date.now() + NONCE_TTL_MS)
  });

  return {
    qrToken: created.token,
    expiresAt: created.expiresAt
  };
}

export const issueQrToken = onCall(
  { region: FUNCTIONS_REGION, secrets: [qrSecret] },
  issueQrTokenHandler
);
