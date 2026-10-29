import { execSync } from "node:child_process";

process.env.PASAJEYA_EMULATOR_TESTS = "1";
const extraArgs = process.argv.slice(2);
const command =
  extraArgs.length === 0
    ? "npm --prefix functions test"
    : `npm --prefix functions test -- ${extraArgs.join(" ")}`;
execSync(command, { stdio: "inherit", env: process.env });
