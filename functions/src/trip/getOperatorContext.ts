import { getFirestore } from "firebase-admin/firestore";
import { onCall, type CallableRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "../config/region.js";
import { readString, requireRecord, requireVerifiedAuth } from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import { assertOperatorRole, readOperatorProfile } from "./shared.js";

export interface OperatorContextData {
  sessionId: string;
}

export interface OperatorContextResult {
  plate: string;
  routeIds: string[];
  status: string;
}

export async function getOperatorContextHandler(
  request: CallableRequest<OperatorContextData>
): Promise<OperatorContextResult> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = readString(data, "sessionId", "La sesión", 100);
  const db = getFirestore();

  await assertActiveSession(auth.uid, sessionId);
  await assertOperatorRole(auth.uid, db);
  const operator = await readOperatorProfile(auth.uid, db);

  return operator;
}

export const getOperatorContext = onCall(
  { region: FUNCTIONS_REGION },
  getOperatorContextHandler
);
