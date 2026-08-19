import https from "node:https";
import os from "node:os";
import path from "node:path";
import { ensureCredentials } from "./credentials.mjs";
import { readHistoryPage } from "./history.mjs";

const home = os.homedir();
const historyDirectory = process.env.PLOT_MAC_HISTORY_DIR || path.join(home, ".codex/memories/extensions/skysight/resources");
const stateDirectory = process.env.PLOT_MAC_BRIDGE_STATE || path.join(home, ".plot-history-bridge");
const host = process.env.PLOT_MAC_BRIDGE_HOST || "0.0.0.0";
const port = Number(process.env.PLOT_MAC_BRIDGE_PORT || 47631);
const credentials = await ensureCredentials(stateDirectory);
const deviceId = process.env.PLOT_MAC_DEVICE_ID || credentials.deviceId;
const deviceName = process.env.PLOT_MAC_DEVICE_NAME || computerName();

const server = https.createServer({ key: credentials.key, cert: credentials.certificate }, async (request, response) => {
  try {
    const url = new URL(request.url ?? "/", `https://${request.headers.host || "localhost"}`);
    if (request.method === "GET" && url.pathname === "/health") {
      return json(response, 200, { ok: true, deviceId, deviceName });
    }
    if (request.headers.authorization !== `Bearer ${credentials.token}`) {
      return json(response, 401, { error: "unauthorized" });
    }
    if (request.method === "GET" && url.pathname === "/v1/history") {
      const page = await readHistoryPage(historyDirectory, url.searchParams.get("after") || "", Number(url.searchParams.get("limit") || 200));
      return json(response, 200, { version: 1, deviceId, deviceName, ...page });
    }
    return json(response, 404, { error: "not_found" });
  } catch (error) {
    console.error(error);
    return json(response, 500, { error: "internal_error" });
  }
});

server.listen(port, host, () => {
  const addresses = localAddresses();
  console.log(`PLOT Mac History Bridge is running on port ${port}.`);
  console.log(`Computer History: ${historyDirectory}`);
  console.log("Paste one pairing code into PLOT on Android:");
  for (const address of addresses) {
    const payload = Buffer.from(JSON.stringify({
      version: 1,
      url: `https://${address}:${port}`,
      token: credentials.token,
      tlsPin: credentials.pin,
      deviceId,
      deviceName,
    })).toString("base64url");
    console.log(`PLOT-MAC-1:${payload}`);
  }
});

function localAddresses() {
  const preferred = Object.values(os.networkInterfaces()).flat().filter((entry) =>
    entry && entry.family === "IPv4" && !entry.internal && !entry.address.startsWith("169.254."),
  ).map((entry) => entry.address);
  return [...new Set(preferred.length ? preferred : ["127.0.0.1"])];
}

function computerName() {
  return os.hostname().replace(/\.local$/i, "").replaceAll("-", " ");
}

function json(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  response.end(body);
}
