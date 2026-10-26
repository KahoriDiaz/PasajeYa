import { onRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "./config/region.js";

export { FUNCTIONS_REGION };

export { completeProfile } from "./account/completeProfile.js";
export { registerDevice } from "./account/registerDevice.js";
export { registerOperator } from "./operator/registerOperator.js";
export { assertActiveSession } from "./sessions/activeSession.js";

export interface HealthPayload {
  status: "ok";
  service: "pasajeya-functions";
}

export interface HealthHttpResponse {
  status(code: number): HealthHttpResponse;
  json(body: unknown): void;
}

export function healthHandler(
  _request: unknown,
  response: HealthHttpResponse
): void {
  const payload: HealthPayload = {
    status: "ok",
    service: "pasajeya-functions"
  };
  response.status(200);
  response.json(payload);
}

export const health = onRequest(
  { region: FUNCTIONS_REGION },
  healthHandler
);
