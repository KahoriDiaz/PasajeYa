import { describe, expect, it } from "vitest";
import {
  compareNames,
  containsUniversityKeywords,
  extractStudentCode,
  isExpired,
  isValidInstitutionalEmail,
  normalizeText
} from "../src/benefit/logic.js";

describe("Benefit Logic", () => {
  describe("normalizeText", () => {
    it("removes accents and converts to uppercase", () => {
      expect(normalizeText("María José")).toBe("MARIA JOSE");
    });

    it("removes special characters and extra spaces", () => {
      expect(normalizeText("  UCSM-2023!!  ")).toBe("UCSM 2023");
    });
  });

  describe("containsUniversityKeywords", () => {
    it("returns true if all keywords are present", () => {
      const text = "Universidad Católica de Santa María";
      expect(containsUniversityKeywords(text)).toBe(true);
    });

    it("returns false if any keyword is missing", () => {
      const text = "Universidad de Santa María";
      expect(containsUniversityKeywords(text)).toBe(false);
    });
  });

  describe("extractStudentCode", () => {
    it("extracts a valid 10-digit code starting with 19 or 20", () => {
      const text = "Alumno: 2023202051 - Facultad de Ingeniería";
      expect(extractStudentCode(text)).toBe("2023202051");
    });

    it("returns null if no valid code is found", () => {
      const text = "Alumno: 1234567890";
      expect(extractStudentCode(text)).toBe(null);
    });

    it("returns null if multiple valid codes are found", () => {
      const text = "2023202051 and 1923202051";
      expect(extractStudentCode(text)).toBe(null);
    });
  });

  describe("compareNames", () => {
    it("matches identical names", () => {
      expect(compareNames("Ana María", "Quispe Pérez", "ANA MARIA QUISPE PEREZ")).toBe(true);
    });

    it("matches names with minor typos in long words", () => {
      // "QUISPE" has 6 letters, so it allows 1 typo -> "QUIPSE"
      expect(compareNames("Ana", "Quispe", "ANA QUIPSE")).toBe(true);
    });

    it("matches if 75% of words match", () => {
      // 3 of 4 words match (75%)
      expect(compareNames("Juan Carlos", "Mamani Rodriguez", "JUAN CARLOS MAMANI")).toBe(true);
    });

    it("fails if match ratio is too low", () => {
      // 1 of 4 words match (25%)
      expect(compareNames("Juan Carlos", "Mamani Rodriguez", "JUAN PEREZ")).toBe(false);
    });
  });

  describe("isValidInstitutionalEmail", () => {
    it("validates correct institutional email", () => {
      expect(isValidInstitutionalEmail("2023202051@ucsm.edu.pe")).toBe(true);
    });

    it("rejects non-institutional domains", () => {
      expect(isValidInstitutionalEmail("2023202051@gmail.com")).toBe(false);
    });

    it("rejects emails with aliases", () => {
      expect(isValidInstitutionalEmail("2023202051+extra@ucsm.edu.pe")).toBe(false);
    });

    it("rejects malformed emails", () => {
      expect(isValidInstitutionalEmail("invalid-email")).toBe(false);
    });
  });

  describe("isExpired", () => {
    const fixedNow = new Date("2026-10-30");

    it("detects expired date with keyword VENCE", () => {
      const text = "VENCE: 31/12/2025";
      expect(isExpired(text, fixedNow)).toBe(true);
    });

    it("detects expired date with keyword HASTA", () => {
      const text = "VIGENTE HASTA: 15-05-2026";
      expect(isExpired(text, fixedNow)).toBe(true);
    });

    it("ignores valid future dates", () => {
      const text = "EXPIRA: 31/12/2026";
      expect(isExpired(text, fixedNow)).toBe(false);
    });

    it("ignores past dates without expiration keywords", () => {
      const text = "FECHA NACIMIENTO: 01/01/2000";
      expect(isExpired(text, fixedNow)).toBe(false);
    });
  });
});
