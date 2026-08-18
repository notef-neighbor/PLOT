import { createHash } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { HistoryAi } from "../src/ai.js";
import { buildApp } from "../src/app.js";

const token = "a".repeat(64);
const tokenHash = createHash("sha256").update(token).digest("hex");

const fakeAi: HistoryAi = {
  summarize: async () => ({
    title: "資料を確認",
    summary: "ブラウザで資料を確認した。",
    keywords: ["資料"],
    status: "completed",
  }),
  ask: async () => ({ answer: "10時にブラウザで資料を確認しました。" }),
};

const app = await buildApp({ historyAi: fakeAi, deviceTokenHashes: new Set([tokenHash]) });

beforeAll(async () => app.ready());
afterAll(async () => app.close());

describe("gateway", () => {
  it("exposes a body-free health check", async () => {
    const response = await app.inject({ method: "GET", url: "/health" });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ ok: true });
  });

  it("rejects unauthenticated history requests", async () => {
    const response = await app.inject({ method: "POST", url: "/v1/history/ask", payload: {} });
    expect(response.statusCode).toBe(401);
  });

  it("validates and summarizes authenticated events", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/v1/history/summarize",
      headers: { authorization: `Bearer ${token}` },
      payload: {
        sessionId: "session-1",
        locale: "ja-JP",
        events: [{
          at: "2026-08-17T08:00:00Z",
          application: "Chrome",
          kind: "window_changed",
          text: "Project documentation",
          contentRedacted: false,
        }],
      },
    });
    expect(response.statusCode).toBe(200);
    expect(response.json().title).toBe("資料を確認");
  });

  it("rejects malformed payloads without forwarding them", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/v1/history/summarize",
      headers: { authorization: `Bearer ${token}` },
      payload: { sessionId: "bad", locale: "ja-JP", events: [] },
    });
    expect(response.statusCode).toBe(400);
  });
});
