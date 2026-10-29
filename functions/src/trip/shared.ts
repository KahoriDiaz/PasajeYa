import {
  GeoPoint,
  Timestamp,
  type DocumentSnapshot,
  type Firestore,
  type Transaction
} from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export interface OperatorProfile {
  plate: string;
  routeIds: string[];
  status: string;
}

export interface PassengerProfile {
  firstName: string;
  age: number;
}

export async function readOperatorProfile(
  uid: string,
  db: Firestore,
  transaction?: Transaction
): Promise<OperatorProfile> {
  const operatorRef = db.collection("operators").doc(uid);
  const snapshot = transaction
    ? await transaction.get(operatorRef)
    : await operatorRef.get();

  if (!snapshot.exists) {
    throw new HttpsError(
      "failed-precondition",
      "Completa el registro de operador."
    );
  }

  const plate = snapshot.get("plate");
  const routeIds = snapshot.get("routeIds");
  if (
    typeof plate !== "string" ||
    plate.length === 0 ||
    !Array.isArray(routeIds) ||
    !routeIds.every((routeId) => typeof routeId === "string")
  ) {
    throw new HttpsError(
      "failed-precondition",
      "El perfil de operador no está completo."
    );
  }

  return {
    plate,
    routeIds,
    status: typeof snapshot.get("status") === "string"
      ? snapshot.get("status")
      : "unverified"
  };
}

export async function assertOperatorRole(
  uid: string,
  db: Firestore,
  transaction?: Transaction
): Promise<void> {
  const userRef = db.collection("users").doc(uid);
  const snapshot = transaction
    ? await transaction.get(userRef)
    : await userRef.get();

  if (!snapshot.exists) {
    throw new HttpsError("failed-precondition", "El perfil no está completo.");
  }
  if (snapshot.get("role") !== "operator") {
    throw new HttpsError(
      "failed-precondition",
      "Esta cuenta no es de operador."
    );
  }
}

export async function readPassengerProfile(
  uid: string,
  db: Firestore,
  transaction: Transaction,
  now: Date
): Promise<PassengerProfile> {
  const snapshot = await transaction.get(
    db.collection("users").doc(uid)
  );

  if (!snapshot.exists) {
    throw new HttpsError("failed-precondition", "El pasajero no tiene perfil.");
  }
  if (snapshot.get("role") !== "passenger") {
    throw new HttpsError(
      "failed-precondition",
      "El QR no pertenece a un pasajero."
    );
  }

  const firstName = snapshot.get("firstName");
  const birthDate = snapshot.get("birthDate");
  if (typeof firstName !== "string" || typeof birthDate !== "string") {
    throw new HttpsError(
      "failed-precondition",
      "El perfil del pasajero no está completo."
    );
  }

  const age = calculateAge(birthDate, now);
  if (age === null) {
    throw new HttpsError(
      "failed-precondition",
      "La fecha de nacimiento del pasajero no es válida."
    );
  }

  return { firstName, age };
}

export function calculateAge(birthDate: string, now: Date): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDate);
  if (match === null) return null;

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const birth = new Date(Date.UTC(year, month - 1, day));
  if (
    birth.getUTCFullYear() !== year ||
    birth.getUTCMonth() !== month - 1 ||
    birth.getUTCDate() !== day
  ) {
    return null;
  }

  let age = now.getUTCFullYear() - year;
  const hasHadBirthday =
    now.getUTCMonth() > month - 1 ||
    (now.getUTCMonth() === month - 1 && now.getUTCDate() >= day);
  if (!hasHadBirthday) age -= 1;

  return age >= 0 && age <= 130 ? age : null;
}

export function readCoordinate(data: Record<string, unknown>): GeoPoint {
  const lat = data.lat;
  const lng = data.lng;
  if (
    typeof lat !== "number" ||
    !Number.isFinite(lat) ||
    lat < -90 ||
    lat > 90 ||
    typeof lng !== "number" ||
    !Number.isFinite(lng) ||
    lng < -180 ||
    lng > 180
  ) {
    throw new HttpsError(
      "invalid-argument",
      "La ubicación del vehículo no es válida."
    );
  }

  return new GeoPoint(lat, lng);
}

export function readOptionalCoordinate(
  data: Record<string, unknown>
): GeoPoint | null {
  if (data.lat === undefined && data.lng === undefined) return null;
  return readCoordinate(data);
}

export function readRequiredNumber(
  data: Record<string, unknown>,
  field: string
): number {
  const value = data[field];
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new HttpsError(
      "invalid-argument",
      `El campo ${field} no es válido.`
    );
  }
  return value;
}

export function readOptionalNumber(
  data: Record<string, unknown>,
  field: string
): number | undefined {
  const value = data[field];
  if (value === undefined || value === null) return undefined;
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) {
    throw new HttpsError(
      "invalid-argument",
      `El campo ${field} no es válido.`
    );
  }
  return value;
}

export function readOptionalString(
  data: Record<string, unknown>,
  field: string,
  maxLength: number
): string | undefined {
  const value = data[field];
  if (value === undefined || value === null || value === "") return undefined;
  if (typeof value !== "string" || value.length > maxLength) {
    throw new HttpsError("invalid-argument", "Los datos no son válidos.");
  }
  return value;
}

export function readTimestampMillis(value: unknown): number | null {
  if (value instanceof Timestamp) return value.toMillis();
  if (value instanceof Date) return value.getTime();
  if (typeof value === "number" && Number.isFinite(value)) return value;
  return null;
}

export function readDocumentTimestampMillis(
  snapshot: DocumentSnapshot,
  field: string
): number {
  const value = readTimestampMillis(snapshot.get(field));
  if (value === null) {
    throw new HttpsError(
      "failed-precondition",
      "El turno no tiene una fecha de inicio válida."
    );
  }
  return value;
}
