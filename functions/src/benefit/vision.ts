import { ImageAnnotatorClient } from "@google-cloud/vision";

/**
 * Interfaz para el reconocimiento de texto (OCR).
 */
export interface TextRecognizer {
  recognizeText(imageBuffer: Buffer): Promise<string>;
}

/**
 * Implementación real usando Google Cloud Vision API.
 */
export class CloudVisionRecognizer implements TextRecognizer {
  private client: ImageAnnotatorClient;

  constructor() {
    this.client = new ImageAnnotatorClient();
  }

  async recognizeText(imageBuffer: Buffer): Promise<string> {
    try {
      const [result] = await this.client.documentTextDetection(imageBuffer);
      const fullText = result.fullTextAnnotation?.text;

      if (!fullText) {
        throw new Error("No se detectó texto en la imagen.");
      }

      return fullText;
    } catch (error) {
      console.error("Error en Cloud Vision API:", error);
      throw new Error(
        "Error al procesar el carné con el servicio de reconocimiento de texto."
      );
    }
  }
}

/**
 * Implementación de prueba para tests.
 */
export class FakeTextRecognizer implements TextRecognizer {
  constructor(private mockText: string = "") {}

  async recognizeText(_imageBuffer: Buffer): Promise<string> {
    if (this.mockText === "FAIL") {
      throw new Error("Simulated Vision failure");
    }
    return this.mockText;
  }
}
