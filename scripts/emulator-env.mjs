export const EMULATOR_ENV = {
  FUNCTIONS_DISCOVERY_TIMEOUT: "60",
  FUNCTIONS_EMULATOR_TIMEOUT_SECONDS: "60"
};

export function applyEmulatorEnv(env = process.env) {
  for (const [key, value] of Object.entries(EMULATOR_ENV)) {
    if (!env[key]) {
      env[key] = value;
    }
  }
  return env;
}
