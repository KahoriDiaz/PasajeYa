import { describe, expect, it } from "vitest";
import { computeFare } from "../src/fare/computeFare.js";
import vectors from "../../shared/fare-vectors.json" assert { type: "json" };

describe("computeFare F3", () => {
  for (const v of vectors as any[]) {
    it(v.name, () => {
      const res = computeFare({
        distanceMeters: v.distanceMeters,
        version: {
          baseFarePerKmCents: v.baseFarePerKmCents,
          fuelIndex: v.fuelIndex,
          minFareCents: v.minFareCents,
          roundingStepCents: v.roundingStepCents
        },
        hasUniversityBenefit: v.hasUniversityBenefit
      });
      expect(res.regularCents).toBe(v.expectedRegularCents);
      expect(res.totalCents).toBe(v.expectedTotalCents);
      expect(res.minApplied).toBe(v.minApplied);
      expect(res.maxApplied).toBe(v.maxApplied);
      expect(res.totalCents).toBeGreaterThanOrEqual(0);
      expect(res.totalCents).toBeLessThanOrEqual(330);
      if (res.benefit) {
        expect(res.totalCents).toBeLessThanOrEqual(res.regularCents);
        expect(res.regularCents - res.benefit.discountCents).toBe(res.totalCents);
      } else {
        expect(res.regularCents).toBe(res.totalCents);
      }
    });
  }
});
