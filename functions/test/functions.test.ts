import { createHmac, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import {
  deleteApp as deleteAdminApp,
  initializeApp as initializeAdminApp
} from "firebase-admin/app";
import { getAuth as getAdminAuth } from "firebase-admin/auth";
import { Timestamp as AdminTimestamp, getFirestore as getAdminFirestore } from "firebase-admin/firestore";
import {
  deleteApp as deleteClientApp,
  initializeApp as initializeClientApp,
  type FirebaseApp
} from "firebase/app";
import {
  connectAuthEmulator,
  getAuth,
  signInWithEmailAndPassword,
  type Auth
} from "firebase/auth";
import {
  connectFunctionsEmulator,
  getFunctions,
  httpsCallable,
  type Functions
} from "firebase/functions";
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it
} from "vitest";

const PROJECT_ID = "demo-pasajeya";
const FUNCTIONS_REGION = "southamerica-east1";
const EMULATOR_HOST = "127.0.0.1";
const AUTH_EMULATOR_URL = "http://127.0.0.1:9099";
const PASSWORD = "Abcdefgh1!";
const ROUTE_ID = "route-test";
const RUN_INTEGRATION = process.env.PASAJEYA_EMULATOR_TESTS === "1";

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
}
if (!process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
}

const adminApp = initializeAdminApp(
  { projectId: PROJECT_ID },
  "f1-integration-admin"
);
const defaultAdminApp = initializeAdminApp({ projectId: PROJECT_ID });
const adminAuth = getAdminAuth(adminApp);
const adminDb = getAdminFirestore(adminApp);
const clientApps = new Set<FirebaseApp>();

interface SignedInClient {
  app: FirebaseApp;
  auth: Auth;
  functions: Functions;
  uid: string;
}

interface CompleteProfileResult {
  completed: true;
  alreadyCompleted: boolean;
  role: UserRole;
}

interface RegisterDeviceResult {
  sessionId: string;
  profileComplete: boolean;
  emailVerified: boolean;
  role: UserRole | null;
  operatorComplete: boolean;
}

interface RegisterOperatorResult {
  registered: true;
  alreadyRegistered: boolean;
  plate: string;
  routeIds: string[];
  status: string;
}

type UserRole = "passenger" | "operator";

let pepper = "";
let emailSequence = 0;
let dniSequence = 20_000_000;

describe.skipIf(!RUN_INTEGRATION)("F1 callable functions", () => {
  beforeAll(async () => {
    pepper = await readPepper();
  });

  beforeEach(async () => {
    await clearEmulatorData();
    await adminDb.collection("routes").doc(ROUTE_ID).set({
      name: "Ruta de prueba",
      active: true,
      stops: [{ name: "Paradero de prueba", lat: -16.4, lng: -71.5 }]
    });
  });

  afterEach(async () => {
    await Promise.allSettled(
      [...clientApps].map((app) => deleteClientApp(app))
    );
    clientApps.clear();
  });



  it("creates the public profile, private DNI, and DNI index", async () => {
    const client = await createClient();
    const registration = await callFunction<RegisterDeviceResult>(
      client,
      "registerDevice",
      { deviceId: randomUUID() }
    );

    expect(registration).toMatchObject({
      profileComplete: false,
      emailVerified: false,
      role: null,
      operatorComplete: false
    });

    const dni = "12345678";
    const result = await completeProfile(
      client,
      registration.sessionId,
      "passenger",
      { dni }
    );

    expect(result).toMatchObject({
      completed: true,
      alreadyCompleted: false,
      role: "passenger"
    });

    const userSnapshot = await adminDb.collection("users").doc(client.uid).get();
    const userData = userSnapshot.data();
    expect(userData?.role).toBe("passenger");
    expect(userData?.privacyConsent).toMatchObject({ version: "1.0" });
    expect(JSON.stringify(userData)).not.toContain(dni);

    const privateSnapshot = await adminDb
      .collection("users_private")
      .doc(client.uid)
      .get();
    expect(privateSnapshot.data()?.dni).toBe(dni);

    const indexSnapshot = await adminDb
      .collection("dniIndex")
      .doc(hashDni(dni))
      .get();
    expect(indexSnapshot.data()?.uid).toBe(client.uid);
  });

  it("is idempotent and keeps the stored role", async () => {
    const client = await createClient();
    const sessionId = await registerSession(client);
    const dni = "23456789";

    const first = await completeProfile(client, sessionId, "passenger", { dni });
    const userBefore = await adminDb.collection("users").doc(client.uid).get();
    const second = await completeProfile(client, sessionId, "operator", { dni });
    const userAfter = await adminDb.collection("users").doc(client.uid).get();

    expect(first.alreadyCompleted).toBe(false);
    expect(second).toMatchObject({
      completed: true,
      alreadyCompleted: true,
      role: "passenger"
    });
    expect(userAfter.get("createdAt").isEqual(userBefore.get("createdAt"))).toBe(
      true
    );
    expect(userAfter.get("role")).toBe("passenger");
  });

  it("rejects invalid profile data and inactive sessions", async () => {
    const client = await createClient();
    const sessionId = await registerSession(client);
    const invalidCases: {
      overrides: Record<string, unknown>;
      code: string;
    }[] = [
      { overrides: { dni: "1234567" }, code: "invalid-argument" },
      { overrides: { dni: "123456789" }, code: "invalid-argument" },
      { overrides: { dni: "1234567a" }, code: "invalid-argument" },
      { overrides: { dni: "00000000" }, code: "invalid-argument" },
      {
        overrides: { birthDate: birthDateYearsAgo(11) },
        code: "invalid-argument"
      },
      { overrides: { consentAccepted: false }, code: "failed-precondition" },
      { overrides: { role: "admin" }, code: "invalid-argument" }
    ];

    for (const testCase of invalidCases) {
      await expectFunctionError(
        completeProfile(client, sessionId, "passenger", testCase.overrides),
        testCase.code
      );
    }

    await expectFunctionError(
      completeProfile(client, randomUUID(), "passenger"),
      "permission-denied"
    );
    expect((await adminDb.collection("users").doc(client.uid).get()).exists).toBe(
      false
    );
  });

  it("rejects a DNI already registered by another account", async () => {
    const dni = "34567890";
    const first = await createClient();
    const firstSession = await registerSession(first);
    await completeProfile(first, firstSession, "passenger", { dni });

    const second = await createClient();
    const secondSession = await registerSession(second);

    await expectFunctionError(
      completeProfile(second, secondSession, "passenger", { dni }),
      "already-exists",
      "Ese DNI ya está registrado."
    );

    const secondUser = await adminDb.collection("users").doc(second.uid).get();
    expect(secondUser.exists).toBe(false);
  });

  it("allows only one concurrent registration for the same DNI", async () => {
    const dni = "45678901";
    const [first, second] = await Promise.all([createClient(), createClient()]);
    const [firstSession, secondSession] = await Promise.all([
      registerSession(first),
      registerSession(second)
    ]);

    const results = await Promise.allSettled([
      completeProfile(first, firstSession, "passenger", { dni }),
      completeProfile(second, secondSession, "passenger", { dni })
    ]);
    const fulfilled = results.filter(
      (result) => result.status === "fulfilled"
    );
    const rejected = results.filter(
      (result) => result.status === "rejected"
    );

    expect(fulfilled).toHaveLength(1);
    expect(rejected).toHaveLength(1);
    expect(
      errorCode((rejected[0] as PromiseRejectedResult).reason)
    ).toBe("already-exists");

    const privateProfiles = await adminDb
      .collection("users_private")
      .where("dni", "==", dni)
      .get();
    expect(privateProfiles.size).toBe(1);

    const userSnapshots = await Promise.all(
      [first.uid, second.uid].map((uid) =>
        adminDb.collection("users").doc(uid).get()
      )
    );
    expect(userSnapshots.filter((snapshot) => snapshot.exists)).toHaveLength(1);
  });

  it("registers a verified operator and vehicle idempotently", async () => {
    const client = await createClient({ emailVerified: true });
    const sessionId = await registerSession(client);
    await completeProfile(client, sessionId, "operator", {
      dni: nextDni()
    });

    const first = await registerOperator(client, sessionId, {
      plate: "abc-123"
    });
    const second = await registerOperator(client, sessionId, {
      plate: "abc-123"
    });

    expect(first).toMatchObject({
      registered: true,
      alreadyRegistered: false,
      plate: "ABC123",
      routeIds: [ROUTE_ID],
      status: "unverified"
    });
    expect(second.alreadyRegistered).toBe(true);

    const operator = await adminDb
      .collection("operators")
      .doc(client.uid)
      .get();
    expect(operator.data()).toMatchObject({
      licenseNumber: "Q12345678",
      licenseClass: "A-IIIb",
      plate: "ABC123",
      routeIds: [ROUTE_ID],
      status: "unverified"
    });

    const vehicle = await adminDb.collection("vehicles").doc("ABC123").get();
    expect(vehicle.data()?.ownerUid).toBe(client.uid);
  });

  it("rejects a duplicate plate owned by another operator", async () => {
    const first = await createOperatorClient(nextDni());
    await registerOperator(first.client, first.sessionId, { plate: "XYZ-987" });

    const second = await createOperatorClient(nextDni());
    await expectFunctionError(
      registerOperator(second.client, second.sessionId, { plate: "xyz987" }),
      "already-exists",
      "placa"
    );

    const secondOperator = await adminDb
      .collection("operators")
      .doc(second.client.uid)
      .get();
    expect(secondOperator.exists).toBe(false);
    const vehicle = await adminDb.collection("vehicles").doc("XYZ987").get();
    expect(vehicle.data()?.ownerUid).toBe(first.client.uid);
  });

  it("rejects malformed operator data and unavailable routes", async () => {
    const client = await createClient({ emailVerified: true });
    const sessionId = await registerSession(client);
    await completeProfile(client, sessionId, "operator", { dni: nextDni() });

    const invalidPayloads: Record<string, unknown>[] = [
      { licenseNumber: "12345678" },
      { licenseClass: "Z-1" },
      { plate: "ABC12" },
      { routeIds: [] },
      { routeIds: ["missing-route"] }
    ];

    for (const overrides of invalidPayloads) {
      await expectFunctionError(
        registerOperator(client, sessionId, overrides),
        "invalid-argument"
      );
    }

    const operator = await adminDb
      .collection("operators")
      .doc(client.uid)
      .get();
    expect(operator.exists).toBe(false);
  });

  it("requires an operator profile and a verified email", async () => {
    const passenger = await createClient({ emailVerified: true });
    const passengerSession = await registerSession(passenger);
    await completeProfile(passenger, passengerSession, "passenger", {
      dni: nextDni()
    });

    await expectFunctionError(
      registerOperator(passenger, passengerSession),
      "failed-precondition"
    );

    const unverified = await createClient();
    const unverifiedSession = await registerSession(unverified);
    await completeProfile(unverified, unverifiedSession, "operator", {
      dni: nextDni()
    });

    await expectFunctionError(
      registerOperator(unverified, unverifiedSession),
      "failed-precondition"
    );
  });

  it("rejects the previous session after a second device registers", async () => {
    const client = await createClient({ emailVerified: true });
    const firstSession = await registerSession(client);
    const secondSession = await registerSession(client);
    await completeProfile(client, secondSession, "operator", {
      dni: nextDni()
    });

    await expectFunctionError(
      registerOperator(client, firstSession),
      "permission-denied"
    );

    const result = await registerOperator(client, secondSession);
    expect(result.registered).toBe(true);
  });
});

async function createClient(
  options: { emailVerified?: boolean } = {}
): Promise<SignedInClient> {
  const email = `test-${emailSequence++}-${randomUUID()}@example.com`;
  const userRecord = await adminAuth.createUser({
    email,
    password: PASSWORD,
    emailVerified: options.emailVerified ?? false
  });
  const app = initializeClientApp(
    { projectId: PROJECT_ID, apiKey: PROJECT_ID },
    `f1-client-${randomUUID()}`
  );
  clientApps.add(app);

  const auth = getAuth(app);
  connectAuthEmulator(auth, AUTH_EMULATOR_URL, { disableWarnings: true });
  await signInWithEmailAndPassword(auth, email, PASSWORD);

  const functions = getFunctions(app, FUNCTIONS_REGION);
  connectFunctionsEmulator(functions, EMULATOR_HOST, 5001);

  return { app, auth, functions, uid: userRecord.uid };
}

async function createOperatorClient(
  dni: string
): Promise<{ client: SignedInClient; sessionId: string }> {
  const client = await createClient({ emailVerified: true });
  const sessionId = await registerSession(client);
  await completeProfile(client, sessionId, "operator", { dni });
  return { client, sessionId };
}

async function registerSession(client: SignedInClient): Promise<string> {
  const result = await callFunction<RegisterDeviceResult>(
    client,
    "registerDevice",
    { deviceId: randomUUID() }
  );
  return result.sessionId;
}

function completeProfile(
  client: SignedInClient,
  sessionId: string,
  role: UserRole,
  overrides: Record<string, unknown> = {}
): Promise<CompleteProfileResult> {
  return callFunction<CompleteProfileResult>(client, "completeProfile", {
    sessionId,
    firstName: "Lucia",
    lastName: "Quispe",
    dni: "12345678",
    birthDate: birthDateYearsAgo(20),
    role,
    consentAccepted: true,
    consentVersion: "1.0",
    ...overrides
  });
}

function registerOperator(
  client: SignedInClient,
  sessionId: string,
  overrides: Record<string, unknown> = {}
): Promise<RegisterOperatorResult> {
  return callFunction<RegisterOperatorResult>(client, "registerOperator", {
    sessionId,
    licenseNumber: "Q12345678",
    licenseClass: "A-IIIb",
    plate: "ABC123",
    routeIds: [ROUTE_ID],
    ...overrides
  });
}

async function callFunction<TResult>(
  client: SignedInClient,
  name: string,
  data: Record<string, unknown>
): Promise<TResult> {
  const result = await httpsCallable(client.functions, name)(data);
  return result.data as TResult;
}

async function expectFunctionError(
  promise: Promise<unknown>,
  expectedCode: string,
  messagePart?: string
): Promise<void> {
  let error: unknown;

  try {
    await promise;
  } catch (caught) {
    error = caught;
  }

  expect(error, "La llamada debía fallar.").toBeDefined();
  expect(errorCode(error)).toBe(expectedCode);
  if (messagePart !== undefined) {
    expect(errorMessage(error)).toContain(messagePart);
  }
}

function errorCode(error: unknown): string | undefined {
  if (typeof error === "object" && error !== null && "code" in error) {
    const code = (error as { code?: unknown }).code;
    if (typeof code === "string") {
      return code.replace(/^functions\//, "");
    }
  }

  return undefined;
}

function errorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null && "message" in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string") {
      return message;
    }
  }

  return "";
}

function hashDni(dni: string): string {
  return createHmac("sha256", pepper).update(dni, "utf8").digest("hex");
}

async function readPepper(): Promise<string> {
  const secretPath = fileURLToPath(
    new URL("../.secret.local", import.meta.url)
  );
  const contents = await readFile(secretPath, "utf8");
  const match = /^DNI_PEPPER=(.+)$/m.exec(contents);
  const value = match?.[1]?.trim();

  if (value === undefined || value.length === 0) {
    throw new Error("Falta DNI_PEPPER en functions/.secret.local.");
  }

  return value;
}

function birthDateYearsAgo(years: number): string {
  const date = new Date();
  date.setUTCFullYear(date.getUTCFullYear() - years);
  return date.toISOString().slice(0, 10);
}

function nextDni(): string {
  return String(dniSequence++).padStart(8, "0");
}

async function clearEmulatorData(): Promise<void> {
  const responses = await Promise.all([
    fetch(
      `http://127.0.0.1:9099/emulator/v1/projects/${PROJECT_ID}/accounts`,
      { method: "DELETE" }
    ),
    fetch(
      `http://127.0.0.1:8080/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
      { method: "DELETE" }
    )
  ]);

  for (const response of responses) {
    if (!response.ok) {
      throw new Error(`No se pudo limpiar el emulador: HTTP ${response.status}.`);
    }
  }
}

describe.skipIf(!RUN_INTEGRATION)("F2 callable functions", () => {
  beforeEach(async () => {
    await clearEmulatorData();
    await adminDb.collection("routes").doc(ROUTE_ID).set({
      name: "Ruta de prueba",
      active: true,
      stops: [{ name: "Paradero de prueba", lat: -16.4, lng: -71.5 }]
    });
    await adminDb.collection("config").doc("currentFare").set({
      versionId: "fare-v1"
    });
  });

  afterEach(async () => {
    await Promise.allSettled(
      [...clientApps].map((app) => deleteClientApp(app))
    );
    clientApps.clear();
  });

  it("issues a private QR and exposes the operator context", async () => {
    const passenger = await createPassenger();
    const operator = await createRegisteredOperator();

    const context = await callFunction<{
      plate: string;
      routeIds: string[];
      status: string;
    }>(operator.client, "getOperatorContext", {
      sessionId: operator.sessionId
    });
    expect(context).toEqual({
      plate: "ABC123",
      routeIds: [ROUTE_ID],
      status: "unverified"
    });

    const issued = await callFunction<{
      qrToken: string;
      expiresAt: number;
    }>(passenger.client, "issueQrToken", {
      sessionId: passenger.sessionId
    });
    expect(issued.qrToken.length).toBeGreaterThan(50);
    expect(issued.expiresAt).toBeGreaterThan(Date.now());
    expect(issued.qrToken).not.toContain(passenger.client.uid);

    const nonces = await adminDb.collection("qrNonces").get();
    expect(nonces.size).toBe(1);
    expect(nonces.docs[0].get("uid")).toBe(passenger.client.uid);

    await expectFunctionError(
      callFunction(passenger.client, "startShift", {
        sessionId: passenger.sessionId,
        routeId: ROUTE_ID
      }),
      "failed-precondition"
    );
    await expectFunctionError(
      callFunction(operator.client, "issueQrToken", {
        sessionId: operator.sessionId
      }),
      "failed-precondition"
    );
  });

  it("returns only the passenger fields required for the voice notice", async () => {
    const operator = await createRegisteredOperator();
    const passenger = await createPassenger();
    const shift = await startShift(operator);

    const tap = await tapIn(operator, passenger, shift.shiftId);

    expect(Object.keys(tap).sort()).toEqual(["age", "firstName", "tripId"]);
  });

  it("opens and closes a trip using real GPS chunks", async () => {
    const operator = await createRegisteredOperator();
    const passenger = await createPassenger();
    const shift = await startShift(operator);
    const tap = await tapIn(operator, passenger, shift.shiftId);
    expect(tap.firstName).toBe("Lucia");
    expect(tap.age).toBeGreaterThanOrEqual(19);
    expect(tap.age).toBeLessThanOrEqual(21);
    const tripSnapshot = await adminDb.collection("trips").doc(tap.tripId).get();
    expect(tripSnapshot.get("shiftId")).toBe(shift.shiftId);
    const startMs = Date.now() - 5 * 60 * 1_000;
    const startedAt = AdminTimestamp.fromMillis(startMs);
    await adminDb.collection("trips").doc(tap.tripId).update({ startedAt });
    const endMs = Date.now();
    const pointCount = 4;
    const points = Array.from({ length: pointCount }, (_, index) => {
      const isLast = index === pointCount - 1;
      return {
        lat: -16.4 + index * 0.00005,
        lng: -71.5,
        acc: 5,
        t: isLast
          ? AdminTimestamp.fromMillis(endMs)
          : AdminTimestamp.fromMillis(
              startMs + Math.round((index * (endMs - startMs)) / (pointCount - 1))
            )
      };
    });
    await adminDb
      .collection("shifts")
      .doc(shift.shiftId)
      .collection("chunks")
      .doc("chunk-1")
      .set({
        points,
        from: points[0].t,
        to: points[points.length - 1].t
      });
    const result = await callFunction<{
      tripId: string;
      status: string;
      distanceMeters: number;
      usedPoints: number;
      coverage: number;
      requiresReview: boolean;
    }>(operator.client, "tapOut", {
      sessionId: operator.sessionId,
      tripId: tap.tripId,
      lat: -16.3998,
      lng: -71.5
    });

    expect(result).toMatchObject({
      tripId: tap.tripId,
      status: "closed",
      requiresReview: false
    });
    expect(result.coverage).toBeGreaterThanOrEqual(60);
    expect(result.distanceMeters).toBeGreaterThan(15);
    expect(result.distanceMeters).toBeLessThan(1_000);

    const closed = await adminDb.collection("trips").doc(tap.tripId).get();
    expect(closed.get("status")).toBe("closed");
    expect(closed.get("fareVersionId")).toBe("fare-v1");
    expect(closed.get("passengerAge")).toBe(tap.age);
    expect(closed.get("passengerFirstName")).toBe("Lucia");
    expect(JSON.stringify(closed.data())).not.toContain("@example.com");
  });

  it("marks a trip for review when there is not enough GPS", async () => {
    const operator = await createRegisteredOperator();
    const passenger = await createPassenger();
    const shift = await startShift(operator);
    const tap = await tapIn(operator, passenger, shift.shiftId);

    const result = await callFunction<{
      status: string;
      requiresReview: boolean;
    }>(operator.client, "tapOut", {
      sessionId: operator.sessionId,
      tripId: tap.tripId,
      lat: -16.4,
      lng: -71.5
    });

    expect(result).toMatchObject({
      status: "requires_review",
      requiresReview: true
    });
    const trip = await adminDb.collection("trips").doc(tap.tripId).get();
    expect(trip.get("reviewReason")).toBe("insufficient_gps");
  });

  it("prevents QR reuse and duplicate active trips", async () => {
    const operator = await createRegisteredOperator();
    const passenger = await createPassenger();
    const shift = await startShift(operator);

    const issued = await issueQr(passenger);
    await callFunction(operator.client, "tapIn", {
      sessionId: operator.sessionId,
      shiftId: shift.shiftId,
      qrToken: issued.qrToken,
      lat: -16.4,
      lng: -71.5,
      accuracy: 5
    });

    await expectFunctionError(
      callFunction(operator.client, "tapIn", {
        sessionId: operator.sessionId,
        shiftId: shift.shiftId,
        qrToken: issued.qrToken,
        lat: -16.4,
        lng: -71.5,
        accuracy: 5
      }),
      "failed-precondition",
      "ya fue utilizado"
    );

    const fresh = await issueQr(passenger);
    await expectFunctionError(
      callFunction(operator.client, "tapIn", {
        sessionId: operator.sessionId,
        shiftId: shift.shiftId,
        qrToken: fresh.qrToken,
        lat: -16.4,
        lng: -71.5,
        accuracy: 5
      }),
      "failed-precondition",
      "viaje activo"
    );
  });

  it("creates only one open trip when two tap-ins race", async () => {
    const operator = await createRegisteredOperator();
    const passenger = await createPassenger();
    const shift = await startShift(operator);
    const first = await issueQr(passenger);
    const second = await issueQr(passenger);

    const results = await Promise.allSettled([
      callFunction(operator.client, "tapIn", {
        sessionId: operator.sessionId,
        shiftId: shift.shiftId,
        qrToken: first.qrToken,
        lat: -16.4,
        lng: -71.5,
        accuracy: 5
      }),
      callFunction(operator.client, "tapIn", {
        sessionId: operator.sessionId,
        shiftId: shift.shiftId,
        qrToken: second.qrToken,
        lat: -16.4,
        lng: -71.5,
        accuracy: 5
      })
    ]);

    expect(results.filter((result) => result.status === "fulfilled")).toHaveLength(1);
    expect(results.filter((result) => result.status === "rejected")).toHaveLength(1);
    const openTrips = await adminDb
      .collection("trips")
      .where("passengerUid", "==", passenger.client.uid)
      .where("status", "==", "open")
      .get();
    expect(openTrips.size).toBe(1);
  });

  it("rejects a tap-in against another operator's shift", async () => {
    const operator = await createRegisteredOperator();
    const otherOperator = await createRegisteredOperator("XYZ987");
    const passenger = await createPassenger();
    const shift = await startShift(operator);
    const issued = await issueQr(passenger);

    await expectFunctionError(
      callFunction(otherOperator.client, "tapIn", {
        sessionId: otherOperator.sessionId,
        shiftId: shift.shiftId,
        qrToken: issued.qrToken,
        lat: -16.4,
        lng: -71.5,
        accuracy: 5
      }),
      "failed-precondition",
      "Inicia un turno"
    );
  });

  it("closes a shift and sends open trips to review", async () => {
    const operator = await createRegisteredOperator();
    const passenger = await createPassenger();
    const shift = await startShift(operator);
    const tap = await tapIn(operator, passenger, shift.shiftId);

    await expectFunctionError(
      callFunction(operator.client, "startShift", {
        sessionId: operator.sessionId,
        routeId: ROUTE_ID
      }),
      "failed-precondition"
    );

    const ended = await callFunction<{
      endedAt: number;
      tripsRequiresReview: number;
    }>(operator.client, "endShift", {
      sessionId: operator.sessionId,
      shiftId: shift.shiftId
    });
    expect(ended.tripsRequiresReview).toBe(1);

    const trip = await adminDb.collection("trips").doc(tap.tripId).get();
    expect(trip.get("status")).toBe("requires_review");
    expect(trip.get("reviewReason")).toBe("shift_ended");

    await expectFunctionError(
      callFunction(operator.client, "tapOut", {
        sessionId: operator.sessionId,
        tripId: tap.tripId
      }),
      "failed-precondition"
    );
  });

  it("closes stale open trips from the scheduled cleanup", async () => {
    const operator = await createRegisteredOperator();
    const passenger = await createPassenger();
    const shift = await startShift(operator);
    const tap = await tapIn(operator, passenger, shift.shiftId);

    await adminDb.collection("trips").doc(tap.tripId).update({
      startedAt: AdminTimestamp.fromMillis(Date.now() - 4 * 60 * 60 * 1_000)
    });

    const { closeStaleTripsHandler } = await import(
      "../src/trip/closeStaleTrips.js"
    );
    const result = await closeStaleTripsHandler(new Date());
    expect(result.closed).toBe(1);

    const trip = await adminDb.collection("trips").doc(tap.tripId).get();
    expect(trip.get("status")).toBe("requires_review");
    expect(trip.get("reviewReason")).toBe("no_tap_out");
  });

  it("rejects a replaced session on shift functions", async () => {
    const operator = await createRegisteredOperator();
    const secondSession = await registerSession(operator.client);

    await expectFunctionError(
      callFunction(operator.client, "startShift", {
        sessionId: operator.sessionId,
        routeId: ROUTE_ID
      }),
      "permission-denied"
    );

    const result = await startShift({
      ...operator,
      sessionId: secondSession
    });
    expect(result.shiftId.length).toBeGreaterThan(0);
  });
});

async function createPassenger(): Promise<{
  client: SignedInClient;
  sessionId: string;
}> {
  const client = await createClient({ emailVerified: true });
  const sessionId = await registerSession(client);
  await completeProfile(client, sessionId, "passenger", {
    dni: nextDni()
  });
  return { client, sessionId };
}

async function createRegisteredOperator(plate = "ABC123"): Promise<{
  client: SignedInClient;
  sessionId: string;
}> {
  const client = await createClient({ emailVerified: true });
  const sessionId = await registerSession(client);
  await completeProfile(client, sessionId, "operator", {
    dni: nextDni()
  });
  await registerOperator(client, sessionId, { plate });
  return { client, sessionId };
}

function startShift(client: {
  client: SignedInClient;
  sessionId: string;
}): Promise<{ shiftId: string; startedAt: number }> {
  return callFunction(client.client, "startShift", {
    sessionId: client.sessionId,
    routeId: ROUTE_ID
  });
}

function issueQr(client: {
  client: SignedInClient;
  sessionId: string;
}): Promise<{ qrToken: string; expiresAt: number }> {
  return callFunction(client.client, "issueQrToken", {
    sessionId: client.sessionId
  });
}

async function tapIn(
  operator: { client: SignedInClient; sessionId: string },
  passenger: { client: SignedInClient; sessionId: string },
  shiftId: string
): Promise<{ tripId: string; firstName: string; age: number }> {
  const issued = await issueQr(passenger);
  return callFunction(operator.client, "tapIn", {
    sessionId: operator.sessionId,
    shiftId,
    qrToken: issued.qrToken,
    lat: -16.4,
    lng: -71.5,
    accuracy: 5
  });
}


afterAll(async () => {
  await Promise.allSettled([
    deleteAdminApp(adminApp),
    deleteAdminApp(defaultAdminApp)
  ]);
});