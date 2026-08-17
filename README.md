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

Planning and repository bootstrap. No production code or third-party code has been imported yet.

Current milestone: prove a local-network, push-to-talk voice loop on stock XE24 before adding vision, remote access, wake words, or firmware changes.

## Repository map

```text
.
|-- AGENTS.md                 Codex's authoritative repository instructions
|-- apps/
|   |-- glass/                Explorer Edition client (Android API 19/GDK)
|   `-- ios/                  Optional future iPhone companion
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
