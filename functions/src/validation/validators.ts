import {
  normalizeLicenseNumber,
  normalizePlate,
  OPERATOR_CONFIG
} from "../config/operators.js";

const EMAIL_PATTERN =
  /^[a-z0-9._%+-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$/;
const NAME_PATTERN = /^\p{L}+(?:[ '-]\p{L}+)*$/u;
const DNI_PATTERN = /^\d{8}$/;
const ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const LIMA_DATE_FORMAT = new Intl.DateTimeFormat("en-CA", {
  timeZone: "America/Lima",
  year: "numeric",
  month: "2-digit",
  day: "2-digit"
});

export const PASSWORD_REQUIREMENTS = [
  "LENGTH",
  "UPPERCASE",
  "LOWERCASE",
  "DIGIT",
  "SYMBOL",
  "NO_WHITESPACE",
  "NOT_EMAIL_LOCAL_PART",
  "NOT_COMMON"
] as const;

export type PasswordRequirement = (typeof PASSWORD_REQUIREMENTS)[number];

export type PasswordStrength = "WEAK" | "FAIR" | "GOOD" | "STRONG";

export interface PasswordValidation {
  valid: boolean;
  unmetRequirements: PasswordRequirement[];
  strength: PasswordStrength;
}

export function isValidEmail(value: string): boolean {
  if (value.length < 6 || value.length > 100 || !EMAIL_PATTERN.test(value)) {
    return false;
  }

  const localPart = value.slice(0, value.indexOf("@"));
  return (
    !localPart.startsWith(".") &&
    !localPart.endsWith(".") &&
    !localPart.includes("..")
  );
}

export function validatePassword(
  value: string,
  email: string,
  commonPasswords: ReadonlySet<string>
): PasswordValidation {
  const localPart = email.split("@")[0]?.toLowerCase() ?? "";
  const unmet = new Set<PasswordRequirement>();

  if (value.length < 10 || value.length > 64) unmet.add("LENGTH");
  if (!/[A-Z]/.test(value)) unmet.add("UPPERCASE");
  if (!/[a-z]/.test(value)) unmet.add("LOWERCASE");
  if (!/[0-9]/.test(value)) unmet.add("DIGIT");
  if (!/[^A-Za-z0-9\s]/.test(value)) unmet.add("SYMBOL");
  if (/\s/.test(value)) unmet.add("NO_WHITESPACE");

  const lowerValue = value.toLowerCase();
  if (localPart.length > 0 && lowerValue.includes(localPart)) {
    unmet.add("NOT_EMAIL_LOCAL_PART");
  }
  if (commonPasswords.has(lowerValue)) unmet.add("NOT_COMMON");

  const unmetRequirements = PASSWORD_REQUIREMENTS.filter((requirement) =>
    unmet.has(requirement)
  );
  const metCount = PASSWORD_REQUIREMENTS.length - unmetRequirements.length;
  const strength: PasswordStrength =
    metCount <= 2
      ? "WEAK"
      : metCount <= 4
        ? "FAIR"
        : metCount <= 6
          ? "GOOD"
          : "STRONG";

  return {
    valid: unmetRequirements.length === 0,
    unmetRequirements,
    strength
  };
}

export function isValidDni(value: string): boolean {
  return DNI_PATTERN.test(value) && new Set(value).size > 1;
}

export function isValidPersonName(value: string): boolean {
  return value.length >= 2 && value.length <= 40 && NAME_PATTERN.test(value);
}

export function todayInLima(now: Date = new Date()): string {
  return LIMA_DATE_FORMAT.format(now);
}

export function isValidBirthDate(
  value: string,
  referenceDate: string = todayInLima()
): boolean {
  const birthDate = parseIsoDate(value);
  const today = parseIsoDate(referenceDate);
  if (birthDate === null || today === null) return false;

  if (compareDates(birthDate, today) > 0) return false;

  let age = today.year - birthDate.year;
  if (
    today.month < birthDate.month ||
    (today.month === birthDate.month && today.day < birthDate.day)
  ) {
    age -= 1;
  }

  return age >= 12 && age <= 110;
}

export function isValidPlate(value: string): boolean {
  return OPERATOR_CONFIG.platePattern.test(normalizePlate(value));
}

export function isValidLicenseNumber(value: string): boolean {
  return OPERATOR_CONFIG.licenseNumberPattern.test(
    normalizeLicenseNumber(value)
  );
}

export function isValidLicenseClass(value: string): boolean {
  return (OPERATOR_CONFIG.licenseClasses as readonly string[]).includes(value);
}

export { normalizeLicenseNumber, normalizePlate };

interface IsoDate {
  year: number;
  month: number;
  day: number;
}

function parseIsoDate(value: string): IsoDate | null {
  const match = ISO_DATE_PATTERN.exec(value);
  if (match === null) return null;

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));

  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return null;
  }

  return { year, month, day };
}

function compareDates(left: IsoDate, right: IsoDate): number {
  if (left.year !== right.year) return left.year - right.year;
  if (left.month !== right.month) return left.month - right.month;
  return left.day - right.day;
}
