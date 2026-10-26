export type UserRole = "passenger" | "operator";

export function isUserRole(value: unknown): value is UserRole {
  return value === "passenger" || value === "operator";
}

export const OPERATOR_STATUS_UNVERIFIED = "unverified";
