import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import type { AddressInfo } from "node:net";
import type { BridgeConfig } from "../src/config.js";
import { buildServer, type SafeLogRecord } from "../src/server.js";
import { FALLBACK_VERSION } from "../src/startup-greeting.js";

const token = "device-token-with-more-than-thirty-two-characters";

async function withServer(run: (baseUrl: string, logs: SafeLogRecord[]) => Promise<void>, requestFetch?: typeof fetch) {
  const directory = await mkdtemp(join(tmpdir(), "jglass-server-test-"));
  const config: BridgeConfig = {
    bindHost: "127.0.0.1", port: 0, deviceToken: token, wakePhrase: "Jarvis",
    elevenLabsApiKey: "provider-key-secret", elevenLabsVoiceId: "voice-secret",
    elevenLabsModelId: "eleven_flash_v2_5", weatherLatitude: 44.5,
    weatherLongitude: -73.2, timeZone: "America/New_York", dataDirectory: directory,
  };
  const provider: typeof fetch = async (input) => String(input).includes("open-meteo")
    ? new Response(JSON.stringify({
      current: { temperature_2m: 70, weather_code: 0 },
      daily: { time: [new Intl.DateTimeFormat("en-CA", { timeZone: config.timeZone }).format(new Date())], temperature_2m_max: [75], temperature_2m_min: [55], precipitation_probability_max: [10] },
    }), { headers: { "content-type": "application/json" } })
    : new Response(new Uint8Array([0x49, 0x44, 0x33]), { headers: { "content-type": "audio/mpeg" } });
  const logs: SafeLogRecord[] = [];
  const server = buildServer(config, requestFetch ?? provider, (record) => logs.push(record));
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const port = (server.address() as AddressInfo).port;
    await run(`http://127.0.0.1:${port}`, logs);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
}

test("server authenticates startup requests and logs metadata only", async () => {
  await withServer(async (baseUrl, logs) => {
    const unauthorized = await fetch(`${baseUrl}/v1/startup-greeting`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
    assert.equal(unauthorized.status, 401);
    const authorized = await fetch(`${baseUrl}/v1/startup-greeting`, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
      body: JSON.stringify({ protocol: "jglass/startup/1", fallbackVersion: FALLBACK_VERSION }),
    });
    assert.equal(authorized.status, 200);
    assert.equal((await authorized.json() as { protocol: string }).protocol, "jglass/startup/1");
    const serialized = JSON.stringify(logs);
    assert.doesNotMatch(serialized, /device-token|provider-key|voice-secret|latitude|spoken|audio/i);
    assert.deepEqual(logs.map((record) => record.statusCode), [401, 200]);
  });
});

test("server rejects malformed, oversized, and unsupported requests", async () => {
  await withServer(async (baseUrl) => {
    const headers = { "content-type": "application/json", authorization: `Bearer ${token}` };
    assert.equal((await fetch(`${baseUrl}/v1/startup-greeting`, { method: "POST", headers, body: "{" })).status, 400);
    assert.equal((await fetch(`${baseUrl}/v1/startup-greeting`, { method: "POST", headers, body: JSON.stringify({ protocol: "bad", padding: "x".repeat(5000) }) })).status, 413);
    assert.equal((await fetch(`${baseUrl}/other`)).status, 404);
  });
});

test("device configuration authenticates, exposes only its contract, and makes no provider calls", async () => {
  const fixture = JSON.parse(await readFile(new URL(
    "../../../../packages/protocol/fixtures/device-config/cases.json", import.meta.url,
  ), "utf8")) as { response: unknown };
  let providerCalls = 0;
  await withServer(async (baseUrl, logs) => {
    for (const authorization of [undefined, "Bearer incorrect", `Basic ${token}`]) {
      const response = await fetch(`${baseUrl}/v1/device-config`, {
        headers: authorization ? { authorization } : {},
      });
      assert.equal(response.status, 401);
    }
    const response = await fetch(`${baseUrl}/v1/device-config`, {
      headers: { authorization: `Bearer ${token}` },
    });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.equal(response.headers.get("content-type"), "application/json; charset=utf-8");
    assert.ok(Number(response.headers.get("content-length")) <= 1024);
    assert.deepEqual(await response.json(), fixture.response);
    assert.equal((await fetch(`${baseUrl}/v1/device-config`, { method: "POST" })).status, 404);
    assert.doesNotMatch(JSON.stringify(logs), /Jarvis|device-token|provider-key|voice-secret/i);
  }, async () => { providerCalls++; throw new Error("Configuration must not call a provider"); });
  assert.equal(providerCalls, 0);
});
