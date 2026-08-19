import assert from "node:assert/strict";
import test from "node:test";
import { parseHistoryFile } from "../src/history.mjs";

test("parses a Computer History summary without exporting citation paths", () => {
  const memory = parseHistoryFile("2026-08-18T12-00-00-AbCd-6h-memory-summary.md", `---
title: PLOT work
description: Android and Mac history
applications: [com.openai.codex, com.google.Chrome]
---

## Memory summary

Worked on PLOT.

## Citations

- /private/raw/events.jsonl
`);
  assert.equal(memory.granularity, "6h");
  assert.equal(memory.startedAt, "2026-08-18T12:00:00.000Z");
  assert.equal(memory.endedAt, "2026-08-18T18:00:00.000Z");
  assert.deepEqual(memory.applications, ["Codex", "Chrome"]);
  assert.ok(memory.keywords.includes("com.openai.codex"));
  assert.match(memory.summary, /Worked on PLOT/);
  assert.doesNotMatch(memory.summary, /private\/raw/);
});

test("rejects unrelated markdown files", () => {
  assert.equal(parseHistoryFile("README.md", "hello"), null);
});
