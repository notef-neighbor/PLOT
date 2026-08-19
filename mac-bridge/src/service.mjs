import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { chmod, mkdir, unlink, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const label = "com.plot.mac-history-bridge";
const uid = process.getuid();
const stateDirectory = path.join(os.homedir(), ".plot-history-bridge");
const agentDirectory = path.join(os.homedir(), "Library/LaunchAgents");
const plistPath = path.join(agentDirectory, `${label}.plist`);
const serverPath = path.join(path.dirname(fileURLToPath(import.meta.url)), "server.mjs");
const action = process.argv[2];

if (action === "install") {
  await mkdir(agentDirectory, { recursive: true });
  await mkdir(stateDirectory, { recursive: true, mode: 0o700 });
  await writeFile(plistPath, plist(), { mode: 0o600 });
  await chmod(plistPath, 0o600);
  runLaunchctl(["bootout", `gui/${uid}/${label}`], true);
  runLaunchctl(["bootstrap", `gui/${uid}`, plistPath]);
  runLaunchctl(["kickstart", "-k", `gui/${uid}/${label}`]);
  console.log("PLOT Mac History Bridge now starts automatically when you sign in.");
  console.log(`Pairing code and service logs: ${path.join(stateDirectory, "bridge.log")}`);
} else if (action === "uninstall") {
  runLaunchctl(["bootout", `gui/${uid}/${label}`], true);
  await unlink(plistPath).catch((error) => {
    if (error.code !== "ENOENT") throw error;
  });
  console.log("PLOT Mac History Bridge was removed from login items. Pairing credentials were preserved.");
} else {
  console.error("Usage: node src/service.mjs install|uninstall");
  process.exitCode = 2;
}

function plist() {
  const logPath = path.join(stateDirectory, "bridge.log");
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>${xml(label)}</string>
  <key>ProgramArguments</key><array><string>${xml(process.execPath)}</string><string>${xml(serverPath)}</string></array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>${xml(logPath)}</string>
  <key>StandardErrorPath</key><string>${xml(logPath)}</string>
</dict></plist>
`;
}

function runLaunchctl(arguments_, ignoreFailure = false) {
  try {
    execFileSync("/bin/launchctl", arguments_, { stdio: ignoreFailure ? "ignore" : "inherit" });
  } catch (error) {
    if (!ignoreFailure) throw error;
  }
}

function xml(value) {
  return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}
