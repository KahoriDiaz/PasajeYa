// Verificacion del brevete contra una consulta oficial del MTC.
//
// El MTC publica el Portal Unico del Conductor como pagina web con captcha y sin API
// publica, asi que la consulta se hace a traves de un proveedor que revende ese dato
// (json.pe, placapi, consultadatos y similares). El token vive en los secretos de GitHub
// y nunca en el APK: la app solo deja el pedido en Firestore y este trabajo lo resuelve.
//
// Secretos necesarios:
//   LICENSE_API_URL    si lleva {dni} se consulta con GET sustituyendo ese hueco;
//                      si no, se envia POST con {"dni": "..."} en el cuerpo, que es
//                      como funciona https://api.json.pe/api/licencia
//   LICENSE_API_TOKEN  token del proveedor (se envia como Bearer)
// Sin esos secretos el trabajo no hace nada y termina bien.
const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const API_URL = process.env.LICENSE_API_URL || "";
const API_TOKEN = process.env.LICENSE_API_TOKEN || "";
const MAX_DRIVERS = 25;

const STATUS_PENDING = "POR_REVISAR";
const STATUS_ENABLED = "HABILITADO";
const STATUS_REJECTED = "RECHAZADO";

if (!API_URL || !API_TOKEN) {
  console.log("No hay proveedor de consulta configurado (LICENSE_API_URL / LICENSE_API_TOKEN).");
  console.log("Los brevetes quedan POR_REVISAR hasta que la empresa los avale.");
  process.exit(0);
}

initializeApp({ credential: cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)) });
const db = getFirestore();

function pick(data, names) {
  if (!data || typeof data !== "object") return null;
  for (const [key, value] of Object.entries(data)) {
    const plain = key.toLowerCase().replace(/[^a-z]/g, "");
    if (names.includes(plain) && value !== null && value !== "") return value;
    if (value && typeof value === "object") {
      const nested = pick(value, names);
      if (nested !== null) return nested;
    }
  }
  return null;
}

function parseDate(raw) {
  if (!raw) return null;
  const text = String(raw).trim();
  const dayFirst = text.match(/^(\d{2})[/-](\d{2})[/-](\d{4})$/);
  if (dayFirst) {
    return new Date(`${dayFirst[3]}-${dayFirst[2]}-${dayFirst[1]}T00:00:00Z`);
  }
  const parsed = new Date(text);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

/** Decide si el brevete habilita a manejar transporte de pasajeros (clase A). */
function evaluate(payload) {
  const licenseClass = String(pick(payload, ["clase", "clasecategoria", "categoria", "class"]) || "")
    .toUpperCase()
    .trim();
  const state = String(pick(payload, ["estado", "situacion", "status", "estadolicencia"]) || "")
    .toUpperCase()
    .trim();
  const expiry = parseDate(
    pick(payload, ["fechahasta", "fechavencimiento", "vencimiento", "fechacaducidad", "validuntil"])
  );

  if (!licenseClass && !state) {
    return { status: STATUS_PENDING, note: "La consulta no devolvio datos de la licencia." };
  }
  if (state && !/VIGENTE|HABILITAD|AUTORIZAD|ACTIV/.test(state)) {
    return { status: STATUS_REJECTED, note: `Licencia en estado ${state}.`, licenseClass, expiry };
  }
  if (licenseClass && !licenseClass.startsWith("A")) {
    return {
      status: STATUS_REJECTED,
      note: `La clase ${licenseClass} no habilita transporte de pasajeros.`,
      licenseClass,
      expiry
    };
  }
  if (expiry && expiry.getTime() <= Date.now()) {
    return { status: STATUS_REJECTED, note: "La licencia esta vencida.", licenseClass, expiry };
  }
  return { status: STATUS_ENABLED, note: "Licencia vigente de clase A.", licenseClass, expiry };
}

async function queryProvider(dni) {
  const headers = {
    Authorization: `Bearer ${API_TOKEN}`,
    Accept: "application/json"
  };
  const usesGet = API_URL.includes("{dni}");
  const url = usesGet ? API_URL.replace("{dni}", encodeURIComponent(dni)) : API_URL;
  const options = usesGet
    ? { headers }
    : {
        method: "POST",
        headers: { ...headers, "Content-Type": "application/json" },
        body: JSON.stringify({ dni })
      };

  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`El proveedor respondio ${response.status}`);
  }
  return response.json();
}

async function dniFor(uid, userData) {
  if (userData.dni) return String(userData.dni);
  const privateDoc = await db.collection("users_private").doc(uid).get();
  const dni = privateDoc.data()?.dni;
  return dni ? String(dni) : null;
}

async function main() {
  const pending = await db
    .collection("users")
    .where("role", "==", "operator")
    .where("licenseStatus", "==", STATUS_PENDING)
    .limit(MAX_DRIVERS)
    .get();

  if (pending.empty) {
    console.log("No hay brevetes por revisar.");
    return;
  }
  console.log(`Brevetes por revisar: ${pending.size}`);

  for (const doc of pending.docs) {
    const data = doc.data();
    const dni = await dniFor(doc.id, data);
    if (!dni) {
      console.log(`- ${doc.id}: sin DNI guardado, no se puede consultar.`);
      continue;
    }

    let result;
    try {
      result = evaluate(await queryProvider(dni));
    } catch (error) {
      console.log(`- ${doc.id}: la consulta fallo (${error.message}).`);
      continue;
    }

    const changes = {
      licenseStatus: result.status,
      licenseCheckNote: result.note,
      licenseCheckedAt: FieldValue.serverTimestamp(),
      licenseCheckSource: "MTC via proveedor de consultas"
    };
    if (result.licenseClass) changes.licenseClassOfficial = result.licenseClass;
    if (result.expiry) changes.licenseExpiry = result.expiry;
    await doc.ref.set(changes, { merge: true });
    console.log(`- ${doc.id}: ${result.status} (${result.note})`);
  }
}

main().then(() => process.exit(0)).catch((error) => {
  console.error(error);
  process.exit(1);
});
