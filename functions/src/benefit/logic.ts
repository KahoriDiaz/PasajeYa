import { createHmac, randomInt, timingSafeEqual } from "node:crypto";
import {
  NAME_MATCH_RATIO,
  NAME_MIN_TOKENS,
  STUDENT_CODE_PATTERN,
  STUDENT_EMAIL_PATTERN,
  UNIVERSITY_DOMAINS,
  UNIVERSITY_KEYWORDS
} from "../config/benefit.js";

/**
 * Normaliza un texto: elimina tildes, convierte a mayúsculas y quita caracteres no alfanuméricos básicos.
 */
export function normalizeText(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/[^A-Z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Comprueba si el texto contiene todas las palabras clave de la universidad.
 */
export function containsUniversityKeywords(
  text: string,
  keywords: string[] = UNIVERSITY_KEYWORDS
): boolean {
  const normalizedText = normalizeText(text);
  return keywords.every((kw) => normalizedText.includes(normalizeText(kw)));
}

/**
 * Extrae candidatos a código de estudiante y devuelve el único válido si existe.
 */
export function extractStudentCode(
  text: string,
  pattern: RegExp = STUDENT_CODE_PATTERN
): string | null {
  const words = text.split(/\s+/);
  const candidates = words.filter((w) => pattern.test(w));
  const uniqueCandidates = [...new Set(candidates)];

  return uniqueCandidates.length === 1 ? uniqueCandidates[0]! : null;
}

/**
 * Compara los nombres del perfil con el texto OCR.
 * Exige que al menos el X% de las palabras del nombre aparezcan en el texto.
 * Permite 1 error de edición en palabras largas (>= 5 letras).
 */
export function compareNames(
  profileFirstName: string,
  profileLastName: string,
  ocrText: string,
  ratio: number = NAME_MATCH_RATIO,
  minTokens: number = NAME_MIN_TOKENS
): boolean {
  const normalizedOcr = normalizeText(ocrText);
  const ocrWords = normalizedOcr.split(/\s+/);

  const profileWords = normalizeText(`${profileFirstName} ${profileLastName}`)
    .split(/\s+/)
    .filter((w) => w.length >= 2);

  if (profileWords.length < minTokens) return false;

  let matches = 0;
  for (const pWord of profileWords) {
    if (hasWordMatch(pWord, ocrWords)) {
      matches++;
    }
  }

  return matches / profileWords.length >= ratio && matches >= minTokens;
}

function hasWordMatch(word: string, tokens: string[]): boolean {
  for (const token of tokens) {
    if (word === token) return true;
    if (word.length >= 5 && levenshteinDistance(word, token) <= 1) return true;
  }
  return false;
}

/**
 * Distancia de Levenshtein simple para pequeñas correcciones.
 */
function levenshteinDistance(a: string, b: string): number {
  if (Math.abs(a.length - b.length) > 1) return 99;
  const matrix = Array.from({ length: a.length + 1 }, () =>
    new Array(b.length + 1).fill(0)
  );

  for (let i = 0; i <= a.length; i++) matrix[i]![0] = i;
  for (let j = 0; j <= b.length; j++) matrix[0]![j] = j;

  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      matrix[i]![j] = Math.min(
        matrix[i - 1]![j]! + 1,
        matrix[i]![j - 1]! + 1,
        matrix[i - 1]![j - 1]! + cost
      );
    }
  }
  return matrix[a.length]![b.length]!;
}

/**
 * Valida si un correo es institucional y cumple el patrón.
 * Evita dominios engañosos y maneja normalización.
 */
export function isValidInstitutionalEmail(
  email: string,
  domains: string[] = UNIVERSITY_DOMAINS,
  pattern: RegExp = STUDENT_EMAIL_PATTERN
): boolean {
  const parts = email.split("@");
  if (parts.length !== 2) return false;

  const localPart = parts[0]!;
  const domain = parts[1]!.toLowerCase();

  // No permitir alias (+)
  if (localPart.includes("+")) return false;

  if (!domains.includes(domain)) return false;

  return pattern.test(email);
}

/**
 * Intenta detectar una fecha de vigencia en el texto.
 */
export function isExpired(text: string, now: Date = new Date()): boolean {
  const datePattern = /(\d{2})[\/\-](\d{2})[\/\-](\d{4})/g;
  let match;
  while ((match = datePattern.exec(text)) !== null) {
    const day = parseInt(match[1]!, 10);
    const month = parseInt(match[2]!, 10);
    const year = parseInt(match[3]!, 10);
    if (day < 1 || day > 31 || month < 1 || month > 12) continue;
    const date = new Date(year, month - 1, day);

    if (year > 2020 && date.getTime() < now.getTime()) {
      const start = Math.max(0, match.index - 20);
      const context = text.substring(start, match.index).toUpperCase();
      if (context.includes("VENCE") || context.includes("EXPIRA") || context.includes("HASTA")) {
        return true;
      }
    }
  }
  return false;
}

/**
 * Genera un código de 6 dígitos aleatorio.
 */
export function generate6DigitCode(): string {
  return randomInt(100000, 1000000).toString();
}

/**
 * Genera un HMAC SHA256.
 */
export function computeHmac(data: string, secret: string): string {
  return createHmac("sha256", secret).update(data).digest("hex");
}

/**
 * Compara dos hashes en tiempo constante.
 */
export function safeCompare(a: string, b: string): boolean {
  const bufferA = Buffer.from(a);
  const bufferB = Buffer.from(b);
  if (bufferA.length !== bufferB.length) return false;
  return timingSafeEqual(bufferA, bufferB);
}
