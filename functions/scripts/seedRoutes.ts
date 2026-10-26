import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

interface StopInput {
  name: string;
  lat: number;
  lng: number;
}

interface RouteInput {
  id: string;
  name: string;
  active: boolean;
  stops: StopInput[];
}

const routesPath = fileURLToPath(
  new URL("../../data/routes.json", import.meta.url)
);
const parsed: unknown = JSON.parse(await readFile(routesPath, "utf8"));

if (!Array.isArray(parsed) || parsed.length === 0) {
  console.error(
    "data/routes.json está vacío. Agrega las rutas reales antes de sembrar Firestore."
  );
  process.exit(1);
}

const routes = parsed.map((route, index) => validateRoute(route, index));
const projectId = process.env.GCLOUD_PROJECT ?? "pasajeya-f150d";
const app = initializeApp({ projectId });
const db = getFirestore(app);
const batchSize = 400;
let written = 0;
let stops = 0;

for (let offset = 0; offset < routes.length; offset += batchSize) {
  const batch = db.batch();
  const chunk = routes.slice(offset, offset + batchSize);

  for (const route of chunk) {
    batch.set(db.collection("routes").doc(route.id), {
      name: route.name,
      active: route.active,
      stops: route.stops
    });
    stops += route.stops.length;
  }

  await batch.commit();
  written += chunk.length;
}

console.log(`Rutas cargadas: ${written}. Paraderos cargados: ${stops}.`);

function validateRoute(value: unknown, index: number): RouteInput {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error(`La ruta en la posición ${index} no es un objeto válido.`);
  }

  const route = value as Record<string, unknown>;
  if (
    typeof route.id !== "string" ||
    !/^[A-Za-z0-9_-]{1,80}$/.test(route.id)
  ) {
    throw new Error(`La ruta en la posición ${index} tiene un id inválido.`);
  }
  if (
    typeof route.name !== "string" ||
    route.name.trim().length < 2 ||
    route.name.trim().length > 120
  ) {
    throw new Error(`La ruta ${route.id} tiene un nombre inválido.`);
  }
  if (typeof route.active !== "boolean") {
    throw new Error(`La ruta ${route.id} debe indicar active como booleano.`);
  }
  if (!Array.isArray(route.stops) || route.stops.length === 0) {
    throw new Error(`La ruta ${route.id} debe tener al menos un paradero.`);
  }

  const stops = route.stops.map((stop, stopIndex) =>
    validateStop(stop, route.id as string, stopIndex)
  );

  return {
    id: route.id,
    name: route.name.trim(),
    active: route.active,
    stops
  };
}

function validateStop(
  value: unknown,
  routeId: string,
  index: number
): StopInput {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error(`El paradero ${index} de la ruta ${routeId} no es válido.`);
  }

  const stop = value as Record<string, unknown>;
  if (typeof stop.name !== "string" || stop.name.trim().length < 2) {
    throw new Error(`El paradero ${index} de la ruta ${routeId} no tiene nombre.`);
  }
  if (
    typeof stop.lat !== "number" ||
    !Number.isFinite(stop.lat) ||
    stop.lat < -90 ||
    stop.lat > 90
  ) {
    throw new Error(`El paradero ${index} de la ruta ${routeId} tiene latitud inválida.`);
  }
  if (
    typeof stop.lng !== "number" ||
    !Number.isFinite(stop.lng) ||
    stop.lng < -180 ||
    stop.lng > 180
  ) {
    throw new Error(`El paradero ${index} de la ruta ${routeId} tiene longitud inválida.`);
  }

  return {
    name: stop.name.trim(),
    lat: stop.lat,
    lng: stop.lng
  };
}
