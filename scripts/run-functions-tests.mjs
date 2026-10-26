import { execSync } from "node:child_process";

process.env.PASAJEYA_EMULATOR_TESTS = "1";
execSync("npm --prefix functions test", { stdio: "inherit", env: process.env });
