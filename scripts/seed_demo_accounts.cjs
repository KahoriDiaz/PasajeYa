// Crea las cuentas de demostracion de PasajeYa! en Firebase Auth y Firestore.
// Se ejecuta a mano desde GitHub Actions (workflow "Crear cuentas de demostracion").
// Son cuentas reales: sirven para mostrar la app cuando no hay un conductor o una
// empresa de verdad a mano.
const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
initializeApp({ credential: cert(serviceAccount) });

const db = getFirestore();
const auth = getAuth();

const PASSWORD = process.env.DEMO_PASSWORD || "Demo.PasajeYa2026";

const DRIVER = {
  email: "conductor.demo@pasajeya.pe",
  firstName: "Conductor",
  lastName: "Demo",
  birthDate: "1990-05-10",
  dni: "70000001",
  plate: "V7D123",
  licenseNumber: "Q12345678",
  licenseClass: "A-IIb"
};

const COMPANY = {
  email: "operador.demo@pasajeya.pe",
  businessName: "Consorcio Demo PasajeYa S.A.C.",
  ruc: "20600000018"
};

function ageFrom(isoDate) {
  const birth = new Date(isoDate);
  const now = new Date();
  let age = now.getFullYear() - birth.getFullYear();
  const beforeBirthday =
    now.getMonth() < birth.getMonth() ||
    (now.getMonth() === birth.getMonth() && now.getDate() < birth.getDate());
  if (beforeBirthday) age -= 1;
  return age;
}

async function ensureUser(email, displayName) {
  try {
    const existing = await auth.getUserByEmail(email);
    await auth.updateUser(existing.uid, {
      password: PASSWORD,
      emailVerified: true,
      displayName
    });
    console.log(`Cuenta actualizada: ${email}`);
    return existing.uid;
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;
    const created = await auth.createUser({
      email,
      password: PASSWORD,
      emailVerified: true,
      displayName
    });
    console.log(`Cuenta creada: ${email}`);
    return created.uid;
  }
}

async function firstActiveRouteId() {
  const active = await db.collection("routes").where("active", "==", true).limit(1).get();
  if (!active.empty) return active.docs[0].id;
  const any = await db.collection("routes").limit(1).get();
  return any.empty ? "" : any.docs[0].id;
}

async function main() {
  const routeId = await firstActiveRouteId();
  if (!routeId) {
    console.warn("No hay rutas en Firestore: el conductor demo quedara sin ruta asignada.");
  }

  const companyUid = await ensureUser(COMPANY.email, COMPANY.businessName);
  const driverUid = await ensureUser(
    DRIVER.email,
    `${DRIVER.firstName} ${DRIVER.lastName}`
  );

  // Empresa (operador): paga la suscripcion y ve a sus conductores.
  await db.collection("users").doc(companyUid).set(
    {
      role: "company",
      businessName: COMPANY.businessName,
      companyRuc: COMPANY.ruc,
      fullName: COMPANY.businessName,
      isDemoAccount: true,
      createdAt: FieldValue.serverTimestamp(),
      privacyConsent: { version: "demo", acceptedAt: FieldValue.serverTimestamp() }
    },
    { merge: true }
  );
  await db.collection("companies").doc(companyUid).set(
    {
      businessName: COMPANY.businessName,
      ruc: COMPANY.ruc,
      isDemoAccount: true,
      createdAt: FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  // Conductor avalado por esa empresa: queda verificado, como un formal real.
  await db.collection("users").doc(driverUid).set(
    {
      role: "operator",
      firstName: DRIVER.firstName,
      lastName: DRIVER.lastName,
      fullName: `${DRIVER.firstName} ${DRIVER.lastName}`,
      dni: DRIVER.dni,
      birthDate: DRIVER.birthDate,
      age: ageFrom(DRIVER.birthDate),
      plate: DRIVER.plate,
      vehiclePlate: DRIVER.plate,
      assignedRouteId: routeId,
      licenseClass: DRIVER.licenseClass,
      licenseNumber: DRIVER.licenseNumber,
      licenseStatus: "HABILITADO",
      driverStatus: "verified_by_company",
      companyId: companyUid,
      isDemoAccount: true,
      createdAt: FieldValue.serverTimestamp(),
      privacyConsent: { version: "demo", acceptedAt: FieldValue.serverTimestamp() },
      benefit: { status: "none" }
    },
    { merge: true }
  );
  await db.collection("users_private").doc(driverUid).set({ dni: DRIVER.dni }, { merge: true });
  await db.collection("operators").doc(driverUid).set(
    {
      licenseNumber: DRIVER.licenseNumber,
      licenseClass: DRIVER.licenseClass,
      plate: DRIVER.plate,
      routeIds: routeId ? [routeId] : [],
      status: "verified_by_company",
      companyId: companyUid,
      isDemoAccount: true,
      updatedAt: FieldValue.serverTimestamp()
    },
    { merge: true }
  );
  await db.collection("vehicles").doc(DRIVER.plate).set({ ownerUid: driverUid }, { merge: true });

  console.log("");
  console.log("Cuentas de demostracion listas:");
  console.log(`  Operador (empresa): ${COMPANY.email}`);
  console.log(`  Conductor:          ${DRIVER.email}`);
  console.log(`  Contrasena:         ${PASSWORD}`);
  console.log(`  Ruta asignada:      ${routeId || "(ninguna)"}`);
  console.log(`  Placa:              ${DRIVER.plate}`);
}

main().then(() => process.exit(0)).catch((error) => {
  console.error(error);
  process.exit(1);
});
