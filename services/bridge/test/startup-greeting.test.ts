import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import type { BridgeConfig } from "../src/config.js";
import { loadConfig, loadSpeechConfig } from "../src/config.js";
import {
  createStartupResponse,
  FALLBACK_VERSION,
  FallbackStore,
  fallbackText,
  parseStartupRequest,
  periodForHour,
  PublicError,
  weatherCodeToWords,
} from "../src/startup-greeting.js";

const secret = "secret-value-that-must-never-appear";

function config(dataDirectory: string): BridgeConfig {
  return {
    bindHost: "127.0.0.1",
    port: 8787,
    deviceToken: secret.repeat(3),
    wakePhrase: "JARVIS",
    elevenLabsApiKey: secret,
    elevenLabsVoiceId: "licensed_voice",
    elevenLabsModelId: "eleven_flash_v2_5",
    weatherLatitude: 44.5,
    weatherLongitude: -73.2,
    timeZone: "America/New_York",
    dataDirectory,
  };
}

function weatherResponse() {
  return new Response(JSON.stringify({
    current: { temperature_2m: 71.6, weather_code: 2 },
    daily: {
      time: ["2026-08-18"],
      temperature_2m_max: [78.2],
      temperature_2m_min: [59.7],
      precipitation_probability_max: [34.6],
    },
  }), { status: 200, headers: { "content-type": "application/json" } });
}

test("environment validation and defaults", () => {
  assert.throws(() => loadConfig({}), /JGLASS_DEVICE_TOKEN/);
  const loaded = loadConfig({
    ELEVENLABS_API_KEY: secret,
    ELEVENLABS_VOICE_ID: "voice_1",
    JGLASS_WEATHER_LATITUDE: "44.5",
    JGLASS_WEATHER_LONGITUDE: "-73.2",
    JGLASS_TIME_ZONE: "America/New_York",
    JGLASS_DEVICE_TOKEN: secret.repeat(3),
  });
  assert.equal(loaded.bindHost, "127.0.0.1");
  assert.equal(loaded.elevenLabsModelId, "eleven_flash_v2_5");
  assert.throws(() => loadConfig({
    ELEVENLABS_API_KEY: secret,
    ELEVENLABS_VOICE_ID: "voice_1",
    JGLASS_WEATHER_LATITUDE: "44.5",
    JGLASS_WEATHER_LONGITUDE: "-73.2",
    JGLASS_TIME_ZONE: "America/New_York",
    JGLASS_DEVICE_TOKEN: secret.repeat(3),
    JGLASS_BIND_HOST: "0.0.0.0",
  }), /specific trusted-LAN address/);
  assert.deepEqual(loadSpeechConfig({
    ELEVENLABS_API_KEY: secret,
    ELEVENLABS_VOICE_ID: "voice_1",
  }), {
    elevenLabsApiKey: secret,
    elevenLabsVoiceId: "voice_1",
    elevenLabsModelId: "eleven_flash_v2_5",
  });
});

test("shared response fixture matches the bridge contract", async () => {
  const fixture = JSON.parse(await readFile(
    new URL("../../../../packages/protocol/fixtures/startup-greeting/success-with-fallbacks.json", import.meta.url),
    "utf8",
  )) as { protocol: string; fallbackAssets: { version: string } };
  assert.equal(fixture.protocol, "jglass/startup/1");
  assert.equal(fixture.fallbackAssets.version, FALLBACK_VERSION);
  assert.ok(Buffer.byteLength(JSON.stringify(fixture)) < 512 * 1024);
});

test("period boundaries and weather wording", () => {
  assert.equal(periodForHour(4), "evening");
  assert.equal(periodForHour(5), "morning");
  assert.equal(periodForHour(11), "morning");
  assert.equal(periodForHour(12), "afternoon");
  assert.equal(periodForHour(17), "afternoon");
  assert.equal(periodForHour(18), "evening");
  assert.equal(weatherCodeToWords(0), "clear");
  assert.equal(weatherCodeToWords(48), "foggy");
  assert.equal(weatherCodeToWords(96), "stormy");
  assert.equal(fallbackText("morning"), "Good morning, sir. The local assistant is unavailable.");
});

test("request parser rejects unsupported and oversized values", () => {
  assert.deepEqual(parseStartupRequest({ protocol: "jglass/startup/1", fallbackVersion: null }), {
    protocol: "jglass/startup/1", fallbackVersion: null,
  });
  assert.throws(() => parseStartupRequest({ protocol: "other", fallbackVersion: null }), PublicError);
  assert.throws(() => parseStartupRequest({ protocol: "jglass/startup/1", fallbackVersion: "x".repeat(65) }), PublicError);
});

test("weather response uses Fahrenheit fields and exact TTS request shape", async () => {
  const directory = await mkdtemp(join(tmpdir(), "jglass-bridge-test-"));
  const calls: Array<{ url: string; init: RequestInit | undefined }> = [];
  const mockFetch: typeof fetch = async (input, init) => {
    const url = String(input);
    calls.push({ url, init });
    if (url.startsWith("https://api.open-meteo.com/")) return weatherResponse();
    return new Response(new Uint8Array([0x49, 0x44, 0x33, 1]), { status: 200, headers: { "content-type": "audio/mpeg" } });
  };
  const response = await createStartupResponse(config(directory), {
    protocol: "jglass/startup/1",
    fallbackVersion: FALLBACK_VERSION,
  }, {
    fetch: mockFetch,
    now: () => new Date("2026-08-18T13:00:00Z"),
    fallbackStore: new FallbackStore(directory),
  }, new AbortController().signal);

  assert.equal(response.greetingDate, "2026-08-18");
  assert.equal(response.period, "morning");
  assert.equal(response.weather.summary, "Partly cloudy · 72°F · H 78° / L 60° · Rain 35%");
  assert.equal(response.weather.attribution, "Weather data by Open-Meteo.com");
  assert.equal(response.fallbackAssets, null);
  const weatherUrl = new URL(calls[0]!.url);
  assert.equal(weatherUrl.searchParams.get("temperature_unit"), "fahrenheit");
  assert.equal(weatherUrl.searchParams.get("timezone"), "America/New_York");
  assert.equal(weatherUrl.searchParams.get("current"), "temperature_2m,weather_code");
  assert.equal(weatherUrl.searchParams.get("daily"), "temperature_2m_max,temperature_2m_min,precipitation_probability_max");
  const tts = calls[1]!;
  assert.match(tts.url, /output_format=mp3_22050_32$/);
  assert.equal((tts.init?.headers as Record<string, string>)["xi-api-key"], secret);
  assert.deepEqual(JSON.parse(String(tts.init?.body)), {
    text: response.spokenText,
    model_id: "eleven_flash_v2_5",
  });
});

test("fallback clips are generated once and loaded from the private cache", async () => {
  const directory = await mkdtemp(join(tmpdir(), "jglass-bridge-cache-"));
  let speechCalls = 0;
  const mockFetch: typeof fetch = async (input) => {
    if (String(input).startsWith("https://api.open-meteo.com/")) return weatherResponse();
    speechCalls += 1;
    return new Response(new Uint8Array([0x49, 0x44, 0x33, speechCalls]), { status: 200, headers: { "content-type": "audio/mpeg" } });
  };
  const dependencies = {
    fetch: mockFetch,
    now: () => new Date("2026-08-18T20:00:00Z"),
    fallbackStore: new FallbackStore(directory),
  };
  const first = await createStartupResponse(config(directory), { protocol: "jglass/startup/1", fallbackVersion: null }, dependencies, new AbortController().signal);
  assert.equal(first.fallbackAssets?.version, FALLBACK_VERSION);
  assert.equal(speechCalls, 4);
  await createStartupResponse(config(directory), { protocol: "jglass/startup/1", fallbackVersion: null }, dependencies, new AbortController().signal);
  assert.equal(speechCalls, 5);
  assert.deepEqual(Array.from(await readFile(join(directory, `${FALLBACK_VERSION}-morning.mp3`))).slice(0, 3), [0x49, 0x44, 0x33]);
});

test("provider redirects, wrong MIME, malformed weather, and oversized audio fail closed", async () => {
  const directory = await mkdtemp(join(tmpdir(), "jglass-bridge-fail-"));
  const base = { protocol: "jglass/startup/1" as const, fallbackVersion: FALLBACK_VERSION };
  const run = (mockFetch: typeof fetch) => createStartupResponse(config(directory), base, {
    fetch: mockFetch,
    now: () => new Date("2026-08-18T13:00:00Z"),
    fallbackStore: new FallbackStore(directory),
  }, new AbortController().signal);
  await assert.rejects(run(async () => new Response(null, { status: 302 })), /Weather provider failed/);
  await assert.rejects(run(async (input) => String(input).includes("open-meteo")
    ? weatherResponse()
    : new Response("no", { status: 200, headers: { "content-type": "text/plain" } })), /unsupported audio type/);
  await assert.rejects(run(async () => new Response("{}", { status: 200, headers: { "content-type": "application/json" } })), /malformed data/);
  await assert.rejects(run(async (input) => String(input).includes("open-meteo")
    ? weatherResponse()
    : new Response(new Uint8Array(256 * 1024 + 1), { status: 200, headers: { "content-type": "audio/mpeg" } })), /size limit/);
});

test("aborted provider work is cancelled", async () => {
  const directory = await mkdtemp(join(tmpdir(), "jglass-bridge-abort-"));
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(createStartupResponse(config(directory), {
    protocol: "jglass/startup/1",
    fallbackVersion: FALLBACK_VERSION,
  }, {
    fetch: async (_input, init) => {
      assert.equal(init?.signal?.aborted, true);
      throw new DOMException("aborted", "AbortError");
    },
    now: () => new Date("2026-08-18T13:00:00Z"),
    fallbackStore: new FallbackStore(directory),
  }, controller.signal), /aborted/);
});

test("fallback provisioning failure does not discard a dynamic greeting", async () => {
  const directory = await mkdtemp(join(tmpdir(), "jglass-bridge-fallback-fail-"));
  const response = await createStartupResponse(config(directory), {
    protocol: "jglass/startup/1", fallbackVersion: null,
  }, {
    fetch: async (input, init) => {
      if (String(input).includes("open-meteo")) return weatherResponse();
      const request = JSON.parse(String(init?.body)) as { text: string };
      if (request.text.includes("assistant is unavailable")) return new Response("failed", { status: 503 });
      return new Response(new Uint8Array([0x49, 0x44, 0x33]), { headers: { "content-type": "audio/mpeg" } });
    },
    now: () => new Date("2026-08-18T13:00:00Z"),
    fallbackStore: new FallbackStore(directory),
  }, new AbortController().signal);
  assert.equal(response.fallbackAssets, null);
  assert.equal(response.audio.mimeType, "audio/mpeg");
});
