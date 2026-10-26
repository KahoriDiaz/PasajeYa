import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  initializeTestEnvironment,
  type RulesTestEnvironment
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
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
});

afterAll(async () => {
  await testEnv.cleanup();
});

describe("default-deny Firestore rules", () => {
  it("denies reads without authentication", async () => {
    const db = testEnv.unauthenticatedContext().firestore();

    await assertFails(getDoc(doc(db, "users/any-uid")));
  });

  it("denies writes without authentication", async () => {
    const db = testEnv.unauthenticatedContext().firestore();

    await assertFails(
      setDoc(doc(db, "users/any-uid"), { role: "passenger" })
    );
  });

  it("denies reads for authenticated users while rules deny all", async () => {
    const db = testEnv.authenticatedContext("user-1").firestore();

    await assertFails(getDoc(doc(db, "trips/trip-1")));
  });
});
