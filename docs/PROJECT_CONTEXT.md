# Project context and handoff

Last verified: 2026-08-18.

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

On 2026-08-18, a read-only inventory confirmed that stock XE24 stores its boot
branding in `/system/media/bootanimation.zip` as a single 640×360 `GLASS` PNG.
The production build is not rooted and mounts `/system` read-only. A deterministic
JGlass animation and a patched copy of the official sparse system image were
built and verified by expand/check/extract/byte-compare on the macOS host. The
official archive SHA-1 matched the published value. On 2026-08-18, the patched
system image was physically flashed with Fastboot 37.0.1 using an explicit 64
MiB sparse limit. All ten send/write pairs reported `OKAY`, and the command
exited successfully after 103.8 seconds. An initial unbounded attempt had
stalled before reporting any send/write stage and left the stock system intact
after a physical restart. This macOS/Fastboot combination also stalls waiting
for the final reboot acknowledgement. After a physical power cycle, the unit
booted successfully. The owner observed the original `GLASS` phase, including
the illuminated A, for a few seconds before the new animated `JGLASS` system
phase replaced it and accepted that result for this milestone. The initial
phase's storage location has not been verified and must not be described as a
system-partition asset without further evidence. The established Kali host
remains the fallback for bootloader recovery.

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
service present, as expected before an iPhone companion existed. Camera capture,
Wi-Fi application transport, and recording/playback cancellation remain
unverified.

On the macOS development host, Xcode 26.6 and the iOS 26.5 Simulator runtime
successfully built, launched, and tested a provider-free SwiftUI companion
diagnostic. It publishes the draft BLE peripheral service on supported hardware
and includes an HFP route probe. Foreground BLE advertising and positive service
discovery were subsequently verified on the physical iPhone/Glass pair. The
companion-owned HFP experiment was also completed and ruled out that profile for
application audio. A foreground GATT connection then completed characteristic
discovery, downlink subscription, fragmented `gateway.hello`/ack and
`gateway.ping`/pong exchanges, and a clean disconnect. The pong reported the
iPhone's internet connection reachable.

A v0.4 Glass reliability diagnostic is now implemented for those open questions.
An explicit ADB launch mode can either retain one connection for five delayed
pings or run five bounded rediscovery/reconnection cycles after an initial
exchange. The companion displays ephemeral subscription, message, restoration,
and relative-time counters. All ten iOS tests pass in the iPhone 17 Pro / iOS
26.5 Simulator, and the pure-Java reliability coordinator tests pass under JDK
21. The primary macOS host's existing Homebrew SDK contains Platform 35, Build
Tools 35.0.0, and platform-tools; the checked-in helpers now discover that SDK
and Homebrew OpenJDK 21. Android lint and the v0.4 APK build pass there. Version
0.4.0 installed and launched on the physical XE24 unit, remained the resumed
activity, and produced no fatal exception in the ADB smoke check. Joshua then
confirmed that the complete v0.4 card and two-line probe status fit the physical
display and that recording/playback still worked.

The foreground v0.2 companion reported one subscription, two messages, and no
restoration for a successful one-shot exchange. With the same connection held
open, the initial exchange plus five 15-second delayed pings all completed while
the iPhone remained locked (`connected-wake 6/6`); the companion reported a clean
unsubscribe afterward. Reconnect behaved differently: after one foreground
success, all five rediscovery attempts under lock ended `not_found`. Starting a
new run while the companion was ordinarily backgrounded and unlocked ended
`reconnect 0/6 last=not_found`, with no new subscription, message, or restoration
events. This verifies background wake on an established link and demonstrates a
background rediscovery failure on this physical pair. Bluetooth interruption,
force-quit, and manual-relaunch recovery were not completed.

The complete result table and exact resume checklist are recorded in
[`testing/2026-08-18-ble-reliability.md`](testing/2026-08-18-ble-reliability.md).

The first positive discovery attempt exposed an XE24 compatibility issue:
Android 4.4's `startLeScan(UUID[], callback)` service filter did not deliver the
valid custom 128-bit iOS advertisement, even though the Glass Bluetooth service
saw nearby advertisements and a privacy-safe macOS Core Bluetooth probe matched
the JGlass UUID. A corrected diagnostic uses a bounded unfiltered scan and
software-matches only the public service UUID from advertisement fields. With
that build installed, Glass reported `Gateway BLE: service found`. The app does
not retain device objects, addresses, names, signal strength, or advertisement
payloads.

On 2026-08-18, the repository's critical/high dependency alerts were traced to
host-side Android Gradle Plugin and Unified Test Platform transitive libraries;
the Glass APK itself still has no third-party runtime dependencies. Narrow
security floors select patched Netty, Bouncy Castle, jose4j, JDOM, and protobuf
versions without changing AGP, Gradle, D8, AAPT2, SDK levels, or application
source. A clean rebuild was byte-for-byte identical to the v0.3 APK already
exercised on the physical XE24/iPhone pair, and that rebuilt artifact installed,
launched, and rendered successfully on the physical XE24 unit.

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

- The Apple Silicon macOS machine is now the primary application-development
  host for JGlass. Its Homebrew JDK 21 and Android command-line SDK can build,
  lint, install, and launch the API 19 diagnostic.
- Kali remains the known-reliable host for complete firmware and recovery
  operations. The macOS host completed the branding-only system flash when
  Fastboot 37.0.1 was given `-S 64M`, but its unbounded flash negotiation and
  final reboot acknowledgement both stalled. Keep Kali as the fallback rather
  than generalizing this one successful, explicitly chunked system write.

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
- Use compressed BLE audio for assistant speech. Physical testing found no A2DP
  media route, and a companion-owned HFP session either received no input
  buffers or fell back to the phone when output rendering became active.
- Start with push-to-talk and one-shot camera capture.
- Add the Codex computer worker only after the ordinary assistant loop works.
- Treat iPhone notification support as an independent experiment.
- Revisit rooted AOSP 5.1.1 only if measured XE24 limitations block the project.
- Keep the XE24 firmware and signed system applications, but replace the stock
  boot animation through a reversible patched official system image. Do not root
  or live-remount the device for branding.
- Use a minimal Node/TypeScript trusted-LAN startup bridge with Open-Meteo and
  ElevenLabs before broad assistant orchestration.

## Questions still open

- Whether later bridge workloads justify splitting the implemented TypeScript
  service from a separate speech worker.
- Which audio codec gives acceptable latency and API 19 compatibility.
- Whether XE24's native speech recognizer remains dependable without retired Google services.
- Whether the Glass Android 4.4 BLE stack can maintain a reliable authorized ANCS session with the owner's current iPhone.
- Which relay design can support use away from home without depending on modern TLS libraries on Glass.
- Whether a connected iPhone BLE peripheral service remains usable and can be
  reconnected after ordinary iOS backgrounding.
- The eventual license for JGlass's original code.
