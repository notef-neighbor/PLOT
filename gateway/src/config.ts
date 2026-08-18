import { z } from "zod";

const Environment = z.object({
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
  HOST: z.string().default("0.0.0.0"),
  AI_API_KEY: z.string().min(20).optional(),
  AI_BASE_URL: z.string().url().refine((value) => value.startsWith("https://"), "AI_BASE_URL must use HTTPS")
    .default("https://api.openai.com/v1"),
  AI_MODEL: z.string().min(1).optional(),
  ALLOW_OPENROUTER_FREE_ROUTING: z.enum(["true", "false"]).default("false"),
  OPENAI_API_KEY: z.string().min(20).optional(),
  OPENAI_MODEL: z.string().min(1).default("gpt-5.6-terra"),
  RECALL_DEVICE_TOKEN_HASHES: z.string().min(64),
  RATE_LIMIT_MAX: z.coerce.number().int().min(1).max(10_000).default(60),
});

export type GatewayConfig = {
  port: number;
  host: string;
  aiApiKey: string;
  aiBaseUrl: string;
  aiModel: string;
  deviceTokenHashes: Set<string>;
  rateLimitMax: number;
};

export function readConfig(environment: NodeJS.ProcessEnv = process.env): GatewayConfig {
  const parsed = Environment.parse(environment);
  const aiApiKey = parsed.AI_API_KEY ?? parsed.OPENAI_API_KEY;
  if (!aiApiKey) throw new Error("AI_API_KEY or OPENAI_API_KEY must be configured");
  const aiModel = parsed.AI_MODEL ?? parsed.OPENAI_MODEL;
  const isOpenRouter = parsed.AI_BASE_URL.includes("openrouter.ai");
  const isFreeRoute = aiModel === "openrouter/free" || aiModel.endsWith(":free");
  if (isOpenRouter && isFreeRoute && parsed.ALLOW_OPENROUTER_FREE_ROUTING !== "true") {
    throw new Error("OpenRouter free routing is blocked for personal history; set ALLOW_OPENROUTER_FREE_ROUTING=true only for non-sensitive development data");
  }
  const hashes = new Set(
    parsed.RECALL_DEVICE_TOKEN_HASHES.split(",")
      .map((value) => value.trim().toLowerCase())
      .filter((value) => /^[a-f0-9]{64}$/.test(value)),
  );
  if (hashes.size === 0) throw new Error("No valid device token hashes configured");
  return {
    port: parsed.PORT,
    host: parsed.HOST,
    aiApiKey,
    aiBaseUrl: parsed.AI_BASE_URL.replace(/\/$/, ""),
    aiModel,
    deviceTokenHashes: hashes,
    rateLimitMax: parsed.RATE_LIMIT_MAX,
  };
}
