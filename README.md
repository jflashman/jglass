# JGlass

JGlass is a personal, experimental software project to turn a discontinued Google Glass Explorer Edition into a modern voice-and-vision interface for AI and remote computer work.

The project starts from a real Google Glass XE-C running the official XE24 firmware (`XRH35`, Android 4.4.4 / API 19 as reported by the connected device) with an unlocked bootloader. The immediate goal is not to force modern workloads onto decade-old hardware. Glass should be the lightweight microphone, camera, display, speaker, and gesture surface; a modern bridge service should perform AI inference, speech synthesis, authentication, and controlled computer delegation.

## Intended experience

- Invoke an assistant from Glass, initially with push-to-talk or a native Glass voice trigger.
- Ask a question and optionally include an intentional camera snapshot.
- Receive a compact visual response and spoken audio from an original licensed ElevenLabs voice.
- Route general conversation and vision requests to an OpenAI model.
- Route explicit coding/computer tasks to a restricted Codex worker with human approval before state-changing actions.
- Display useful iPhone notifications through public accessory mechanisms where technically possible.

Apple Watch-equivalent iPhone integration is not assumed. Apple ANCS can expose notifications and certain actions to a BLE accessory, but universal voice replies are not a public capability.

## Status

Early JGlass client implementation. The stock-XE24 Glass app has been exercised
on the physical device, and a provider-free iPhone companion baseline builds and
passes its tests in the iOS Simulator. A host-verified v0.5 client/bridge slice
now adds once-daily startup weather and licensed speech, plus deterministic
JGlass system-boot branding. The branded system image has now been physically
flashed and the JGlass animation verified; provider-backed greeting behavior
still requires physical verification.

Current milestone: measure foreground/background GATT reliability and compressed
BLE audio throughput. The physical Glass/iPhone pair has completed the bounded
fragmented hello/ping exchange, while companion-owned HFP proved unusable for
application audio. A bounded v0.4 reliability diagnostic now supports delayed
connected pings and repeated rediscovery/reconnection attempts. Physical testing
shows that an established GATT connection survives iPhone lock, while XE24 could
not rediscover the companion after ordinary backgrounding or lock.

In parallel, the trusted-LAN startup slice now has a dependency-free API-19
Canvas loader, authenticated Node/TypeScript bridge, Open-Meteo attribution,
ElevenLabs MP3 output, versioned fixed fallbacks, shared contract fixtures, and a
reversible XE24 boot-animation patch. Secrets and coordinates remain outside the
APK and repository.

## Repository map

```text
.
|-- AGENTS.md                 Codex's authoritative repository instructions
|-- apps/
|   |-- glass/                Explorer Edition client (Android API 19/GDK)
|   `-- ios/                  SwiftUI iPhone gateway diagnostic
|-- services/
|   `-- bridge/               Modern AI/audio/session bridge
|-- packages/
|   `-- protocol/             Versioned cross-component message schemas
|-- docs/
|   |-- PROJECT_CONTEXT.md    Hardware history, goals, constraints, known facts
|   |-- ARCHITECTURE.md       Proposed system and trust boundaries
|   |-- OPEN_SOURCE_POLICY.md License and attribution workflow
|   |-- ROADMAP.md            Milestones and acceptance criteria
|   `-- adr/                  Architecture decision records
`-- THIRD_PARTY_NOTICES.md    Exact provenance of imported/adapted work
```

## Start a Codex task

Open this repository as the task's project or start Codex from the repository root. Codex automatically reads `AGENTS.md`; project-scoped conservative permissions are in `.codex/config.toml` and apply after the repository is trusted.

A useful first prompt is:

> Read AGENTS.md and the linked project docs. Confirm the current milestone and propose the smallest testable implementation for a stock-XE24 push-to-talk Glass client and local bridge. Do not import third-party code or add dependencies until you have checked and documented their licenses.

## Documentation

- [Project context](docs/PROJECT_CONTEXT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Open-source policy](docs/OPEN_SOURCE_POLICY.md)
- [Security policy](SECURITY.md)

## Licensing status

This private repository does not yet declare a project-wide open-source license. Do not redistribute it or assume permission to reuse its original code until Joshua chooses a license. Third-party components retain their own licenses and notices.
