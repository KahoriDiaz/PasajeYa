import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment
} from "@firebase/rules-unit-testing";
import {
  doc,
  getDoc,
  setDoc
} from "firebase/firestore";
import { afterAll, beforeAll, describe, it } from "vitest";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  const rules = await readFile(
    fileURLToPath(new URL("../../firestore.rules", import.meta.url)),
    "utf8"
  );

  testEnv = await initializeTestEnvironment({
    projectId: "demo-pasajeya",
    firestore: {
      rules,
      host: "127.0.0.1",
      port: 8080
    }
  });

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "users/owner"), { role: "passenger" });
    await setDoc(doc(db, "users/other"), { role: "operator" });
    await setDoc(doc(db, "sessions/owner"), {
      sessionId: "session-owner"
    });
    await setDoc(doc(db, "users_private/owner"), { dni: "12345678" });
    await setDoc(doc(db, "dniIndex/hash"), { uid: "owner" });
    await setDoc(doc(db, "operators/owner"), { plate: "ABC123" });
    await setDoc(doc(db, "vehicles/ABC123"), { ownerUid: "owner" });
    await setDoc(doc(db, "routes/route-1"), {
      name: "Ruta de prueba",
      active: true,
      stops: []
    });
  });
}, 60_000);

afterAll(async () => {
  await testEnv.cleanup();
});

describe("F1 Firestore rules", () => {
  it("denies everything without authentication", async () => {
    const db = testEnv.unauthenticatedContext().firestore();

    await assertFails(getDoc(doc(db, "users/owner")));
    await assertFails(getDoc(doc(db, "sessions/owner")));
    await assertFails(getDoc(doc(db, "routes/route-1")));
  });

  it("allows an owner to read their user document", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertSucceeds(getDoc(doc(db, "users/owner")));
  });

  it("denies reading another user's user document", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertFails(getDoc(doc(db, "users/other")));
  });

  it("denies client writes to users", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertFails(
      setDoc(doc(db, "users/owner"), { role: "operator" })
    );
    await assertFails(
      setDoc(doc(db, "users/other"), { role: "passenger" })
    );
  });

  it("allows an owner to read their session and denies writes", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertSucceeds(getDoc(doc(db, "sessions/owner")));
    await assertFails(
      setDoc(doc(db, "sessions/owner"), { sessionId: "forged" })
    );
    await assertFails(getDoc(doc(db, "sessions/other")));
  });

  it("denies all client access to private account collections", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertFails(getDoc(doc(db, "users_private/owner")));
    await assertFails(
      setDoc(doc(db, "users_private/owner"), { dni: "87654321" })
    );
    await assertFails(getDoc(doc(db, "dniIndex/hash")));
    await assertFails(getDoc(doc(db, "operators/owner")));
    await assertFails(
      setDoc(doc(db, "operators/owner"), { plate: "XYZ999" })
    );
    await assertFails(getDoc(doc(db, "vehicles/ABC123")));
    await assertFails(
      setDoc(doc(db, "vehicles/ABC123"), { ownerUid: "owner" })
    );
  });

  it("allows authenticated route reads and denies route writes", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertSucceeds(getDoc(doc(db, "routes/route-1")));
    await assertFails(
      setDoc(doc(db, "routes/route-1"), {
        name: "Ruta alterada",
        active: true,
        stops: []
      })
    );
  });

  it("denies writes to unknown collections", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertFails(
      setDoc(doc(db, "unknown/anything"), { value: true })
    );
  });
});
