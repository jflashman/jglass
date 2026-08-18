import { resolve } from "node:path";

export interface SpeechConfig {
  elevenLabsApiKey: string;
  elevenLabsVoiceId: string;
  elevenLabsModelId: string;
}

export interface BridgeConfig extends SpeechConfig {
  bindHost: string;
  port: number;
  deviceToken: string;
  weatherLatitude: number;
  weatherLongitude: number;
  timeZone: string;
  dataDirectory: string;
}

export function loadSpeechConfig(environment: NodeJS.ProcessEnv = process.env): SpeechConfig {
  const voiceId = required(environment, "ELEVENLABS_VOICE_ID");
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(voiceId)) {
    throw new Error("ELEVENLABS_VOICE_ID contains unsupported characters");
  }

  const modelId = environment.ELEVENLABS_MODEL_ID?.trim() || "eleven_flash_v2_5";
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(modelId)) {
    throw new Error("ELEVENLABS_MODEL_ID contains unsupported characters");
  }

  return {
    elevenLabsApiKey: required(environment, "ELEVENLABS_API_KEY"),
    elevenLabsVoiceId: voiceId,
    elevenLabsModelId: modelId,
  };
}

function required(environment: NodeJS.ProcessEnv, name: string): string {
  const value = environment[name]?.trim();
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function coordinate(environment: NodeJS.ProcessEnv, name: string, minimum: number, maximum: number): number {
  const raw = required(environment, name);
  const value = Number(raw);
  if (!Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error(`Invalid coordinate in environment variable: ${name}`);
  }
  return value;
}

function validTimeZone(value: string): boolean {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: value }).format();
    return true;
  } catch {
    return false;
  }
}

export function loadConfig(environment: NodeJS.ProcessEnv = process.env): BridgeConfig {
  const token = required(environment, "JGLASS_DEVICE_TOKEN");
  if (token.length < 32) {
    throw new Error("JGLASS_DEVICE_TOKEN must contain at least 32 characters");
  }

  const speech = loadSpeechConfig(environment);

  const timeZone = required(environment, "JGLASS_TIME_ZONE");
  if (!validTimeZone(timeZone)) {
    throw new Error("JGLASS_TIME_ZONE is not an IANA time zone");
  }

  const port = Number(environment.JGLASS_PORT || "8787");
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("JGLASS_PORT must be an integer from 1 through 65535");
  }

  const bindHost = environment.JGLASS_BIND_HOST?.trim() || "127.0.0.1";
  if (bindHost === "0.0.0.0" || bindHost === "::" || bindHost === "[::]") {
    throw new Error("JGLASS_BIND_HOST must be loopback or a specific trusted-LAN address");
  }

  return {
    bindHost,
    port,
    deviceToken: token,
    ...speech,
    weatherLatitude: coordinate(environment, "JGLASS_WEATHER_LATITUDE", -90, 90),
    weatherLongitude: coordinate(environment, "JGLASS_WEATHER_LONGITUDE", -180, 180),
    timeZone,
    dataDirectory: resolve(environment.JGLASS_DATA_DIRECTORY?.trim() || "services/bridge/data"),
  };
}
