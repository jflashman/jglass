import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { BridgeConfig } from "./config.js";

export const STARTUP_PROTOCOL = "jglass/startup/1";
export const FALLBACK_VERSION = "fallback-en-US-v1";
export const MAX_RESPONSE_BYTES = 512 * 1024;
const MAX_AUDIO_BYTES = 256 * 1024;
const MAX_FALLBACK_AUDIO_BYTES = 128 * 1024;
const WEATHER_ATTRIBUTION = "Weather data by Open-Meteo.com";

export type GreetingPeriod = "morning" | "afternoon" | "evening";

export interface StartupRequest {
  protocol: typeof STARTUP_PROTOCOL;
  fallbackVersion: string | null;
}

interface EncodedAudio {
  mimeType: "audio/mpeg";
  dataBase64: string;
}

export interface StartupResponse {
  protocol: typeof STARTUP_PROTOCOL;
  greetingDate: string;
  period: GreetingPeriod;
  spokenText: string;
  weather: {
    summary: string;
    attribution: typeof WEATHER_ATTRIBUTION;
  };
  audio: EncodedAudio;
  fallbackAssets: null | {
    version: typeof FALLBACK_VERSION;
    clips: Record<GreetingPeriod, EncodedAudio>;
  };
}

export interface StartupDependencies {
  fetch: typeof fetch;
  now: () => Date;
  fallbackStore: FallbackStore;
}

interface Weather {
  condition: string;
  temperature: number;
  high: number;
  low: number;
  precipitation: number;
}

export class PublicError extends Error {
  constructor(readonly statusCode: number, message: string) {
    super(message);
  }
}

export class FallbackStore {
  private readonly pending = new Map<GreetingPeriod, Promise<Uint8Array>>();

  constructor(private readonly directory: string) {}

  async getOrCreate(period: GreetingPeriod, generate: () => Promise<Uint8Array>): Promise<Uint8Array> {
    const existing = this.pending.get(period);
    if (existing) return existing;
    const operation = this.loadOrCreate(period, generate);
    this.pending.set(period, operation);
    try {
      return await operation;
    } finally {
      this.pending.delete(period);
    }
  }

  private async loadOrCreate(period: GreetingPeriod, generate: () => Promise<Uint8Array>): Promise<Uint8Array> {
    const filename = join(this.directory, `${FALLBACK_VERSION}-${period}.mp3`);
    try {
      const cached = await readFile(filename);
      validateAudio(cached, MAX_FALLBACK_AUDIO_BYTES);
      return cached;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") {
        throw error;
      }
    }

    const generated = await generate();
    validateAudio(generated, MAX_FALLBACK_AUDIO_BYTES);
    await mkdir(this.directory, { recursive: true, mode: 0o700 });
    const temporary = `${filename}.${process.pid}.${Date.now()}.tmp`;
    await writeFile(temporary, generated, { mode: 0o600 });
    await rename(temporary, filename);
    return generated;
  }
}

export function parseStartupRequest(value: unknown): StartupRequest {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new PublicError(400, "Request body must be a JSON object");
  }
  const record = value as Record<string, unknown>;
  if (record.protocol !== STARTUP_PROTOCOL) {
    throw new PublicError(400, "Unsupported startup protocol");
  }
  if (record.fallbackVersion !== null && typeof record.fallbackVersion !== "string") {
    throw new PublicError(400, "fallbackVersion must be a string or null");
  }
  if (typeof record.fallbackVersion === "string" && record.fallbackVersion.length > 64) {
    throw new PublicError(400, "fallbackVersion is too long");
  }
  return { protocol: STARTUP_PROTOCOL, fallbackVersion: record.fallbackVersion as string | null };
}

export function periodForHour(hour: number): GreetingPeriod {
  if (hour >= 5 && hour < 12) return "morning";
  if (hour >= 12 && hour < 18) return "afternoon";
  return "evening";
}

export function weatherCodeToWords(code: number): string {
  if (code === 0) return "clear";
  if (code === 1) return "mostly clear";
  if (code === 2) return "partly cloudy";
  if (code === 3) return "overcast";
  if (code === 45 || code === 48) return "foggy";
  if (code >= 51 && code <= 57) return "drizzly";
  if (code >= 61 && code <= 67) return "rainy";
  if (code >= 71 && code <= 77) return "snowy";
  if (code >= 80 && code <= 82) return "showery";
  if (code >= 85 && code <= 86) return "snowy";
  if (code >= 95 && code <= 99) return "stormy";
  return "unsettled";
}

export function fallbackText(period: GreetingPeriod): string {
  return `Good ${period}, sir. Nearby weather is unavailable. The Bluetooth assistant connection is not properly configured.`;
}

function dateParts(date: Date, timeZone: string): { date: string; hour: number } {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const get = (type: Intl.DateTimeFormatPartTypes): string => {
    const part = parts.find((candidate) => candidate.type === type)?.value;
    if (!part) throw new Error("Could not format configured local time");
    return part;
  };
  return { date: `${get("year")}-${get("month")}-${get("day")}`, hour: Number(get("hour")) };
}

function rounded(value: number): number {
  return Math.round(value);
}

function finiteNumber(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new PublicError(502, `Weather provider returned invalid ${field}`);
  }
  return value;
}

async function fetchWeather(config: BridgeConfig, requestFetch: typeof fetch, signal: AbortSignal, date: string): Promise<Weather> {
  const url = new URL("https://api.open-meteo.com/v1/forecast");
  url.searchParams.set("latitude", String(config.weatherLatitude));
  url.searchParams.set("longitude", String(config.weatherLongitude));
  url.searchParams.set("current", "temperature_2m,weather_code");
  url.searchParams.set("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max");
  url.searchParams.set("temperature_unit", "fahrenheit");
  url.searchParams.set("timezone", config.timeZone);
  url.searchParams.set("forecast_days", "1");

  const response = await requestFetch(url, { signal, redirect: "error", headers: { Accept: "application/json" } });
  if (!response.ok) throw new PublicError(502, "Weather provider failed");
  const contentType = response.headers.get("content-type")?.split(";", 1)[0]?.trim().toLowerCase();
  if (contentType !== "application/json") throw new PublicError(502, "Weather provider returned an unsupported type");
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(new TextDecoder().decode(await readBounded(response, 64 * 1024))) as Record<string, unknown>;
  } catch (error) {
    if (error instanceof PublicError) throw error;
    throw new PublicError(502, "Weather provider returned malformed data");
  }
  const current = body.current as Record<string, unknown> | undefined;
  const daily = body.daily as Record<string, unknown> | undefined;
  const times = daily?.time;
  const highs = daily?.temperature_2m_max;
  const lows = daily?.temperature_2m_min;
  const precipitation = daily?.precipitation_probability_max;
  if (!current || !Array.isArray(times) || !Array.isArray(highs) || !Array.isArray(lows)
      || !Array.isArray(precipitation) || times[0] !== date) {
    throw new PublicError(502, "Weather provider returned malformed data");
  }
  return {
    condition: weatherCodeToWords(finiteNumber(current.weather_code, "weather code")),
    temperature: finiteNumber(current.temperature_2m, "temperature"),
    high: finiteNumber(highs[0], "daily high"),
    low: finiteNumber(lows[0], "daily low"),
    precipitation: finiteNumber(precipitation[0], "precipitation probability"),
  };
}

function spokenWeather(period: GreetingPeriod, weather: Weather): string {
  return `Good ${period}, sir. Nearby, it is ${weather.condition} and ${rounded(weather.temperature)} degrees. Today's high is ${rounded(weather.high)}, with a low of ${rounded(weather.low)}. The chance of precipitation is ${rounded(weather.precipitation)} percent.`;
}

function weatherSummary(weather: Weather): string {
  const condition = weather.condition.charAt(0).toUpperCase() + weather.condition.slice(1);
  return `${condition} · ${rounded(weather.temperature)}°F · H ${rounded(weather.high)}° / L ${rounded(weather.low)}° · Rain ${rounded(weather.precipitation)}%`;
}

function validateAudio(audio: Uint8Array, maximum: number): void {
  const mp3Header = audio.byteLength >= 3 && (
    (audio[0] === 0x49 && audio[1] === 0x44 && audio[2] === 0x33)
    || (audio[0] === 0xff && (audio[1]! & 0xe0) === 0xe0)
  );
  if (!mp3Header || audio.byteLength > maximum) {
    throw new PublicError(502, "Speech provider returned invalid audio size");
  }
}

async function readBounded(response: Response, maximum: number): Promise<Uint8Array> {
  const declared = Number(response.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > maximum) {
    throw new PublicError(502, "Provider response exceeded its size limit");
  }
  if (!response.body) throw new PublicError(502, "Provider returned an empty response");
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  while (true) {
    const item = await reader.read();
    if (item.done) break;
    size += item.value.byteLength;
    if (size > maximum) {
      await reader.cancel();
      throw new PublicError(502, "Provider response exceeded its size limit");
    }
    chunks.push(item.value);
  }
  const result = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return result;
}

async function synthesize(config: BridgeConfig, requestFetch: typeof fetch, signal: AbortSignal, text: string, maximum = MAX_AUDIO_BYTES): Promise<Uint8Array> {
  const voiceId = encodeURIComponent(config.elevenLabsVoiceId);
  const url = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?output_format=mp3_22050_32`;
  const response = await requestFetch(url, {
    method: "POST",
    signal,
    redirect: "error",
    headers: {
      Accept: "audio/mpeg",
      "Content-Type": "application/json",
      "xi-api-key": config.elevenLabsApiKey,
    },
    body: JSON.stringify({ text, model_id: config.elevenLabsModelId }),
  });
  if (!response.ok) throw new PublicError(502, "Speech provider failed");
  const contentType = response.headers.get("content-type")?.split(";", 1)[0]?.trim().toLowerCase();
  if (contentType !== "audio/mpeg") throw new PublicError(502, "Speech provider returned an unsupported audio type");
  const audio = await readBounded(response, maximum);
  validateAudio(audio, maximum);
  return audio;
}

function encoded(audio: Uint8Array): EncodedAudio {
  return { mimeType: "audio/mpeg", dataBase64: Buffer.from(audio).toString("base64") };
}

async function provisionFallbacks(
  config: BridgeConfig,
  dependencies: StartupDependencies,
  signal: AbortSignal,
): Promise<NonNullable<StartupResponse["fallbackAssets"]>> {
  const periods: GreetingPeriod[] = ["morning", "afternoon", "evening"];
  const entries = await Promise.all(periods.map(async (period) => {
    const audio = await dependencies.fallbackStore.getOrCreate(period, () =>
      synthesize(config, dependencies.fetch, signal, fallbackText(period), MAX_FALLBACK_AUDIO_BYTES));
    return [period, encoded(audio)] as const;
  }));
  return {
    version: FALLBACK_VERSION,
    clips: Object.fromEntries(entries) as Record<GreetingPeriod, EncodedAudio>,
  };
}

export async function createStartupResponse(
  config: BridgeConfig,
  request: StartupRequest,
  dependencies: StartupDependencies,
  signal: AbortSignal,
): Promise<StartupResponse> {
  const local = dateParts(dependencies.now(), config.timeZone);
  const period = periodForHour(local.hour);
  const fallbackPromise = request.fallbackVersion === FALLBACK_VERSION
    ? Promise.resolve(null)
    : provisionFallbacks(config, dependencies, signal).catch(() => null);
  const weather = await fetchWeather(config, dependencies.fetch, signal, local.date);
  const spokenText = spokenWeather(period, weather);
  const audio = await synthesize(config, dependencies.fetch, signal, spokenText);
  const response: StartupResponse = {
    protocol: STARTUP_PROTOCOL,
    greetingDate: local.date,
    period,
    spokenText,
    weather: { summary: weatherSummary(weather), attribution: WEATHER_ATTRIBUTION },
    audio: encoded(audio),
    fallbackAssets: await fallbackPromise,
  };
  if (Buffer.byteLength(JSON.stringify(response), "utf8") > MAX_RESPONSE_BYTES) {
    throw new PublicError(502, "Startup response exceeded its size limit");
  }
  return response;
}
