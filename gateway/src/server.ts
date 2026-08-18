import { OpenAiCompatibleHistoryAi } from "./ai.js";
import { buildApp } from "./app.js";
import { readConfig } from "./config.js";

const config = readConfig();
const app = await buildApp({
  historyAi: new OpenAiCompatibleHistoryAi(config.aiApiKey, config.aiModel, config.aiBaseUrl),
  deviceTokenHashes: config.deviceTokenHashes,
  rateLimitMax: config.rateLimitMax,
});

await app.listen({ host: config.host, port: config.port });
