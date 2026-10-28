export function roundToStep(value: number, step: number): number {
  if (step <= 0) return Math.round(value);
  const remainder = value % step;
  if (remainder === 0) return value;
  if (remainder > step / 2) {
    return value + (step - remainder);
  }
  if (remainder < step / 2) {
    return value - remainder;
  }
  return value + (step - remainder);
}
