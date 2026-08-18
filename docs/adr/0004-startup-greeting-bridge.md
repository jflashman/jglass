# ADR 0004: trusted-LAN startup greeting bridge

- Status: accepted
- Date: 2026-08-18

## Context

JGlass needs a thin API-19 startup experience that can obtain nearby weather and
licensed speech without placing provider credentials or precise coordinates on
Glass. The first implementation must be small enough to audit and must not make
the old Android TLS stack a public-network security boundary.

## Decision

Use a minimal TypeScript service on Node 22+ with built-in HTTP and `fetch`, no
web framework, and no provider SDK. Expose versioned authenticated
`POST /v1/startup-greeting` on loopback by default. A non-loopback bind is an
explicit trusted-LAN operator choice; public exposure is prohibited.

The bridge owns fixed coordinates and an IANA timezone, obtains Fahrenheit
current/daily data from Open-Meteo, formats compact deterministic text, and calls
ElevenLabs text-to-speech with `mp3_22050_32`. The default model is
`eleven_flash_v2_5` for low latency and may be overridden without code changes.
The deployment owner supplies a licensed custom voice ID. Begin on free/PAYG
usage; no subscription is purchased or enabled by software.

Glass and bridge share `jglass/startup/1`. The entire response is limited to 512
KiB. Glass refuses redirects, wrong protocol/MIME/attribution, malformed
JSON/Base64, unexpected dates, and oversized bodies. It records the local date
only when dynamic or cached fallback playback begins. The bridge may include
versioned fixed fallback MP3s when Glass reports them missing or stale.

## Consequences

Coordinates, provider credentials, and voice identifiers stay off Glass.
Open-Meteo attribution must accompany every displayed dynamic weather summary.
The device token and bridge URL remain sensitive app-private configuration and
are revocable by clearing/overwriting the app data. Plain HTTP is acceptable
only on the explicitly trusted LAN; away-from-home operation still requires the
separate authenticated relay milestone.

Fixed fallback audio is retained on bridge and Glass. Dynamic weather audio is
ephemeral and deleted from Glass after playback. Normal logs exclude secrets,
coordinates, text, and audio.
