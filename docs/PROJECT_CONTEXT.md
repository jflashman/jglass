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
- The connected XE24 device reports Android 4.4.4 / API 19 (`XRH35`). Earlier
  planning described the baseline as Android 4.4.2; the physical-device result
  is authoritative for this unit.
- The display is 640x360 and the hardware is thermally, computationally, and battery constrained.

### Verified non-sensitive device baseline

Observed over authorized ADB from Kali Linux on 2026-08-17:

- product: Google Glass 1 / `glass-1`;
- build: `XRH35`, user build, release keys;
- runtime: Android 4.4.4 / API 19, ARMv7, OMAP4430, OpenGL ES 2.0;
- display: 640x360 at 240 dpi;
- memory: approximately 1.4 GiB total;
- available `/data` space during the check: approximately 12.4 GiB;
- declared features include microphone, camera, Wi-Fi, Bluetooth Classic, and BLE.
- system clock synchronization is enabled; the timezone is manually fixed to
  `America/New_York` because XE24 did not obtain a timezone from its retired
  companion path. Automatic timezone is disabled to prevent reversion to GMT.

The device serial, network identifiers, accounts, and captured user data were
not recorded.

### Verified diagnostic behavior

On 2026-08-17, the first JGlass diagnostic APK was built on Kali, installed over
ADB, and tested by Joshua while wearing the physical XE24 unit. The 640x360 card
was visible, tap input started and stopped an in-memory 16 kHz mono recording,
the recording state was visible, the spoken test phrase played through Glass as
expected, and the app returned to its ready state. No captured audio was written
to storage.

Subsequent testing paired the unit with an iPhone 16 Pro, confirmed HFP call
audio output and the absence of an A2DP media route, and ran a bounded filtered
scan for the future JGlass BLE service. The scan timed out cleanly with no
service present, as expected before an iPhone companion exists. Camera capture,
Wi-Fi application transport, positive BLE service discovery/GATT traffic, and
recording/playback cancellation remain unverified.

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
- Use an iPhone companion as the first away-from-home application gateway;
  do not use Personal Hotspot or general Bluetooth tethering as the product path.
- Prefer the paired HFP call-audio route for assistant speech if an iOS
  `AVAudioSession` test confirms full-duplex application audio. Physical testing
  found no A2DP media route from the iPhone to stock XE24.
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
- Whether the selectable iPhone-to-XE24 HFP call route carries reliable
  full-duplex speaker and microphone audio for a companion app. A2DP media
  routing was tested and is not available on this stock XE24 pairing.
- Whether a connected iPhone BLE peripheral service remains usable and can be
  reconnected after ordinary iOS backgrounding.
- The eventual license for JGlass's original code.
