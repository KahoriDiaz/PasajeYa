import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const secretPath = join(root, "functions", ".secret.local");
const requiredSecrets = ["DNI_PEPPER", "QR_SECRET"];

const contents = existsSync(secretPath)
  ? readFileSync(secretPath, "utf8")
  : "";
const present = new Set(
  contents
    .split(/\r?\n/)
    .map((line) => line.split("=")[0]?.trim())
    .filter(Boolean)
);
const missing = requiredSecrets.filter((name) => !present.has(name));

if (missing.length > 0) {
  const lines = missing.map(
    (name) => `${name}=${randomBytes(32).toString("hex")}`
  );
  const separator = contents.length > 0 && !contents.endsWith("\n") ? "\n" : "";
  writeFileSync(secretPath, `${contents}${separator}${lines.join("\n")}\n`, "utf8");
  console.log(
    `Se agregaron secretos solo para emuladores: ${missing.join(", ")}.`
  );
}
