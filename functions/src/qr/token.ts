import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";

export interface QrTokenPayload {
  uid: string;
  nonce: string;
  iat: number;
  exp: number;
}

export interface SignedQrToken extends QrTokenPayload {
  firma: string;
}

export interface CreatedQrToken {
  token: string;
  payload: SignedQrToken;
  expiresAt: number;
}

export type QrTokenFailure = "expired" | "invalid";

export class QrTokenError extends Error {
  constructor(public readonly failure: QrTokenFailure) {
    super(failure);
    this.name = "QrTokenError";
  }
}

export function createQrToken(
  uid: string,
  secret: string,
  nowMs: number = Date.now(),
  ttlSeconds = 60
): CreatedQrToken {
  if (uid.length === 0) throw new QrTokenError("invalid");
  if (secret.length === 0) throw new Error("QR_SECRET no estǭ configurado");

  const iat = Math.floor(nowMs / 1_000);
  const exp = iat + ttlSeconds;
  const payload: QrTokenPayload = {
    uid,
    nonce: cryptoRandomNonce(),
    iat,
    exp
  };
  const payloadPart = base64UrlEncode(Buffer.from(JSON.stringify(payload), "utf8"));
  const signature = sign(payloadPart, secret);
  const signed: SignedQrToken = { ...payload, firma: signature };

  return {
    token: `${payloadPart}.${signature}`,
    payload: signed,
    expiresAt: exp * 1_000
  };
}

export function verifyQrToken(
  token: string,
  secret: string,
  nowMs: number = Date.now()
): SignedQrToken {
  if (secret.length === 0) throw new Error("QR_SECRET no estǭ configurado");
  if (typeof token !== "string" || token.length > 2_048) {
    throw new QrTokenError("invalid");
  }

  const parts = token.split(".");
  if (parts.length !== 2 || parts[0].length === 0 || parts[1].length === 0) {
    throw new QrTokenError("invalid");
  }

  const [payloadPart, signature] = parts;
  const expected = sign(payloadPart, secret);
  if (!constantTimeEqual(signature, expected)) {
    throw new QrTokenError("invalid");
  }

  let payload: unknown;
  try {
    payload = JSON.parse(base64UrlDecode(payloadPart).toString("utf8"));
  } catch {
    throw new QrTokenError("invalid");
  }

  if (!isPayload(payload)) throw new QrTokenError("invalid");
  if (payload.exp * 1_000 <= nowMs) throw new QrTokenError("expired");

  return { ...payload, firma: signature };
}

function isPayload(value: unknown): value is QrTokenPayload {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.uid === "string" &&
    candidate.uid.length > 0 &&
    candidate.uid.length <= 128 &&
    typeof candidate.nonce === "string" &&
    candidate.nonce.length > 0 &&
    candidate.nonce.length <= 128 &&
    typeof candidate.iat === "number" &&
    Number.isInteger(candidate.iat) &&
    typeof candidate.exp === "number" &&
    Number.isInteger(candidate.exp) &&
    candidate.exp > candidate.iat
  );
}

function sign(payloadPart: string, secret: string): string {
  return base64UrlEncode(
    createHmac("sha256", secret).update(payloadPart, "utf8").digest()
  );
}

function constantTimeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left, "utf8");
  const rightBuffer = Buffer.from(right, "utf8");
  if (leftBuffer.length !== rightBuffer.length) return false;
  return timingSafeEqual(leftBuffer, rightBuffer);
}

function base64UrlEncode(buffer: Buffer): string {
  return buffer
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Buffer {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/");
  const padding = padded.length % 4;
  return Buffer.from(
    padding === 0 ? padded : padded + "=".repeat(4 - padding),
    "base64"
  );
}

function cryptoRandomNonce(): string {
  return base64UrlEncode(randomBytes(16));
}
