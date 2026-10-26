import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { beforeAll, describe, expect, it } from "vitest";
import {
  isValidBirthDate,
  isValidDni,
  isValidEmail,
  isValidLicenseClass,
  isValidLicenseNumber,
  isValidPersonName,
  isValidPlate,
  normalizeLicenseNumber,
  normalizePlate,
  validatePassword
} from "../src/validation/validators.js";

interface VectorCase {
  value: string;
  valid: boolean;
  reason: string;
  normalized?: string;
}

interface PasswordVectorCase extends VectorCase {
  email: string;
}

interface Vectors {
  referenceDate: string;
  email: VectorCase[];
  password: PasswordVectorCase[];
  dni: VectorCase[];
  personName: VectorCase[];
  birthDate: VectorCase[];
  plate: VectorCase[];
  licenseNumber: VectorCase[];
  licenseClass: VectorCase[];
}

let vectors: Vectors;
let commonPasswords: Set<string>;

beforeAll(async () => {
  const sharedPath = (name: string) =>
    fileURLToPath(new URL(`../../shared/${name}`, import.meta.url));

  vectors = JSON.parse(
    await readFile(sharedPath("validation-vectors.json"), "utf8")
  ) as Vectors;
  commonPasswords = new Set(
    JSON.parse(
      await readFile(sharedPath("common-passwords.json"), "utf8")
    ) as string[]
  );
});

describe("shared validation vectors", () => {
  it("includes at least one hundred common passwords", () => {
    expect(commonPasswords.size).toBeGreaterThanOrEqual(100);
  });

  it("validates email", () => {
    for (const testCase of vectors.email) {
      expect(isValidEmail(testCase.value), testCase.reason).toBe(testCase.valid);
    }
  });

  it("validates password", () => {
    for (const testCase of vectors.password) {
      expect(
        validatePassword(testCase.value, testCase.email, commonPasswords).valid,
        testCase.reason
      ).toBe(testCase.valid);
    }
  });

  it("validates DNI", () => {
    for (const testCase of vectors.dni) {
      expect(isValidDni(testCase.value), testCase.reason).toBe(testCase.valid);
    }
  });

  it("validates person names", () => {
    for (const testCase of vectors.personName) {
      expect(isValidPersonName(testCase.value), testCase.reason).toBe(
        testCase.valid
      );
    }
  });

  it("validates birth dates", () => {
    for (const testCase of vectors.birthDate) {
      expect(
        isValidBirthDate(testCase.value, vectors.referenceDate),
        testCase.reason
      ).toBe(testCase.valid);
    }
  });

  it("validates and normalizes plates", () => {
    for (const testCase of vectors.plate) {
      expect(isValidPlate(testCase.value), testCase.reason).toBe(testCase.valid);
      if (testCase.normalized !== undefined) {
        expect(normalizePlate(testCase.value), testCase.reason).toBe(
          testCase.normalized
        );
      }
    }
  });

  it("validates and normalizes license numbers", () => {
    for (const testCase of vectors.licenseNumber) {
      expect(isValidLicenseNumber(testCase.value), testCase.reason).toBe(
        testCase.valid
      );
      if (testCase.normalized !== undefined) {
        expect(normalizeLicenseNumber(testCase.value), testCase.reason).toBe(
          testCase.normalized
        );
      }
    }
  });

  it("validates license classes", () => {
    for (const testCase of vectors.licenseClass) {
      expect(isValidLicenseClass(testCase.value), testCase.reason).toBe(
        testCase.valid
      );
    }
  });

  it("reports password requirements individually", () => {
    const result = validatePassword(
      "abcdefgh",
      "pasajero@example.com",
      commonPasswords
    );

    expect(result.valid).toBe(false);
    expect(result.unmetRequirements).toEqual([
      "LENGTH",
      "UPPERCASE",
      "DIGIT",
      "SYMBOL"
    ]);
  });
});
