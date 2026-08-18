import { timingSafeEqual } from "node:crypto";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { pathToFileURL } from "node:url";
import { loadConfig, type BridgeConfig } from "./config.js";
import {
  createStartupResponse,
  FallbackStore,
  parseStartupRequest,
  PublicError,
} from "./startup-greeting.js";

const MAX_REQUEST_BYTES = 4096;
const REQUEST_TIMEOUT_MILLIS = 11_000;

export interface SafeLogRecord {
  event: "request_complete" | "request_failed";
  statusCode: number;
  durationMs: number;
}

export type SafeLogger = (record: SafeLogRecord) => void;

function authenticated(request: IncomingMessage, token: string): boolean {
  const header = request.headers.authorization;
  const expected = `Bearer ${token}`;
  if (!header) return false;
  const suppliedBytes = Buffer.from(header);
  const expectedBytes = Buffer.from(expected);
  return suppliedBytes.length === expectedBytes.length && timingSafeEqual(suppliedBytes, expectedBytes);
}

async function readJson(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += bytes.length;
    if (size > MAX_REQUEST_BYTES) throw new PublicError(413, "Request body is too large");
    chunks.push(bytes);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new PublicError(400, "Request body is not valid JSON");
  }
}

function sendJson(response: ServerResponse, statusCode: number, body: unknown): void {
  const bytes = Buffer.from(JSON.stringify(body));
  response.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": bytes.length,
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  response.end(bytes);
}

export function buildServer(config: BridgeConfig, requestFetch: typeof fetch = fetch, logger: SafeLogger = console.log) {
  const fallbackStore = new FallbackStore(config.dataDirectory);
  return createServer(async (request, response) => {
    const started = Date.now();
    let statusCode = 500;
    try {
      if (request.method === "GET" && request.url === "/health") {
        statusCode = 200;
        sendJson(response, statusCode, { status: "ok" });
        return;
      }
      if (request.method !== "POST" || request.url !== "/v1/startup-greeting") {
        throw new PublicError(404, "Not found");
      }
      if (!authenticated(request, config.deviceToken)) {
        throw new PublicError(401, "Unauthorized");
      }
      if (request.headers["content-type"]?.split(";", 1)[0]?.trim().toLowerCase() !== "application/json") {
        throw new PublicError(415, "Content-Type must be application/json");
      }
      const startupRequest = parseStartupRequest(await readJson(request));
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);
      try {
        const startupResponse = await createStartupResponse(config, startupRequest, {
          fetch: requestFetch,
          now: () => new Date(),
          fallbackStore,
        }, controller.signal);
        statusCode = 200;
        sendJson(response, statusCode, startupResponse);
      } finally {
        clearTimeout(timeout);
      }
    } catch (error) {
      statusCode = error instanceof PublicError ? error.statusCode : 502;
      if (!response.headersSent) sendJson(response, statusCode, { error: statusCode === 502 ? "Provider request failed" : (error as Error).message });
    } finally {
      logger({ event: statusCode < 400 ? "request_complete" : "request_failed", statusCode, durationMs: Date.now() - started });
    }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const config = loadConfig();
    buildServer(config).listen(config.port, config.bindHost, () => {
      console.log(JSON.stringify({ event: "bridge_listening", host: config.bindHost, port: config.port }));
    });
  } catch (error) {
    console.error((error as Error).message);
    process.exitCode = 1;
  }
}
