import { GPS_CONFIG } from "../config/gps.js";

const EARTH_RADIUS_METERS = 6_371_000;

export interface GpsPoint {
  lat: number;
  lng: number;
  acc: number;
  t: number;
}

export type DistanceReviewReason = "insufficient_gps";

export interface DistanceResult {
  distanceMeters: number;
  usedPoints: number;
  coverage: number;
  reviewReason?: DistanceReviewReason;
}

export function haversineMeters(a: GpsPoint, b: GpsPoint): number {
  const lat1 = toRadians(a.lat);
  const lat2 = toRadians(b.lat);
  const deltaLat = toRadians(b.lat - a.lat);
  const deltaLng = toRadians(b.lng - a.lng);

  const h =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
}

export function calculateDistance(
  rawPoints: GpsPoint[],
  startedAt: number,
  endedAt: number
): DistanceResult {
  if (
    !Number.isFinite(startedAt) ||
    !Number.isFinite(endedAt) ||
    endedAt <= startedAt
  ) {
    return insufficient();
  }

  const ordered = rawPoints
    .filter(
      (point) =>
        Number.isFinite(point.lat) &&
        Number.isFinite(point.lng) &&
        Number.isFinite(point.acc) &&
        Number.isFinite(point.t) &&
        point.lat >= -90 &&
        point.lat <= 90 &&
        point.lng >= -180 &&
        point.lng <= 180 &&
        point.t >= startedAt &&
        point.t <= endedAt
    )
    .sort((a, b) => a.t - b.t);

  const accepted: GpsPoint[] = [];
  for (const point of ordered) {
    if (point.acc <= 0 || point.acc > GPS_CONFIG.maxAccuracyMeters) continue;

    const previous = accepted[accepted.length - 1];
    if (previous === undefined) {
      accepted.push(point);
      continue;
    }

    const elapsedMs = point.t - previous.t;
    if (elapsedMs <= 0) continue;

    const jump = haversineMeters(previous, point);
    const speed = jump / (elapsedMs / 1_000);
    if (jump > GPS_CONFIG.maxJumpMeters || speed > GPS_CONFIG.maxSpeedMetersPerSecond) {
      continue;
    }

    accepted.push(point);
  }

  if (accepted.length < GPS_CONFIG.minValidPoints) {
    return insufficient();
  }

  let distanceMeters = 0;
  for (let index = 1; index < accepted.length; index += 1) {
    const previous = accepted[index - 1];
    const current = accepted[index];
    if (previous === undefined || current === undefined) continue;
    const segment = haversineMeters(previous, current);
    if (segment >= GPS_CONFIG.minMovementMeters) {
      distanceMeters += segment;
    }
  }

  const first = accepted[0];
  const last = accepted[accepted.length - 1];
  if (first === undefined || last === undefined) {
    return insufficient();
  }
  const coverage =
    ((last.t - first.t) / (endedAt - startedAt)) * 100;

  if (coverage < GPS_CONFIG.minCoveragePercent) {
    return insufficient();
  }

  return {
    distanceMeters: round(distanceMeters, 1),
    usedPoints: accepted.length,
    coverage: round(coverage, 1)
  };
}

function insufficient(): DistanceResult {
  return {
    distanceMeters: 0,
    usedPoints: 0,
    coverage: 0,
    reviewReason: "insufficient_gps"
  };
}

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180;
}

function round(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}
