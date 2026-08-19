import { createHash, randomBytes, randomUUID, X509Certificate } from "node:crypto";
import { chmod, mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";

export async function ensureCredentials(stateDirectory) {
  await mkdir(stateDirectory, { recursive: true, mode: 0o700 });
  const configPath = path.join(stateDirectory, "config.json");
  const keyPath = path.join(stateDirectory, "bridge-key.pem");
  const certificatePath = path.join(stateDirectory, "bridge-cert.pem");
  if (!existsSync(configPath)) {
    await writeFile(configPath, JSON.stringify({ token: randomBytes(32).toString("base64url") }, null, 2), { mode: 0o600 });
  }
  if (!existsSync(keyPath) || !existsSync(certificatePath)) {
    const result = spawnSync("/usr/bin/openssl", [
      "req", "-x509", "-newkey", "rsa:2048", "-nodes",
      "-keyout", keyPath, "-out", certificatePath, "-days", "825",
      "-subj", "/CN=PLOT Mac History Bridge",
    ], { stdio: "pipe", encoding: "utf8" });
    if (result.status !== 0) throw new Error(`Could not create TLS certificate: ${result.stderr.trim()}`);
    await chmod(keyPath, 0o600);
    await chmod(certificatePath, 0o600);
  }
  const config = JSON.parse(await readFile(configPath, "utf8"));
  if (!config.deviceId) {
    config.deviceId = randomUUID();
    await writeFile(configPath, JSON.stringify(config, null, 2), { mode: 0o600 });
  }
  const key = await readFile(keyPath);
  const certificate = await readFile(certificatePath);
  const publicKey = new X509Certificate(certificate).publicKey.export({ type: "spki", format: "der" });
  const pin = `sha256/${createHash("sha256").update(publicKey).digest("base64")}`;
  return { token: config.token, deviceId: config.deviceId, key, certificate, pin };
}
