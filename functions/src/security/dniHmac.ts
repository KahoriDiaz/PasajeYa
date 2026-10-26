import { createHmac } from "node:crypto";

export function hashDni(dni: string, pepper: string): string {
  if (pepper.length === 0) {
    throw new Error("DNI_PEPPER no está configurado");
  }

  return createHmac("sha256", pepper).update(dni, "utf8").digest("hex");
}
