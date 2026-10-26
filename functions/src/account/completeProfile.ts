import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { FUNCTIONS_REGION } from "../config/region.js";
import { PRIVACY_CONSENT_VERSION } from "../config/privacy.js";
import {
  readString,
  requireAuth,
  requireRecord
} from "../http/auth.js";
import { hashDni } from "../security/dniHmac.js";
import { assertActiveSession } from "../sessions/activeSession.js";
import { isUserRole, type UserRole } from "../types/user.js";
import {
  isValidBirthDate,
  isValidDni,
  isValidPersonName
} from "../validation/validators.js";

const DNI_PEPPER = defineSecret("DNI_PEPPER");

export interface CompleteProfileData {
  sessionId: string;
  firstName: string;
  lastName: string;
  dni: string;
  birthDate: string;
  role: UserRole;
  consentAccepted: boolean;
  consentVersion: string;
}

export interface CompleteProfileResult {
  completed: true;
  alreadyCompleted: boolean;
  role: UserRole;
}

export async function completeProfileHandler(
  request: CallableRequest<CompleteProfileData>,
  pepper: string
): Promise<CompleteProfileResult> {
  const auth = requireAuth(request);
  const data = requireRecord(request.data);
  const sessionId = readString(data, "sessionId", "La sesión", 100);
  const firstName = readString(data, "firstName", "Los nombres", 40).trim();
  const lastName = readString(data, "lastName", "Los apellidos", 40).trim();
  const dni = readString(data, "dni", "El DNI", 9);
  const birthDate = readString(data, "birthDate", "La fecha de nacimiento", 10);
  const role = data.role;
  const consentVersion = readString(
    data,
    "consentVersion",
    "La versión del consentimiento",
    20
  );

  if (!isValidPersonName(firstName)) {
    throw new HttpsError("invalid-argument", "Los nombres no son válidos.");
  }
  if (!isValidPersonName(lastName)) {
    throw new HttpsError("invalid-argument", "Los apellidos no son válidos.");
  }
  if (!isValidDni(dni)) {
    throw new HttpsError(
      "invalid-argument",
      "El DNI debe tener exactamente 8 dígitos y no puede ser una repetición."
    );
  }
  if (!isValidBirthDate(birthDate)) {
    throw new HttpsError(
      "invalid-argument",
      "La fecha de nacimiento no es válida. Debes tener entre 12 y 110 años."
    );
  }
  if (!isUserRole(role)) {
    throw new HttpsError("invalid-argument", "El perfil seleccionado no es válido.");
  }
  if (data.consentAccepted !== true) {
    throw new HttpsError(
      "failed-precondition",
      "Debes aceptar la política de privacidad."
    );
  }
  if (consentVersion !== PRIVACY_CONSENT_VERSION) {
    throw new HttpsError(
      "failed-precondition",
      "La versión del consentimiento no es válida."
    );
  }

  const uid = auth.uid;
  const hmacDni = hashDni(dni, pepper);
  const db = getFirestore();
  const userRef = db.collection("users").doc(uid);
  const privateRef = db.collection("users_private").doc(uid);
  const indexRef = db.collection("dniIndex").doc(hmacDni);

  return db.runTransaction(async (transaction) => {
    await assertActiveSession(uid, sessionId, transaction);

    const userSnapshot = await transaction.get(userRef);
    const indexSnapshot = await transaction.get(indexRef);

    if (userSnapshot.exists) {
      if (indexSnapshot.exists && indexSnapshot.get("uid") === uid) {
        const storedRole = userSnapshot.get("role");
        return {
          completed: true,
          alreadyCompleted: true,
          role: isUserRole(storedRole) ? storedRole : role
        };
      }

      throw new HttpsError(
        "failed-precondition",
        "El perfil ya está completo."
      );
    }

    if (indexSnapshot.exists) {
      throw new HttpsError(
        "already-exists",
        "Ese DNI ya está registrado."
      );
    }

    const now = FieldValue.serverTimestamp();
    transaction.create(userRef, {
      role,
      firstName,
      lastName,
      birthDate,
      createdAt: now,
      privacyConsent: {
        version: consentVersion,
        acceptedAt: now
      },
      benefit: {
        status: "none",
        kind: null,
        validUntil: null
      }
    });
    transaction.create(privateRef, { dni });
    transaction.create(indexRef, { uid });

    return {
      completed: true,
      alreadyCompleted: false,
      role
    };
  });
}

export const completeProfile = onCall(
  {
    region: FUNCTIONS_REGION,
    secrets: [DNI_PEPPER]
  },
  (request) => completeProfileHandler(request, DNI_PEPPER.value())
);
