import { roundToStep } from "../utils/rounding.js";

export interface FareVersionInput {
  baseFarePerKmCents: number;
  fuelIndex: number;
  minFareCents: number;
  maxFareCents?: number;
  roundingStepCents: number;
}

export interface FareBenefitInput {
  kind: string;
  percent: number;
}

export interface FareBreakdown {
  distanceMeters: number;
  baseFarePerKmCents: number;
  fuelIndex: number;
  calculatedCents: number;
  regularCents: number;
  benefit: {
    kind: string;
    percent: number;
    discountCents: number;
  } | null;
  totalCents: number;
  capped: boolean;
  minApplied: boolean;
}

export interface ComputeFareInput {
  distanceMeters: number;
  version: FareVersionInput;
  hasUniversityBenefit?: boolean;
}

export interface ComputeFareResult {
  distanceMeters: number;
  baseFarePerKmCents: number;
  fuelIndex: number;
  calculatedCents: number;
  regularCents: number;
  benefit: {
    kind: "university";
    percent: 50;
    discountCents: number;
  } | null;
  totalCents: number;
  capped: boolean;
  minApplied: boolean;
  maxApplied: boolean;
}

const DEFAULT_MAX_FARE_CENTS = 330;

function assertPositiveInteger(value: number, name: string): void {
  if (!Number.isFinite(value) || value < 0 || !Number.isInteger(value)) {
    throw new Error(`${name} invÃ¡lido.`);
  }
}

function assertPositive(value: number, name: string): void {
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} invÃ¡lido.`);
  }
}

function assertRate(value: number, name: string): void {
  if (!Number.isFinite(value) || value < 0.1 || value > 10) {
    throw new Error(`${name} invÃ¡lido.`);
  }
}

export function computeFare(input: ComputeFareInput): ComputeFareResult {
  assertPositiveInteger(input.distanceMeters, "La distancia");
  assertPositive(input.version.baseFarePerKmCents, "La tarifa base por km");
  assertRate(input.version.fuelIndex, "El Ã­ndice de combustible");
  assertPositiveInteger(input.version.minFareCents, "La tarifa mÃ­nima");
  assertPositiveInteger(input.version.roundingStepCents, "El paso de redondeo");

  const distanceKm = input.distanceMeters / 1000;
  const raw = distanceKm * input.version.baseFarePerKmCents * input.version.fuelIndex;
  const calculated = Math.round(raw);
  const rounded = roundToStep(Math.round(raw), input.version.roundingStepCents);
  let regular = Math.max(rounded, input.version.minFareCents);
  const minApplied = rounded < input.version.minFareCents;
  let capped = false;
  let maxApplied = false;
  if (regular > DEFAULT_MAX_FARE_CENTS) {
    regular = DEFAULT_MAX_FARE_CENTS;
    capped = true;
    maxApplied = true;
  }

  let benefit: ComputeFareResult["benefit"] = null;
  let total = regular;
  if (input.hasUniversityBenefit) {
    const discounted = roundToStep(Math.round(regular * 0.5), input.version.roundingStepCents);
    const conBeneficio = discounted;
    const discountCents = regular - conBeneficio;
    if (discountCents < 0) {
      benefit = {
        kind: "university",
        percent: 50,
        discountCents: 0
      };
      total = regular;
    } else {
      benefit = {
        kind: "university",
        percent: 50,
        discountCents
      };
      total = conBeneficio;
    }
  }

  if (total > regular) {
    total = regular;
  }
  if (total > DEFAULT_MAX_FARE_CENTS) {
    total = DEFAULT_MAX_FARE_CENTS;
  }
  if (total < 0) {
    total = 0;
  }

  return {
    distanceMeters: input.distanceMeters,
    baseFarePerKmCents: input.version.baseFarePerKmCents,
    fuelIndex: input.version.fuelIndex,
    calculatedCents: calculated,
    regularCents: regular,
    benefit,
    totalCents: total,
    capped,
    minApplied,
    maxApplied
  };
}

export function buildFareBreakdown(
  result: ComputeFareResult,
  fareVersionId: string,
  _concessionaireName?: string | null
) {
  return {
    distanceMeters: result.distanceMeters,
    baseFarePerKmCents: result.baseFarePerKmCents,
    fuelIndex: result.fuelIndex,
    calculatedCents: result.calculatedCents,
    officialFareCents: DEFAULT_MAX_FARE_CENTS,
    regularCents: result.regularCents,
    benefit: result.benefit
      ? {
          kind: result.benefit.kind,
          percent: result.benefit.percent,
          officialUniversityFareCents: Math.round(result.regularCents * 0.5),
          discountCents: result.benefit.discountCents
        }
      : null,
    totalCents: result.totalCents,
    capped: result.capped,
    minApplied: result.minApplied,
    fareVersionId
  };
}
