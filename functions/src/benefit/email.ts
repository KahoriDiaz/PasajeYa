/**
 * Interfaz para el envío de correos electrónicos.
 */
export interface EmailProvider {
  sendVerificationCode(email: string, code: string): Promise<void>;
}

/**
 * Proveedor de correo de prueba (solo escribe en logs).
 */
export class ConsoleEmailProvider implements EmailProvider {
  async sendVerificationCode(email: string, code: string): Promise<void> {
    console.log(`[EMAIL TEST] Destinatario: ${email} | Código: ${code}`);
  }
}

// En el futuro se agregará SmtpEmailProvider usando nodemailer y los secretos configurados.
