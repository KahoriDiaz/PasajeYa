import { describe, expect, it, vi } from "vitest";
import {
  FUNCTIONS_REGION,
  healthHandler,
  type HealthHttpResponse
} from "../src/index.js";

describe("health", () => {
  it("uses the required region", () => {
    expect(FUNCTIONS_REGION).toBe("southamerica-east1");
  });

  it("responds with an ok payload", () => {
    const response = {
      status: vi.fn(),
      json: vi.fn()
    };

    healthHandler({}, response as unknown as HealthHttpResponse);

    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.json).toHaveBeenCalledWith({
      status: "ok",
      service: "pasajeya-functions"
    });
  });
});
