# JGlass Codex Instructions

## Project identity

JGlass is Joshua's personal Google Glass Explorer Edition research project. It is unrelated to Circadence, Brink, Microsoft SIW, M76, or any employer/client work. Never import terminology, files, credentials, infrastructure, or assumptions from those projects.

Before substantive work, read:

1. `README.md`
2. `docs/PROJECT_CONTEXT.md`
3. `docs/ARCHITECTURE.md`
4. `docs/OPEN_SOURCE_POLICY.md`
5. `docs/ROADMAP.md`

Treat those files as the durable handoff from the original planning conversation. Update them when decisions or verified hardware behavior change.

## Mission

Turn a Google Glass Explorer Edition XE-C running XE24 into a useful, voice-first personal AI interface that can:

- show selected iPhone notifications and offer the actions that public Apple APIs permit;
- provide a voice-and-camera assistant backed by OpenAI models, with ElevenLabs speech output using a licensed original voice;
- delegate explicitly approved work to Codex running on Joshua's computer and return compact spoken/visual results.

The preferred architecture keeps Glass thin. Glass captures voice or an intentional camera frame, renders concise cards, and plays audio. Modern AI, secrets, networking, policy, and computer actions live in a bridge service off-device.

## Current hardware baseline

- Device: Google Glass Explorer Edition, XE-C / Explorer v3.
- Firmware: official XE24, successfully flashed and booted.
- Bootloader: unlocked.
- OS baseline: the connected XE24 unit reports Android 4.4.4 / API 19 (`XRH35`), with the Glass Development Kit behavior retained.
- Reliable bootloader host: Kali Linux on a 2017 MacBook Air.
- Windows ADB/recovery worked, but Windows Fastboot enumeration was unreliable. Do not assume Windows Fastboot works.

Do not flash firmware, root the device, erase partitions, replace recovery, or migrate to AOSP 5.1.1 without Joshua's explicit approval in the active task. Preserve a path back to official XE24.

## Product and engineering priorities

1. Prove a safe push-to-talk loop on stock XE24: capture speech, call the bridge, display text, and play streamed speech.
2. Add an intentional camera snapshot to a multimodal request. Do not begin with continuous vision.
3. Add a Codex worker behind an explicit proposal/approval/result flow.
4. Prototype iPhone notification display through ANCS as an independent workstream. Do not promise universal text replies; public ANCS does not provide Apple Watch parity.
5. Explore a wake word only after latency, heat, and battery measurements are acceptable.

Prefer the smallest end-to-end slice over broad scaffolding. Preserve the native Glass interaction model until evidence shows that AOSP 5.1.1 is required.

## Architecture boundaries

- `apps/glass/`: Java/Kotlin-compatible Android client constrained by API 19 and the 640x360 Glass display. Prefer Java where library/toolchain compatibility is materially better.
- `services/bridge/`: modern typed service for session orchestration, OpenAI, ElevenLabs, authentication, and protocol translation.
- `apps/ios/`: optional iPhone companion for project-owned data and service-specific actions. It cannot read every other app's notifications; ANCS consumption belongs on the BLE accessory side.
- `packages/protocol/`: versioned transport schemas shared conceptually across components. Do not make the Glass build depend on a runtime it cannot support.
- Computer/Codex execution is a separate privilege boundary from ordinary assistant conversation.

Never put OpenAI, ElevenLabs, GitHub, relay, or computer-access credentials in an APK, source file, QR code committed to Git, log, screenshot, or client-visible configuration. Secrets stay in the bridge's environment or an OS secret store.

## AI implementation rules

- Use the current OpenAI Responses API for new model-backed orchestration unless current official documentation establishes a better supported interface for the requirement.
- Use the Codex SDK for coding-agent delegation; do not imitate Codex with unrestricted shell execution.
- Do not pin a model merely because it was current when this repository was created. Select models from current official documentation and record important choices in an ADR.
- Stream partial assistant text to speech when it improves latency, but preserve interruption/cancellation semantics.
- Make tool calls typed, narrowly scoped, auditable, and idempotent where practical.
- Separate conversation from action. A spoken request may produce a proposed action; state-changing computer work requires an explicit approval event.
- Treat camera, microphone, notifications, transcripts, and computer context as sensitive data. Minimize collection and retention.
- Build evaluation fixtures for latency, transcription accuracy, tool approval, and unsafe-action rejection before making behavior autonomous.

## Open-source reuse rules

Follow `docs/OPEN_SOURCE_POLICY.md` before copying code or assets.

- Verify the license in the upstream repository at the exact commit being used.
- No declared license means reference-only: do not copy, translate, or create a close derivative without written permission.
- Record every copied or adapted component in `THIRD_PARTY_NOTICES.md`, including repository URL, commit SHA, license, files used, and modifications.
- Preserve copyright and license notices.
- Ask Joshua before introducing GPL/AGPL code into a component because it may determine distribution obligations for that component or combined work.
- Prefer clean-room reimplementation of protocols and ideas when licensing is absent or incompatible.

## Working practices

- Keep changes personal-project scoped and inside this repository.
- Never commit `.env` files, API keys, device identifiers, Wi-Fi credentials, private keys, recordings, captured notifications, or camera images.
- Use feature branches for non-trivial work and keep commits focused.
- Add or update an ADR when choosing a transport, public network exposure, an AI provider/model strategy, a firmware baseline, or a copyleft dependency.
- Update documentation and third-party notices in the same change as the behavior they describe.
- Run the narrowest relevant tests, then broader checks in proportion to risk. Report exactly what was and was not verified on physical Glass.
- Clearly distinguish observed device behavior from assumptions and online reports.

Ask before:

- destructive device operations;
- making the repository public;
- purchasing or enabling paid services;
- exposing any endpoint to the public internet;
- granting a computer worker access outside configured workspaces;
- sending messages, committing, pushing, or opening pull requests unless the active request already authorizes it.

## Definition of done

A change is complete when the implementation, relevant tests, documentation, security implications, and open-source attribution are all updated. For hardware work, include a short reproducible test procedure and state whether it was verified on the XE24 device, an emulator, or neither.
