import { createHash, timingSafeEqual } from "node:crypto";
import type { FastifyReply, FastifyRequest } from "fastify";

export function createDeviceAuthenticator(allowedHashes: ReadonlySet<string>) {
  const expected = [...allowedHashes].map((hash) => Buffer.from(hash, "hex"));
  return async function authenticate(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const authorization = request.headers.authorization;
    const token = authorization?.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
    if (token.length < 32 || token.length > 512) {
      await reply.code(401).send({ error: "unauthorized" });
      return;
    }
    const actual = createHash("sha256").update(token, "utf8").digest();
    const valid = expected.some((candidate) => candidate.length === actual.length && timingSafeEqual(candidate, actual));
    if (!valid) await reply.code(401).send({ error: "unauthorized" });
  };
}
