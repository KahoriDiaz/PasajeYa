export const OPERATOR_CONFIG = {
  platePattern: /^[A-Z0-9]{6}$/,
  licenseNumberPattern: /^[A-Z][0-9]{8}$/,
  licenseClasses: [
    "A-I",
    "A-IIa",
    "A-IIb",
    "A-IIIa",
    "A-IIIb",
    "A-IIIc",
    "B-I",
    "B-IIa",
    "B-IIb",
    "B-IIc"
  ] as const
} as const;

export function normalizePlate(value: string): string {
  return value.trim().toUpperCase().replace(/-/g, "");
}

export function normalizeLicenseNumber(value: string): string {
  return value.trim().toUpperCase();
}
