// Explicit opt-in integration check: requires the project's existing Java toolchain.
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import type { AddressInfo } from "node:net";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { buildServer } from "../src/server.js";

const server = buildServer({
  bindHost: "127.0.0.1", port: 0,
  deviceToken: "synthetic-token-at-least-32-characters", wakePhrase: "Jarvis",
  elevenLabsApiKey: "unused", elevenLabsVoiceId: "unused", elevenLabsModelId: "unused",
  weatherLatitude: 0, weatherLongitude: 0, timeZone: "UTC", dataDirectory: ".",
}, async () => { throw new Error("Provider access is forbidden in this check"); }, () => {});

await new Promise<void>((resolve, reject) => {
  server.once("error", reject);
  server.listen(0, "127.0.0.1", resolve);
});
try {
  const baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  const { stdout } = await promisify(execFile)(fileURLToPath(new URL(
    "../../../../apps/glass/scripts/test-java.sh", import.meta.url,
  )), [baseUrl], { timeout: 30_000 });
  assert.match(stdout, /BridgeJsonClientSelfTest: PASS/);
  process.stdout.write(stdout);
  console.log("Java-to-Node configuration integration: PASS");
} finally {
  server.closeAllConnections();
  await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
}
