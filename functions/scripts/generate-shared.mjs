import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const sourcePath = fileURLToPath(
  new URL("../../shared/common-passwords.json", import.meta.url)
);
const outputPath = fileURLToPath(
  new URL("../src/generated/commonPasswords.ts", import.meta.url)
);

const parsed = JSON.parse(await readFile(sourcePath, "utf8"));
if (!Array.isArray(parsed) || parsed.length < 100) {
  throw new Error("shared/common-passwords.json debe tener al menos 100 entradas");
}

const passwords = parsed.map((value) => {
  if (typeof value !== "string" || value.length === 0) {
    throw new Error("shared/common-passwords.json contiene una entrada inválida");
  }
  return value.toLowerCase();
});

const uniquePasswords = [...new Set(passwords)];
const source = `// Archivo generado desde shared/common-passwords.json. No editar a mano.
export const COMMON_PASSWORDS: ReadonlySet<string> = new Set(${JSON.stringify(
  uniquePasswords,
  null,
  2
)});
`;

await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, source, "utf8");
