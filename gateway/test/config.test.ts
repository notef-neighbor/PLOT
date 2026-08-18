import { describe, expect, it } from "vitest";
import { readConfig } from "../src/config.js";

const tokenHash = "a".repeat(64);

describe("gateway provider configuration", () => {
  it("supports the OpenRouter free-model router", () => {
    const config = readConfig({
      AI_API_KEY: "not-a-real-openrouter-key",
      AI_BASE_URL: "https://openrouter.ai/api/v1/",
      AI_MODEL: "openrouter/free",
      ALLOW_OPENROUTER_FREE_ROUTING: "true",
      RECALL_DEVICE_TOKEN_HASHES: tokenHash,
    });
    expect(config.aiBaseUrl).toBe("https://openrouter.ai/api/v1");
    expect(config.aiModel).toBe("openrouter/free");
  });

  it("blocks OpenRouter free routing for personal history by default", () => {
    expect(() => readConfig({
      AI_API_KEY: "not-a-real-openrouter-key",
      AI_BASE_URL: "https://openrouter.ai/api/v1",
      AI_MODEL: "openrouter/free",
      RECALL_DEVICE_TOKEN_HASHES: tokenHash,
    })).toThrow(/blocked for personal history/);
  });

  it("keeps the OpenAI configuration compatible", () => {
    const config = readConfig({
      OPENAI_API_KEY: "not-a-real-openai-key",
      RECALL_DEVICE_TOKEN_HASHES: tokenHash,
    });
    expect(config.aiBaseUrl).toBe("https://api.openai.com/v1");
    expect(config.aiModel).toBe("gpt-5.6-terra");
  });

  it("rejects a non-HTTPS model endpoint", () => {
    expect(() => readConfig({
      AI_API_KEY: "not-a-real-openrouter-key",
      AI_BASE_URL: "http://example.test/v1",
      AI_MODEL: "openrouter/free",
      RECALL_DEVICE_TOKEN_HASHES: tokenHash,
    })).toThrow();
  });
});
