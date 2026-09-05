import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { deviceConfig, normalizeWakePhrase } from "../src/device-config.js";
import { loadConfig } from "../src/config.js";

test("shared device configuration and phrase validation fixtures", async () => {
  const fixture = JSON.parse(await readFile(new URL(
    "../../../../packages/protocol/fixtures/device-config/cases.json", import.meta.url,
  ), "utf8")) as {
    response: unknown; valid: Array<{ input: string; expected: string }>; invalid: unknown[];
  };
  assert.deepEqual(deviceConfig("Jarvis"), fixture.response);
  for (const item of fixture.valid) assert.equal(normalizeWakePhrase(item.input), item.expected);
  for (const input of fixture.invalid) assert.throws(() => normalizeWakePhrase(input));
  assert.throws(() => normalizeWakePhrase(" ".repeat(129) + "Jarvis"));
});

test("environment defaults only an absent phrase; invalid explicit phrases fail without echoing input", () => {
  const environment = {
    ELEVENLABS_API_KEY: "unused", ELEVENLABS_VOICE_ID: "unused",
    JGLASS_WEATHER_LATITUDE: "0", JGLASS_WEATHER_LONGITUDE: "0",
    JGLASS_TIME_ZONE: "UTC", JGLASS_DEVICE_TOKEN: "x".repeat(32),
  };
  assert.equal(loadConfig(environment).wakePhrase, "JARVIS");
  assert.equal(loadConfig({ ...environment, JGLASS_WAKE_PHRASE: "  hey Jarvis " }).wakePhrase, "HEY JARVIS");
  for (const phrase of ["", "private-invalid-value!"]) {
    assert.throws(() => loadConfig({ ...environment, JGLASS_WAKE_PHRASE: phrase }), (error: Error) => {
      assert.match(error.message, /JGLASS_WAKE_PHRASE/);
      if (phrase) assert.ok(!error.message.includes(phrase));
      return true;
    });
  }
});
