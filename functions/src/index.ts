import { onRequest } from "firebase-functions/v2/https";

export const FUNCTIONS_REGION = "southamerica-east1";

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
