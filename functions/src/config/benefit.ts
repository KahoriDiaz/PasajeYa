/**
 * Configuración para el beneficio universitario.
 * Valores marcados como CONFIRMAR deben ser verificados con datos reales.
 */

// Dominio autorizado de la universidad (UCSM)
export const UNIVERSITY_DOMAINS = ["ucsm.edu.pe"]; // CONFIRMAR

// Patrón para el correo institucional (ej: 2023202051@ucsm.edu.pe)
export const STUDENT_EMAIL_PATTERN = /^\d{10}@ucsm\.edu\.pe$/i; // CONFIRMAR

// Palabras clave que deben aparecer en el carné para identificar la universidad
export const UNIVERSITY_KEYWORDS = ["CATOLICA", "SANTA", "MARIA"];

// Patrón para el código de estudiante (10 dígitos que empiezan por 19 o 20)
export const STUDENT_CODE_PATTERN = /^(19|20)\d{8}$/;

// Ratio mínimo de palabras del nombre que deben coincidir (75 %)
export const NAME_MATCH_RATIO = 0.75;

// Mínimo de palabras que deben coincidir
export const NAME_MIN_TOKENS = 2;

// Calidad mínima de la imagen
export const MIN_IMAGE_WIDTH = 800;
export const BLUR_THRESHOLD = 1000; // Varianza del Laplaciano (ajustable)

// Vigencia
export const REQUIRE_VALIDITY_DATE = false; // Si no trae fecha, se acepta
export const BENEFIT_VALID_DAYS = 180;

// Límites de abuso
export const MAX_CARNE_SUBMISSIONS_PER_DAY = 3;
export const MAX_CODE_REQUESTS_PER_HOUR = 3;
export const MAX_CODE_REQUESTS_PER_DAY = 5;
export const MAX_CODE_ATTEMPTS = 5;
export const CODE_EXPIRY_MINUTES = 10;

// ¿El número del correo debe coincidir con el código del carné?
export const EMAIL_MUST_MATCH_CODE = true;
