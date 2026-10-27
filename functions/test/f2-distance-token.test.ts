import { describe, expect, it } from "vitest";
import {
  calculateDistance,
  haversineMeters,
  type GpsPoint
} from "../src/geo/distance.js";
import {
  createQrToken,
  QrTokenError,
  verifyQrToken
} from "../src/qr/token.js";

const START = Date.UTC(2026, 9, 26, 12, 0, 0);
const SECRET = "test-qr-secret-with-enough-length";

describe("F2 GPS distance", () => {
  it("measures a straight synthetic route with less than 1% error", () => {
    const points = linePoints(0.001, 11);

    const result = calculateDistance(
      points,
      START,
      START + 100_000
    );

    expect(result.reviewReason).toBeUndefined();
    expect(result.usedPoints).toBe(11);
    expect(result.coverage).toBe(100);
    expect(Math.abs(result.distanceMeters - 1_111.95)).toBeLessThan(11.2);
  });

  it("discards points with poor accuracy and impossible jumps", () => {
    const points = linePoints(0.001, 11).map((point, index) => ({
      ...point,
      acc: index === 4 ? 45 : 5
    }));
    points[7] = {
      ...points[7],
      lat: points[7].lat + 0.02
    };

    const result = calculateDistance(points, START, START + 100_000);

    expect(result.reviewReason).toBeUndefined();
    expect(result.usedPoints).toBeLessThan(points.length);
    expect(result.distanceMeters).toBeLessThan(1_111.95 * 1.1);
  });

  it("flags a GPS gap that covers less than 60% of the trip", () => {
    const points = linePoints(0.002, 4);

    const result = calculateDistance(points, START, START + 1_000_000);

    expect(result).toMatchObject({
      distanceMeters: 0,
      usedPoints: 0,
      coverage: 0,
      reviewReason: "insufficient_gps"
    });
  });

  it("keeps a stopped vehicle at zero distance but full coverage", () => {
    const points = Array.from({ length: 6 }, (_, index) => ({
      lat: -16.4,
      lng: -71.5,
      acc: 4,
      t: START + index * 2_000
    }));

    const result = calculateDistance(points, START, START + 10_000);

    expect(result).toMatchObject({
      distanceMeters: 0,
      usedPoints: 6,
      coverage: 100
    });
  });

  it("requires at least three valid points", () => {
    const points = linePoints(0.01, 2);

    const result = calculateDistance(points, START, START + 10_000);

    expect(result.reviewReason).toBe("insufficient_gps");
  });

  it("accepts points only inside the trip interval", () => {
    const points = linePoints(0.001, 11);
    const outside = [
      { ...points[0], t: START - 10_000 },
      { ...points[10], t: START + 60_000 }
    ];
    const result = calculateDistance(
      [...outside, ...points.slice(0, 6)],
      START,
      START + 50_000
    );

    expect(result.reviewReason).toBeUndefined();
    expect(result.usedPoints).toBe(6);
  });

  it("uses haversine for short distances", () => {
    const distance = haversineMeters(
      { lat: -16.4, lng: -71.5, acc: 3, t: START },
      { lat: -16.4001, lng: -71.5, acc: 3, t: START + 1_000 }
    );

    expect(distance).toBeGreaterThan(11);
    expect(distance).toBeLessThan(12);
  });
});

describe("F2 QR token", () => {
  it("creates and verifies a signed token without personal data", () => {
    const created = createQrToken("passenger-uid", SECRET, START);

    const verified = verifyQrToken(created.token, SECRET, START + 1_000);

    expect(verified.uid).toBe("passenger-uid");
    expect(verified.exp - verified.iat).toBe(60);
    expect(verified.firma).toHaveLength(43);
    expect(created.token).not.toContain("passenger-uid");
  });

  it("uses a fresh nonce for every token", () => {
    const first = createQrToken("passenger-uid", SECRET, START);
    const second = createQrToken("passenger-uid", SECRET, START);

    expect(first.payload.nonce).not.toBe(second.payload.nonce);
  });

  it("rejects an expired token", () => {
    const created = createQrToken("passenger-uid", SECRET, START);

    expect(() =>
      verifyQrToken(created.token, SECRET, START + 61_000)
    ).toThrowError(QrTokenError);

    try {
      verifyQrToken(created.token, SECRET, START + 61_000);
    } catch (error) {
      expect((error as QrTokenError).failure).toBe("expired");
    }
  });

  it("rejects a manipulated payload or signature", () => {
    const created = createQrToken("passenger-uid", SECRET, START);
    const [payload, signature] = created.token.split(".");
    const changedPayload = `${payload.slice(0, 10)}A${payload.slice(11)}`;
    const changedSignature = `${signature.slice(0, 10)}A${signature.slice(11)}`;

    expect(() =>
      verifyQrToken(`${changedPayload}.${signature}`, SECRET, START)
    ).toThrowError(QrTokenError);
    expect(() =>
      verifyQrToken(`${payload}.${changedSignature}`, SECRET, START)
    ).toThrowError(QrTokenError);
    expect(() => verifyQrToken("not-a-token", SECRET, START)).toThrowError(
      QrTokenError
    );
  });
});

function linePoints(deltaLat: number, count: number): GpsPoint[] {
  return Array.from({ length: count }, (_, index) => ({
    lat: -16.4 + deltaLat * index,
    lng: -71.5,
    acc: 5,
    t: START + index * 10_000
  }));
}
