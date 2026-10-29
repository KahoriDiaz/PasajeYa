import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  documentId,
  getAggregateFromServer,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  setDoc,
  startAfter,
  sum,
  updateDoc,
  where,
  writeBatch,
  type QueryDocumentSnapshot
} from "firebase/firestore";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

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
    const startedAt = Timestamp.fromDate(new Date("2026-10-26T12:00:00Z"));
    await setDoc(doc(db, "shifts/shift-owner"), {
      operatorUid: "owner",
      plate: "ABC123",
      routeId: "route-1",
      status: "active",
      startedAt
    });
    await setDoc(doc(db, "shifts/shift-other"), {
      operatorUid: "other",
      plate: "XYZ999",
      routeId: "route-1",
      status: "active",
      startedAt
    });
    await setDoc(doc(db, "shifts/shift-ended"), {
      operatorUid: "owner",
      plate: "ABC123",
      routeId: "route-1",
      status: "ended",
      startedAt
    });
    await setDoc(doc(db, "trips/trip-owner"), {
      passengerUid: "owner",
      operatorUid: "other",
      shiftId: "shift-other",
      status: "open"
    });
    await setDoc(doc(db, "trips/trip-other"), {
      passengerUid: "other",
      operatorUid: "owner",
      shiftId: "shift-owner",
      status: "closed"
    });
    await setDoc(doc(db, "qrNonces/nonce-1"), {
      uid: "owner",
      expiresAt: startedAt
    });
    await setDoc(doc(db, "shiftLocks/operator_owner"), {
      shiftId: "shift-owner"
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

describe("F5 privacy rules", () => {
  it("denies an operator reading a passenger public and private profile", async () => {
    const operatorDb = testEnv.authenticatedContext("other").firestore();

    await assertFails(getDoc(doc(operatorDb, "users/owner")));
    await assertFails(getDoc(doc(operatorDb, "users_private/owner")));
  });
});

describe("F2 Firestore rules", () => {
  it("allows only the operator to read their shift", async () => {
    const ownerDb = testEnv.authenticatedContext("owner").firestore();
    const otherDb = testEnv.authenticatedContext("other").firestore();

    await assertSucceeds(getDoc(doc(ownerDb, "shifts/shift-owner")));
    await assertFails(getDoc(doc(otherDb, "shifts/shift-owner")));
    await assertFails(getDoc(doc(ownerDb, "shifts/shift-other")));
  });

  it("denies every client write to shifts", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertFails(
      setDoc(doc(db, "shifts/shift-owner"), {
        operatorUid: "owner",
        status: "ended"
      })
    );
    await assertFails(
      setDoc(doc(db, "shifts/forged"), {
        operatorUid: "owner",
        status: "active"
      })
    );
  });

  it("allows a valid GPS chunk only on the operator active shift", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();
    const now = Timestamp.fromDate(new Date());
    const chunk = {
      points: [{ lat: -16.4, lng: -71.5, acc: 5, t: now }],
      from: now,
      to: now
    };

    await assertSucceeds(
      setDoc(doc(db, "shifts/shift-owner/chunks/chunk-1"), chunk)
    );
    await assertFails(
      setDoc(doc(db, "shifts/shift-ended/chunks/chunk-2"), chunk)
    );
    await assertFails(
      setDoc(doc(db, "shifts/shift-other/chunks/chunk-3"), chunk)
    );
  });

  it("rejects malformed GPS chunks", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();
    const now = Timestamp.fromDate(new Date());
    const invalidChunks = [
      { points: [], from: now, to: now },
      { points: [{ lat: -16.4, lng: -71.5, acc: 5, t: now }], from: now },
      {
        points: [{ lat: -16.4, lng: -71.5, acc: 5, t: now }],
        from: now,
        to: now,
        extra: true
      },
      {
        points: [{ lat: "x", lng: -71.5, acc: 5, t: now }],
        from: now,
        to: now
      },
      {
        points: [{ lat: -16.4, lng: -71.5, acc: 5, t: now }],
        from: now,
        to: Timestamp.fromMillis(now.toMillis() - 1_000)
      }
    ];

    for (const [index, chunk] of invalidChunks.entries()) {
      await assertFails(
        setDoc(
          doc(db, `shifts/shift-owner/chunks/invalid-${index}`),
          chunk
        )
      );
    }
  });

  it("does not allow editing or deleting a chunk", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();
    const now = Timestamp.fromDate(new Date());
    const chunkRef = doc(db, "shifts/shift-owner/chunks/chunk-edit");
    const chunk = {
      points: [{ lat: -16.4, lng: -71.5, acc: 5, t: now }],
      from: now,
      to: now
    };
    await assertSucceeds(setDoc(chunkRef, chunk));

    await assertFails(updateDoc(chunkRef, { to: now }));
    await assertFails(deleteDoc(chunkRef));
  });

  it("allows trip reads only for its passenger and operator", async () => {
    const ownerDb = testEnv.authenticatedContext("owner").firestore();
    const otherDb = testEnv.authenticatedContext("other").firestore();
    const strangerDb = testEnv.authenticatedContext("stranger").firestore();

    await assertSucceeds(getDoc(doc(ownerDb, "trips/trip-owner")));
    await assertSucceeds(getDoc(doc(otherDb, "trips/trip-owner")));
    await assertSucceeds(getDoc(doc(ownerDb, "trips/trip-other")));
    await assertFails(getDoc(doc(strangerDb, "trips/trip-owner")));
  });

  it("denies client writes to trips, QR nonces, and shift locks", async () => {
    const db = testEnv.authenticatedContext("owner").firestore();

    await assertFails(
      setDoc(doc(db, "trips/forged"), {
        passengerUid: "owner",
        operatorUid: "owner",
        status: "closed"
      })
    );
    await assertFails(getDoc(doc(db, "qrNonces/nonce-1")));
    await assertFails(
      setDoc(doc(db, "qrNonces/nonce-2"), { uid: "owner" })
    );
    await assertFails(getDoc(doc(db, "shiftLocks/operator_owner")));
    await assertFails(
      setDoc(doc(db, "shiftLocks/operator_owner"), { shiftId: "forged" })
    );
  });
});

const HISTORY_PASSENGER = "history-passenger";
const HISTORY_OTHER = "history-other";
const HISTORY_OPERATOR = "history-operator";
const HISTORY_SHIFT = "history-shift";
const HISTORY_TRIP_COUNT = 45;
const HISTORY_CLOSED_COUNT = 40;

describe("F6 Firestore rules and history data", () => {
  async function seedHistoryTrips() {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      const batch = writeBatch(db);
      const startedAt = Date.UTC(2026, 8, 1, 12, 0, 0);

      for (let index = 0; index < HISTORY_TRIP_COUNT; index += 1) {
        const closed = index < HISTORY_CLOSED_COUNT;
        batch.set(
          doc(
            db,
            `trips/history-trip-${String(index).padStart(2, "0")}`
          ),
          {
            passengerUid: HISTORY_PASSENGER,
            operatorUid: HISTORY_OPERATOR,
            shiftId: HISTORY_SHIFT,
            routeId: "route-1",
            status: closed ? "closed" : "requires_review",
            startedAt: Timestamp.fromMillis(startedAt + index * 60_000),
            fareBreakdown: {
              totalCents: closed ? (index + 1) * 100 : 9_999_999
            }
          }
        );
      }

      batch.set(doc(db, "users/history-passenger"), {
        role: "passenger"
      });
      batch.set(doc(db, "users/history-operator"), {
        role: "operator"
      });
      batch.set(doc(db, "users/history-other"), {
        role: "passenger"
      });
      batch.set(doc(db, "shifts/history-shift"), {
        operatorUid: HISTORY_OPERATOR,
        plate: "HIS123",
        routeId: "route-1",
        status: "ended",
        startedAt: Timestamp.fromMillis(startedAt)
      });
      batch.set(doc(db, "trips/history-other-trip"), {
        passengerUid: HISTORY_OTHER,
        operatorUid: "owner",
        shiftId: "shift-owner",
        routeId: "route-1",
        status: "closed",
        startedAt: Timestamp.fromMillis(startedAt)
      });
      await batch.commit();
    });
  }

  it("allows a passenger to read only their own trips", async () => {
    await seedHistoryTrips();
    const db = testEnv.authenticatedContext(HISTORY_PASSENGER).firestore();

    const ownTrips = await assertSucceeds(
      getDocs(
        query(
          collection(db, "trips"),
          where("passengerUid", "==", HISTORY_PASSENGER),
          where("status", "in", ["closed", "requires_review"]),
          orderBy("startedAt", "desc"),
          orderBy(documentId(), "desc"),
          limit(20)
        )
      )
    );
    expect(ownTrips.size).toBe(20);

    await assertFails(
      getDocs(
        query(
          collection(db, "trips"),
          where("passengerUid", "==", HISTORY_OTHER)
        )
      )
    );
  });

  it("allows an operator to read the trips of their shift only", async () => {
    await seedHistoryTrips();
    const db = testEnv.authenticatedContext(HISTORY_OPERATOR).firestore();

    const shiftTrips = await assertSucceeds(
      getDocs(
        query(
          collection(db, "trips"),
          where("operatorUid", "==", HISTORY_OPERATOR),
          where("shiftId", "==", HISTORY_SHIFT),
          orderBy("startedAt", "desc")
        )
      )
    );
    expect(shiftTrips.size).toBe(HISTORY_TRIP_COUNT);

    await assertFails(
      getDocs(
        query(
          collection(db, "trips"),
          where("shiftId", "==", "shift-owner")
        )
      )
    );
  });

  it("denies every client write to trips", async () => {
    await seedHistoryTrips();
    const db = testEnv.authenticatedContext(HISTORY_PASSENGER).firestore();
    const trip = doc(db, "trips/history-trip-00");

    await assertFails(
      setDoc(trip, {
        passengerUid: HISTORY_PASSENGER,
        operatorUid: HISTORY_OPERATOR,
        shiftId: HISTORY_SHIFT,
        status: "closed",
        fareBreakdown: { totalCents: 1 }
      })
    );
    await assertFails(updateDoc(trip, { status: "closed" }));
    await assertFails(deleteDoc(trip));
  });

  it("paginates 45 trips as 20, 20 and 5 without duplicates or gaps", async () => {
    await seedHistoryTrips();
    const db = testEnv.authenticatedContext(HISTORY_PASSENGER).firestore();
    const baseQuery = query(
      collection(db, "trips"),
      where("passengerUid", "==", HISTORY_PASSENGER),
      where("status", "in", ["closed", "requires_review"]),
      orderBy("startedAt", "desc"),
      orderBy(documentId(), "desc"),
      limit(20)
    );
    const pageSizes: number[] = [];
    const ids: string[] = [];
    let cursor: QueryDocumentSnapshot | null = null;

    for (let page = 0; page < 3; page += 1) {
      const pageQuery = cursor
        ? query(
            baseQuery,
            startAfter(cursor.get("startedAt"), cursor.id)
          )
        : baseQuery;
      const snapshot = await assertSucceeds(getDocs(pageQuery));
      pageSizes.push(snapshot.size);
      ids.push(...snapshot.docs.map((item) => item.id));
      cursor = snapshot.docs.at(-1) ?? null;
    }

    expect(pageSizes).toEqual([20, 20, 5]);
    expect(new Set(ids).size).toBe(HISTORY_TRIP_COUNT);
    expect(ids).toEqual(
      Array.from(
        { length: HISTORY_TRIP_COUNT },
        (_, index) => `history-trip-${String(index).padStart(2, "0")}`
      ).reverse()
    );
  });

  it("sums only closed trips with the aggregate query", async () => {
    await seedHistoryTrips();
    const db = testEnv.authenticatedContext(HISTORY_PASSENGER).firestore();
    const closedQuery = query(
      collection(db, "trips"),
      where("passengerUid", "==", HISTORY_PASSENGER),
      where("status", "==", "closed")
    );
    const expectedTotal = Array.from(
      { length: HISTORY_CLOSED_COUNT },
      (_, index) => (index + 1) * 100
    ).reduce((total, value) => total + value, 0);

    const aggregate = await assertSucceeds(
      getAggregateFromServer(closedQuery, {
        total: sum("fareBreakdown.totalCents")
      })
    );

    expect(aggregate.data().total).toBe(expectedTotal);
  });
});