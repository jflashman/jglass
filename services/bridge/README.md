# JGlass bridge

The bridge is a minimal Node 22+ / TypeScript service for the authenticated
trusted-LAN startup greeting. It uses built-in HTTP and `fetch`; there is no web
framework or provider SDK and no runtime package dependency.

## Configure

Set these uncommitted environment values:

- `ELEVENLABS_API_KEY` and the deployment owner's licensed
  `ELEVENLABS_VOICE_ID`;
- optional `ELEVENLABS_MODEL_ID` (default `eleven_flash_v2_5`);
- `JGLASS_WEATHER_LATITUDE`, `JGLASS_WEATHER_LONGITUDE`, and an IANA
  `JGLASS_TIME_ZONE`;
- a random `JGLASS_DEVICE_TOKEN` of 32–512 printable ASCII characters without spaces;
- optional `JGLASS_WAKE_PHRASE` (default `JARVIS`): one to four ASCII-letter words,
  at most 32 characters after space normalization; invalid explicit values
  prevent startup;
- optional `JGLASS_BIND_HOST` (loopback by default), `JGLASS_PORT` (8787), and
  ignored `JGLASS_DATA_DIRECTORY`.

Node does not implicitly load `.env`. A local shell may source an ignored copy:

```sh
cp .env.example .env
# edit .env without committing it
set -a
. ./.env
set +a
```

The startup greeting uses Open-Meteo current temperature/condition, daily
high/low, and maximum precipitation probability in Fahrenheit. Coordinates are
never returned or logged. ElevenLabs generates `mp3_22050_32` using the selected
voice. Start on free/PAYG usage; this code neither purchases nor enables a paid
plan.

## Build and run

```sh
cd services/bridge
npm ci
npm test
npm run typecheck
npm run build
node dist/src/server.js
```

With the existing Glass Java toolchain available, `npm run test:glass` also
checks the real Java HTTP client against a temporary Node loopback server.
It uses synthetic credentials and makes no provider calls.

Generate the six licensed boot fallback assets after sourcing the ignored
environment and before building the release APK. This command requires only the
three `ELEVENLABS_*` values; weather and device configuration are not involved:

```sh
npm run generate:boot-fallbacks
```

The generator writes only MP3s under `apps/glass/src/main/res/raw/` and
non-secret provenance/hashes under `docs/assets/`. It never writes the provider
key or voice ID. Do not substitute a cloned or impersonated voice.

The default listener is `127.0.0.1:8787`. Trusted-LAN binding is an explicit
operator decision, for example `JGLASS_BIND_HOST=192.0.2.10`; do not bind to
`0.0.0.0`, forward the port, or expose it to the public internet. Permit only the
Glass/client subnet in the host firewall.

`GET /health` returns non-sensitive process health. Authenticated
`GET /v1/device-config` returns only the protocol and normalized phrase, with no
provider calls; it follows
[`device configuration v1`](../../packages/protocol/DEVICE_CONFIG_V1.md).
Restart the bridge after changing its environment configuration. Authenticated
`POST /v1/startup-greeting` follows
[`../../packages/protocol/STARTUP_GREETING_V1.md`](../../packages/protocol/STARTUP_GREETING_V1.md).
Requests are bounded and timed out, redirects are rejected, and response audio
type/signature/size are checked. Logs contain event name, status, and duration
only—not keys, tokens, voice IDs, coordinates, spoken text, or audio.

Legacy fixed morning/afternoon/evening fallback MP3s may be cached under the
ignored bridge data directory for older clients. Current v0.6 clients bundle
their safety fallbacks. Dynamic weather audio is returned in memory and not
written by the bridge.
