import { spawn } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { applyEmulatorEnv } from "./emulator-env.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const firebaseBin = join(
  root,
  "node_modules",
  "firebase-tools",
  "lib",
  "bin",
  "firebase.js"
);

const child = spawn(process.execPath, [firebaseBin, ...process.argv.slice(2)], {
  stdio: "inherit",
  env: applyEmulatorEnv({ ...process.env })
});

child.on("error", (error) => {
  console.error(error);
  process.exit(1);
});

child.on("exit", (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});
