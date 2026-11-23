const admin = require("firebase-admin");
const serviceAccount = require("../serviceAccountKey.json");

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

const auth = admin.auth();
const db = admin.firestore();

async function createOperator() {
  const email = "conductor.pasajeya@gmail.com";
  const password = "Password123!";
  const plate = "V1A-824";
  const routeId = "R-01";

  let uid;
  try {
    const userRecord = await auth.getUserByEmail(email);
    uid = userRecord.uid;
    console.log(`Usuario existente encontrado con UID: ${uid}`);
  } catch (error) {
    if (error.code === "auth/user-not-found") {
      const newUser = await auth.createUser({
        email: email,
        password: password,
        displayName: "Conductor Demo"
      });
      uid = newUser.uid;
      console.log(`Usuario creado exitosamente con UID: ${uid}`);
    } else {
      throw error;
    }
  }

  // Configurar Custom Claims y Documento Firestore
  await auth.setCustomUserClaims(uid, { role: "operator" });

  await db.collection("users").document(uid).set({
    uid: uid,
    email: email,
    displayName: "Conductor Demo",
    role: "operator",
    operatorStatus: "verified",
    plate: plate,
    assignedRoutes: [routeId],
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });

  // Crear la ruta de prueba si no existe para que pueda iniciar turno
  await db.collection("routes").document(routeId).set({
    id: routeId,
    name: "Ruta 01 - Cayma / Cercado",
    code: "R-01",
    active: true
  }, { merge: true });

  console.log("\n========================================");
  console.log("¡OPERADOR CREADO Y CONFIGURADO CON ÉXITO!");
  console.log(`Correo:   ${email}`);
  console.log(`Clave:    ${password}`);
  console.log(`Placa:    ${plate}`);
  console.log(`Ruta ID:  ${routeId}`);
  console.log("========================================\n");
}

createOperator().catch(console.error);
