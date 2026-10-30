import { getApps, initializeApp } from "firebase-admin/app";
import { onRequest } from "firebase-functions/v2/https";
import { FUNCTIONS_REGION } from "./config/region.js";

if (getApps().length === 0) {
  initializeApp();
}

export { FUNCTIONS_REGION };

export { completeProfile } from "./account/completeProfile.js";
export { registerDevice } from "./account/registerDevice.js";
export { registerOperator } from "./operator/registerOperator.js";
export { assertActiveSession } from "./sessions/activeSession.js";
export { closeStaleTrips } from "./trip/closeStaleTrips.js";
export { endShift } from "./trip/endShift.js";
export { getOperatorContext } from "./trip/getOperatorContext.js";
export { issueQrToken } from "./trip/issueQrToken.js";
export { startShift } from "./trip/startShift.js";
export { tapIn } from "./trip/tapIn.js";
export { tapOut } from "./trip/tapOut.js";
export { publishFareVersion } from "./fare/publishFareVersion.js";
export { calibrateFare } from "./fare/calibrateFare.js";
export { estimateFare } from "./trip/estimateFare.js";

// F4 - Beneficio Universitario
export { submitCarne } from "./benefit/submitCarne.js";
export { requestBenefitCode } from "./benefit/requestBenefitCode.js";
export { confirmBenefitCode } from "./benefit/confirmBenefitCode.js";
export { previewFare } from "./benefit/previewFare.js";

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
