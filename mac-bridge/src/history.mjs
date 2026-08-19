import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";

const FILE_PATTERN = /^(\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2})-[A-Za-z]{4}-(10min|6h)-.*\.md$/;

export async function readHistoryPage(directory, after = "", limit = 200) {
  const names = (await readdir(directory))
    .filter((name) => FILE_PATTERN.test(name) && name > after)
    .sort()
    .slice(0, Math.max(1, Math.min(limit, 500)));
  const entries = [];
  for (const name of names) {
    const parsed = parseHistoryFile(name, await readFile(path.join(directory, name), "utf8"));
    if (parsed) entries.push(parsed);
  }
  return {
    entries,
    nextCursor: names.at(-1) ?? after,
    hasMore: names.length === Math.max(1, Math.min(limit, 500)),
  };
}

export function parseHistoryFile(name, markdown) {
  const match = name.match(FILE_PATTERN);
  if (!match) return null;
  const startedAt = new Date(`${match[1].replace(/-(\d{2})-(\d{2})$/, ":$1:$2")}Z`);
  if (Number.isNaN(startedAt.valueOf())) return null;
  const durationMs = match[2] === "6h" ? 6 * 60 * 60 * 1_000 : 10 * 60 * 1_000;
  const { frontmatter, body } = splitFrontmatter(markdown);
  const title = scalar(frontmatter, "title") || name;
  const description = scalar(frontmatter, "description");
  const applicationIds = list(frontmatter, "applications");
  const applications = [...new Set(applicationIds.map(displayApplication))];
  const evidence = body.split(/^## Citations\s*$/m)[0].trim();
  const summary = evidence || description || title;
  return {
    id: createHash("sha256").update(name).digest("hex").slice(0, 32),
    cursor: name,
    granularity: match[2],
    startedAt: startedAt.toISOString(),
    endedAt: new Date(startedAt.valueOf() + durationMs).toISOString(),
    title: title.slice(0, 240),
    summary: summary.slice(0, 32_000),
    applications: applications.slice(0, 40),
    keywords: extractKeywords(title, description, [...applications, ...applicationIds]),
  };
}

function displayApplication(identifier) {
  const known = {
    "com.google.Chrome": "Chrome",
    "com.openai.codex": "Codex",
    "com.anthropic.claudefordesktop": "Claude",
    "com.apple.Terminal": "Terminal",
    "com.apple.finder": "Finder",
    "com.apple.dock": "Dock",
    "com.apple.controlcenter": "Control Center",
    "com.apple.systempreferences": "System Settings",
  };
  return known[identifier] || identifier;
}

function splitFrontmatter(markdown) {
  if (!markdown.startsWith("---\n")) return { frontmatter: "", body: markdown };
  const end = markdown.indexOf("\n---\n", 4);
  if (end < 0) return { frontmatter: "", body: markdown };
  return { frontmatter: markdown.slice(4, end), body: markdown.slice(end + 5) };
}

function scalar(frontmatter, key) {
  const value = frontmatter.match(new RegExp(`^${key}:\\s*(.+)$`, "m"))?.[1]?.trim() ?? "";
  return value.replace(/^['"]|['"]$/g, "");
}

function list(frontmatter, key) {
  const value = frontmatter.match(new RegExp(`^${key}:\\s*\\[(.*)\\]$`, "m"))?.[1] ?? "";
  return value.split(",").map((item) => item.trim().replace(/^['"]|['"]$/g, "")).filter(Boolean);
}

function extractKeywords(title, description, applications) {
  const words = `${title} ${description}`
    .split(/[\s、。/・:：,()[\]]+/)
    .map((word) => word.trim())
    .filter((word) => word.length >= 2 && word.length <= 60);
  return [...new Set([...applications, ...words])].slice(0, 60);
}
