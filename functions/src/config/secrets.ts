import { defineSecret } from "firebase-functions/params";

export const qrSecret = defineSecret("QR_SECRET");
export const carnePepper = defineSecret("CARNE_PEPPER");
export const smtpHost = defineSecret("SMTP_HOST");
export const smtpPort = defineSecret("SMTP_PORT");
export const smtpUser = defineSecret("SMTP_USER");
export const smtpPass = defineSecret("SMTP_PASS");
export const smtpFrom = defineSecret("SMTP_FROM");
