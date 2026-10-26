import { FieldValue, getFirestore } from "firebase-admin/firestore";
import {
  onCall,
  HttpsError,
  type CallableRequest
} from "firebase-functions/v2/https";
import {
  normalizeLicenseNumber,
  normalizePlate
} from "../config/operators.js";
import { FUNCTIONS_REGION } from "../config/region.js";
import {
  readString,
  requireRecord,
  requireVerifiedAuth
} from "../http/auth.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import {
  isUserRole,
  OPERATOR_STATUS_UNVERIFIED
} from "../types/user.js";
import {
  isValidLicenseClass,
  isValidLicenseNumber,
  isValidPlate
} from "../validation/validators.js";

export interface RegisterOperatorData {
  sessionId: string;
  licenseNumber: string;
  licenseClass: string;
  plate: string;
  routeIds: string[];
}

export interface RegisterOperatorResult {
  registered: true;
  alreadyRegistered: boolean;
  plate: string;
  routeIds: string[];
  status: string;
}

export async function registerOperatorHandler(
  request: CallableRequest<RegisterOperatorData>
): Promise<RegisterOperatorResult> {
  const auth = requireVerifiedAuth(request);
  const data = requireRecord(request.data);
  const sessionId = readString(data, "sessionId", "La sesión", 100);
  const licenseNumber = normalizeLicenseNumber(
    readString(data, "licenseNumber", "La licencia", 20)
  );
  const licenseClass = readString(data, "licenseClass", "La clase de licencia", 10);
  const plate = normalizePlate(readString(data, "plate", "La placa", 10));
  const rawRouteIds = data.routeIds;

  if (!isValidLicenseNumber(licenseNumber)) {
    throw new HttpsError(
      "invalid-argument",
      "El número de licencia no tiene el formato esperado."
    );
  }
  if (!isValidLicenseClass(licenseClass)) {
    throw new HttpsError(
      "invalid-argument",
      "La clase de licencia no es válida."
    );
  }
  if (!isValidPlate(plate)) {
    throw new HttpsError("invalid-argument", "La placa no es válida.");
  }
  if (!Array.isArray(rawRouteIds)) {
    throw new HttpsError(
      "invalid-argument",
      "Debes seleccionar al menos una ruta."
    );
  }

  const routeIds = [
    ...new Set(
      rawRouteIds.map((routeId) => {
        if (typeof routeId !== "string" || routeId.length === 0 || routeId.length > 100) {
          throw new HttpsError(
            "invalid-argument",
            "Una de las rutas seleccionadas no es válida."
          );
        }
        return routeId;
      })
    )
  ];

  if (routeIds.length === 0 || routeIds.length > 20) {
    throw new HttpsError(
      "invalid-argument",
      "Debes seleccionar entre 1 y 20 rutas."
    );
  }

  const uid = auth.uid;
  const db = getFirestore();
  const userRef = db.collection("users").doc(uid);
  const operatorRef = db.collection("operators").doc(uid);
  const vehicleRef = db.collection("vehicles").doc(plate);
  const routeRefs = routeIds.map((routeId) =>
    db.collection("routes").doc(routeId)
  );

  return db.runTransaction(async (transaction) => {
    await assertActiveSession(uid, sessionId, transaction);

    const userSnapshot = await transaction.get(userRef);
    if (!userSnapshot.exists || !isUserRole(userSnapshot.get("role"))) {
      throw new HttpsError(
        "failed-precondition",
        "El perfil no está completo."
      );
    }
    if (userSnapshot.get("role") !== "operator") {
      throw new HttpsError(
        "failed-precondition",
        "Esta cuenta no es de operador."
      );
    }

    const operatorSnapshot = await transaction.get(operatorRef);
    const vehicleSnapshot = await transaction.get(vehicleRef);
    const routeSnapshots = [];
    for (const routeRef of routeRefs) {
      routeSnapshots.push(await transaction.get(routeRef));
    }

    if (
      routeSnapshots.some(
        (snapshot) => !snapshot.exists || snapshot.get("active") !== true
      )
    ) {
      throw new HttpsError(
        "invalid-argument",
        "Una de las rutas seleccionadas ya no está disponible."
      );
    }

    if (operatorSnapshot.exists) {
      const sameRegistration =
        operatorSnapshot.get("licenseNumber") === licenseNumber &&
        operatorSnapshot.get("licenseClass") === licenseClass &&
        operatorSnapshot.get("plate") === plate &&
        sameRouteIds(operatorSnapshot.get("routeIds"), routeIds);

      if (!sameRegistration) {
        throw new HttpsError(
          "failed-precondition",
          "El alta de operador ya está registrada."
        );
      }

      if (
        vehicleSnapshot.exists &&
        vehicleSnapshot.get("ownerUid") !== uid
      ) {
        throw new HttpsError(
          "already-exists",
          "Esa placa ya está registrada."
        );
      }

      if (!vehicleSnapshot.exists) {
        transaction.create(vehicleRef, { ownerUid: uid });
      }

      return {
        registered: true,
        alreadyRegistered: true,
        plate,
        routeIds,
        status:
          operatorSnapshot.get("status") ?? OPERATOR_STATUS_UNVERIFIED
      };
    }

    if (vehicleSnapshot.exists) {
      throw new HttpsError(
        "already-exists",
        "Esa placa ya está registrada."
      );
    }

    transaction.create(operatorRef, {
      licenseNumber,
      licenseClass,
      plate,
      routeIds,
      status: OPERATOR_STATUS_UNVERIFIED,
      createdAt: FieldValue.serverTimestamp()
    });
    transaction.create(vehicleRef, { ownerUid: uid });

    return {
      registered: true,
      alreadyRegistered: false,
      plate,
      routeIds,
      status: OPERATOR_STATUS_UNVERIFIED
    };
  });
}

export const registerOperator = onCall(
  { region: FUNCTIONS_REGION },
  registerOperatorHandler
);

function sameRouteIds(value: unknown, expected: string[]): boolean {
  if (!Array.isArray(value) || value.length !== expected.length) return false;

  const actual = new Set(
    value.filter((routeId): routeId is string => typeof routeId === "string")
  );
  return actual.size === expected.length && expected.every((id) => actual.has(id));
}
