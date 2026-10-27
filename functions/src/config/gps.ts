export const GPS_CONFIG = {
  maxAccuracyMeters: 30,
  maxSpeedMetersPerSecond: 33,
  maxJumpMeters: 500,
  minMovementMeters: 5,
  minCoveragePercent: 60,
  minValidPoints: 3,
  movingIntervalMs: 5_000,
  stoppedIntervalMs: 15_000,
  chunkMaxPoints: 30,
  staleTripMs: 3 * 60 * 60 * 1_000
} as const;
