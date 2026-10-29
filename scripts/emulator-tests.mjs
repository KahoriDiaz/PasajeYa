import { execSync } from "node:child_process";
import { applyEmulatorEnv } from "./emulator-env.mjs";

applyEmulatorEnv();

function runNpmTest(prefix) {
  execSync(`npm --prefix ${prefix} test`, { stdio: "inherit" });
}

runNpmTest("firebase-tests");
process.env.PASAJEYA_EMULATOR_TESTS = "1";
runNpmTest("functions");
