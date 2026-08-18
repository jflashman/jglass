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
only when dynamic or bundled fallback playback begins. The bridge may include
versioned fixed fallback MP3s when an older Glass client reports them missing or
stale.

Android `BOOT_COMPLETED` now triggers the experience. The receiver only checks
the local daily/debug rule and schedules a wakeup Activity three seconds later;
it does not wait, access the network, or play audio. The short delay and
boot-only screen flags let the XE24 home timeline settle before playback. Boot
launches have a 30-second Activity-owned deadline
for network readiness and at most three authenticated requests. Production
speaks once per local day. A persisted `daily|always` replay mode is accepted
only by debuggable builds.

Current APKs bundle six fixed licensed clips so the first offline boot can still
speak. Period-specific clips distinguish confirmed Bluetooth-off from a general
local-assistant failure; Wi-Fi, bridge, or provider failures are never
misreported as Bluetooth failures. The legacy response field remains wire
compatible, but current clients report `bundled-en-US-v2` and do not require
bridge provisioning.

## Consequences

Coordinates, provider credentials, and voice identifiers stay off Glass.
Open-Meteo attribution must accompany every displayed dynamic weather summary.
The device token and bridge URL remain sensitive app-private configuration and
are revocable by clearing/overwriting the app data. Plain HTTP is acceptable
only on the explicitly trusted LAN; away-from-home operation still requires the
separate authenticated relay milestone.

Fixed fallback audio is packaged in Glass and may remain in the bridge's legacy
private cache. Dynamic weather audio is ephemeral and deleted from Glass after
playback. Normal logs exclude secrets, coordinates, text, and audio.
