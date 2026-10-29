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
  concessionaireId: string;
  stops: StopInput[];
  distanceKm: number;
  officialFareCents: number;
  officialUniversityFareCents: number;
  fareSourceUrl: string;
  fareCheckedAt: string;
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
      concessionaireId: route.concessionaireId,
      stops: route.stops,
      distanceKm: route.distanceKm,
      officialFareCents: route.officialFareCents,
      officialUniversityFareCents: route.officialUniversityFareCents,
      fareSourceUrl: route.fareSourceUrl,
      fareCheckedAt: route.fareCheckedAt
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
  if (typeof route.concessionaireId !== "string") {
    throw new Error(`La ruta ${route.id} debe indicar concessionaireId como string.`);
  }
  if (!Array.isArray(route.stops) || route.stops.length === 0) {
    throw new Error(`La ruta ${route.id} debe tener al menos un paradero.`);
  }

  const stops = route.stops.map((stop, stopIndex) =>
    validateStop(stop, route.id as string, stopIndex)
  );

  const distanceKm = haversineDistanceKm(
    stops[0]?.lat ?? 0,
    stops[0]?.lng ?? 0,
    stops[stops.length - 1]?.lat ?? 0,
    stops[stops.length - 1]?.lng ?? 0
  );

  const officialFareCents = route.officialFareCents as number | undefined ?? getOfficialFareCents(route.concessionaireId);
  const officialUniversityFareCents =
    route.officialUniversityFareCents as number | undefined ??
    getOfficialUniversityFareCents(route.concessionaireId);
  const fareSourceUrl = route.fareSourceUrl as string | undefined ?? `https://elbuho.pe/?p=431669`;
  const fareCheckedAt = route.fareCheckedAt as string | undefined ?? new Date().toISOString();

  return {
    id: route.id,
    name: route.name.trim(),
    active: route.active,
    concessionaireId: route.concessionaireId,
    stops,
    distanceKm,
    officialFareCents,
    officialUniversityFareCents,
    fareSourceUrl,
    fareCheckedAt
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

function haversineDistanceKm(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const R = 6371;
  const toRad = (v: number) => (v * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
      Math.sin(dLng / 2) * Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const distance = R * c;
  return Math.round(distance * 1000) / 1000;
}

function getOfficialFareCents(concessionaireId: string): number {
  const fares: Record<string, number> = {
    "cono-norte": 100,
    "transcayma": 100,
    "union-aqp": 100,
    "etrabus": 100,
    "megabus": 100,
    "cotum-express": 100,
    "union-grau": 130,
    "aqp-masivo": 130,
    "sabandia-characato-c8": 130,
    "emarsistran": 130
  };
  return fares[concessionaireId] ?? 130;
}

function getOfficialUniversityFareCents(concessionaireId: string): number {
  const fares: Record<string, number> = {
    "cono-norte": 50,
    "transcayma": 50,
    "union-aqp": 50,
    "etrabus": 50,
    "megabus": 50,
    "cotum-express": 50,
    "union-grau": 65,
    "aqp-masivo": 65,
    "sabandia-characato-c8": 65,
    "emarsistran": 65
  };
  return fares[concessionaireId] ?? 65;
}
