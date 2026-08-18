import rateLimit from "@fastify/rate-limit";
import Fastify, { LogController, type FastifyInstance } from "fastify";
import type { HistoryAi } from "./ai.js";
import { createDeviceAuthenticator } from "./auth.js";
import { AskInput, SummarizeInput } from "./schemas.js";

export type BuildAppOptions = {
  historyAi: HistoryAi;
  deviceTokenHashes: ReadonlySet<string>;
  rateLimitMax?: number;
};

export async function buildApp(options: BuildAppOptions): Promise<FastifyInstance> {
  const app = Fastify({
    logger: {
      level: process.env.LOG_LEVEL ?? "info",
      redact: ["req.headers.authorization", "request.headers.authorization"],
    },
    logController: new LogController({ disableRequestLogging: true }),
    bodyLimit: 2 * 1024 * 1024,
    requestIdHeader: "x-request-id",
  });

  await app.register(rateLimit, {
    max: options.rateLimitMax ?? 60,
    timeWindow: "1 minute",
    keyGenerator: (request) => request.ip,
  });

  const authenticate = createDeviceAuthenticator(options.deviceTokenHashes);

  app.get("/health", async () => ({ ok: true }));

  app.post("/v1/history/summarize", { preHandler: authenticate }, async (request, reply) => {
    const parsed = SummarizeInput.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: "invalid_request" });
    const summary = await options.historyAi.summarize(parsed.data);
    return reply.send(summary);
  });

  app.post("/v1/history/ask", { preHandler: authenticate }, async (request, reply) => {
    const parsed = AskInput.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: "invalid_request" });
    const answer = await options.historyAi.ask(parsed.data);
    return reply.send(answer);
  });

  app.setErrorHandler((error, request, reply) => {
    request.log.error({ err: error, requestId: request.id }, "request failed");
    void reply.code(500).send({ error: "internal_error", requestId: request.id });
  });

  return app;
}
