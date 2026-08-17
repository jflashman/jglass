# Project context and handoff

Last verified: 2026-08-17.

This document captures the relevant state from the conversation that created JGlass so a future Codex task can continue without access to that conversation.

## Ownership and scope

This is Joshua's personal project under the private GitHub repository `jflashman/jglass`. It is not a Circadence, Brink, SIW, M76, Microsoft, or employer project. Do not cross-contaminate source, prompts, credentials, characters, infrastructure, or documentation from those environments.

## Physical device

- Google Glass Explorer Edition XE-C, described by the owner as Explorer v3.
- Originally ran XE18.3.
- Updated successfully to the official final XE24 image.
- Bootloader was unlocked successfully with `fastboot oem unlock` on Kali Linux.
- The device booted after flashing and appeared to be running the updated software.
- Official Glass/GDK baseline is Android 4.4.2 / API 19.
- The display is 640x360 and the hardware is thermally, computationally, and battery constrained.

### Recovery information

Official XE24 factory archive:

- URL: `https://storage.googleapis.com/support-kms-prod/bTh25b2gcZx5f7apQdJU3lULYTTBoZDHqdsr`
- Archive SHA-1: `46430bc827267796566f0a73d9b503059759561c`
- Official system/kernel page: `https://developers.google.com/glass/tools-downloads/system`

The successful flash sequence was:

```text
fastboot flash:raw boot boot.img
fastboot flash system system.img
fastboot flash recovery recovery.img
fastboot erase cache
fastboot erase userdata
```

This is recovery context, not permission to flash again. Never perform destructive device operations without explicit approval in the active task.

### Host behavior discovered during recovery

- Windows ADB worked in normal Android and recovery sideload mode.
- Windows Fastboot repeatedly failed to enumerate the Glass bootloader despite USB driver work.
- Fastboot and OEM unlock succeeded on a 2017 MacBook Air running Kali Linux.
- Prefer that Kali machine for future bootloader operations.
- Do not interpret a black Glass screen alone as proof of Fastboot; verify with `fastboot devices` and `fastboot getvar product`. Expected product: `glass_1`.

## Product vision

Joshua wants a fun, hackable daily-use device with three connected capabilities.

### Voice-and-vision assistant

- Voice activated, with push-to-talk or native Glass activation first and an always-on wake word later if battery permits.
- Conversational intelligence through current OpenAI APIs.
- Intentional visual context from the Glass camera.
- Spoken responses through ElevenLabs using an original licensed voice with a polished, understated science-fiction-assistant character. Do not clone or impersonate a recognizable actor without rights and consent.
- Concise visual cards appropriate for Glass rather than chat transcripts squeezed onto the display.

### iPhone notifications

- Show selected iPhone notifications on Glass.
- Allow dismissal and public notification actions where Apple permits them.
- Explore voice responses for specifically supported services.

Important limitation: an ordinary iOS application cannot globally read every other application's notifications. Apple Notification Center Service (ANCS) is the plausible accessory path for receiving notifications on Glass over BLE. Public ANCS exposes notification data and positive/negative actions, but not universal arbitrary text replies. Do not promise Apple Watch parity.

Primary specification: `https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html`

### Work through a computer

- Ask Codex on Joshua's computer to inspect or work in an explicitly allowed repository.
- Receive spoken and visual progress/results on Glass while away from the desk.
- Keep state-changing work behind a proposal and approval step.
- Never expose raw unauthenticated SSH, VNC, shell, or agent endpoints to the public internet.

## Existing ecosystem findings

Useful projects and ideas discovered during initial research are tracked in `docs/OPEN_SOURCE_POLICY.md`. The most directly relevant concepts were:

- a Glass-native voice client with a computer-side Whisper/LLM proxy;
- Android notification forwarding to a Glass receiver;
- Glass camera MJPEG streaming and snapshots;
- SSH, VNC, and a dedicated 640x360 desktop stream;
- native Glass voice triggers and GDK camera capture;
- custom AOSP 5.1.1 launchers and utilities.

Several of the most attractive repositories have no declared license. Their behavior can inform requirements, but their code must not be copied without permission.

## Deliberate initial choices

- Remain on stock XE24 for the first prototype.
- Keep API keys off Glass.
- Use a bridge service on a modern machine.
- Start on a trusted local network.
- Start with push-to-talk and one-shot camera capture.
- Add the Codex computer worker only after the ordinary assistant loop works.
- Treat iPhone notification support as an independent experiment.
- Revisit rooted AOSP 5.1.1 only if measured XE24 limitations block the project.

## Questions still open

- Whether the first bridge should be TypeScript-only or split TypeScript orchestration from a Python speech worker.
- Which audio codec gives acceptable latency and API 19 compatibility.
- Whether XE24's native speech recognizer remains dependable without retired Google services.
- Whether the Glass Android 4.4 BLE stack can maintain a reliable authorized ANCS session with the owner's current iPhone.
- Which relay design can support use away from home without depending on modern TLS libraries on Glass.
- The eventual license for JGlass's original code.
