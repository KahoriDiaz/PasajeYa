export interface OperatorVerificationInput {
  uid: string;
  licenseNumber: string;
  licenseClass: string;
  plate: string;
}

export type OperatorVerificationOutcome =
  | "verified"
  | "rejected"
  | "unavailable";

export interface OperatorVerificationResult {
  outcome: OperatorVerificationOutcome;
  source: string | null;
}

export interface OperatorVerifier {
  verify(input: OperatorVerificationInput): Promise<OperatorVerificationResult>;
}

export class UnavailableOperatorVerifier implements OperatorVerifier {
  async verify(
    _input: OperatorVerificationInput
  ): Promise<OperatorVerificationResult> {
    return {
      outcome: "unavailable",
      source: null
    };
  }
}

export const operatorVerifier: OperatorVerifier =
  new UnavailableOperatorVerifier();
