import { randomBytes } from "node:crypto";
import { existsSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const secretPath = join(root, "functions", ".secret.local");

if (!existsSync(secretPath)) {
  const testPepper = randomBytes(32).toString("hex");
  writeFileSync(secretPath, `DNI_PEPPER=${testPepper}\n`, "utf8");
  console.log("Se creó functions/.secret.local con un secreto solo para emuladores.");
}
