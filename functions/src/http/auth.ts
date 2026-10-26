import {
  HttpsError,
  type CallableRequest
} from "firebase-functions/v2/https";

type AuthData = NonNullable<CallableRequest<unknown>["auth"]>;

export function requireAuth<T>(request: CallableRequest<T>): AuthData {
  if (request.auth === undefined) {
    throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  }

  return request.auth;
}

export function requireVerifiedAuth<T>(request: CallableRequest<T>): AuthData {
  const auth = requireAuth(request);
  if (auth.token.email_verified !== true) {
    throw new HttpsError(
      "failed-precondition",
      "Verifica tu correo antes de continuar."
    );
  }

  return auth;
}

export function requireRecord(value: unknown): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", "Los datos enviados no son válidos.");
  }

  return value as Record<string, unknown>;
}

export function readString(
  data: Record<string, unknown>,
  field: string,
  label: string,
  maxLength: number
): string {
  const value = data[field];
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
    throw new HttpsError("invalid-argument", `${label} no es válido.`);
  }

  return value;
}
