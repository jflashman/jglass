# Roadmap

The roadmap is ordered to produce a usable vertical slice before adding difficult integrations.

## Milestone 0: Baseline and reproducibility

- [x] Confirm ADB connectivity to stock XE24 from the chosen development host.
- [x] Record `adb shell getprop` fields needed for builds without committing the device serial.
- [x] Verify microphone recording, speaker playback, and tap interaction with a minimal diagnostic app.
- [ ] Verify camera capture and Wi-Fi application transport with minimal diagnostic slices.
  The authenticated startup transport is host-implemented but still requires a
  provider-backed physical XE24 run.
- [ ] Verify Bluetooth Classic and BLE behavior with minimal diagnostic slices.
  Classic pairing, profile-state reporting, foreground application BLE service
  discovery, and a fragmented GATT hello/ping round trip are verified. Maintained
  connections survive lock; background rediscovery failed on the tested pair.
  Interruption and relaunch recovery remain unverified.
- [x] Select and document the Android build toolchain that can reliably target API 19.
- [x] Select Node 22+ / TypeScript with built-in HTTP/fetch for the initial bridge.
- [x] Create repeatable build, lint, and test commands.

Acceptance: a new Codex task can build and install a harmless diagnostic APK using documented commands.

## Milestone 0.5: iPhone gateway feasibility

- [x] Select a non-tethered iPhone companion gateway architecture.
- [x] Define draft GATT roles, UUIDs, diagnostic messages, and safety bounds.
- [x] Pair iPhone and Glass and identify the advertised audio routes: HFP is
  selectable for calls; A2DP media is not offered.
- [x] Test ordinary iPhone media while Glass is worn: it remains on the iPhone
  speaker and Glass reports the media profile idle.
- [x] Confirm HFP call audio reaches the Glass speaker. Microphone routing was
  accepted as expected call behavior but was not independently isolated.
- [x] Test an iOS companion-owned full-duplex HFP audio session. The route-only
  probe received no input buffers; active output caused iOS to fall back to the
  phone microphone and receiver, so HFP is not the application audio path.
- [x] Create the minimal Xcode companion and verify its provider-free target,
  message handling, and BLE framing in the iOS Simulator.
- [x] Perform foreground BLE service discovery on the physical iPhone and Glass
  pair.
- [x] Connect to the companion GATT service and complete a bounded fragmented
  hello/ping exchange.
- [ ] Add explicit enrollment.
- [x] Confirm a connected write wakes the background companion. Five delayed
  pings completed while the paired iPhone remained locked.
- [ ] Measure reconnection after screen lock, suspension, process termination,
  Bluetooth interruption, and app relaunch.
  Locked rediscovery produced one foreground success followed by five
  `not_found` results; starting with the companion ordinarily backgrounded
  produced six `not_found` results. Bluetooth interruption, termination, and
  manual relaunch recovery remain pending.
- [ ] Relay a non-sensitive cellular-backed ping through the locked iPhone.
- [ ] Measure compressed microphone upload throughput before selecting a codec.
- [ ] Add enrollment authentication before sending real audio or actions.

Acceptance: while away from Wi-Fi, invoke a non-sensitive diagnostic request on
Glass, relay it through the nearby locked iPhone's cellular connection, and
receive a response without enabling Personal Hotspot. Standard Bluetooth audio
routing and reconnection limitations are documented from physical tests.

## Milestone 1: Local voice loop

- [ ] Glass push-to-talk UI with recording indication.
- [ ] Authenticated local bridge connection. Startup greeting transport is
  implemented and host-tested; physical Wi-Fi verification remains.
- [ ] Speech-to-text.
- [ ] OpenAI text response.
- [ ] Compact streaming HUD output.
- [ ] ElevenLabs response playback with cancellation.
- [ ] Redacted structured logs and latency measurements.

### Daily startup slice

- [x] Add API-19 Canvas JGlass loader and once-per-day state rules.
- [x] Add an API-19 boot receiver, bounded readiness retries, and debug-only
  every-boot replay control.
- [x] Add authenticated weather/TTS protocol, strict response bounds, and shared
  Glass/bridge fixtures.
- [x] Add Open-Meteo Fahrenheit summaries and visible attribution.
- [x] Add versioned period/cause-specific bundled fallback handling and
  ephemeral dynamic audio handling, including licensed binary clips generated
  under the project owner's paid Creator plan.
- [x] Build and host-verify a reversible JGlass XE24 boot-animation system image.
- [x] Flash and verify the JGlass system boot animation on the physical XE24.
  The earlier stock `GLASS` boot phase remains visible briefly before the new
  JGlass system animation; this two-stage sequence is accepted for now.
- [ ] Verify automatic boot launch, successful speech, both offline fallbacks,
  same-day skip, debug replay, and cancellation on the physical XE24.

### JGlass system shell

- [x] Register an API-19 JGlass Home activity without deleting or disabling the
  signed stock launcher.
- [x] Move the daily greeting into Home and suppress the delayed boot receiver
  when JGlass is the selected default.
- [x] Add a 640×360 clock/status surface with assistant entry and a direct
  system-Settings gesture.
- [x] Make JGlass the sole system Home and verify a successful physical cold
  boot, center-to-assistant, Back-to-Home, and Home-Back-to-signed-Settings.
  Touchpad hold is not a reliable plain-Android long-press gesture on the tested
  shell, so swipe down/Back is the supported Settings gesture.
- [ ] Verify display sleep/wake and crash recovery with JGlass as system Home.
- [ ] Physically verify rollback by flashing the preserved branded or official
  XE24 system image and confirming the stock Home returns.
- [ ] Replace the diagnostic assistant entry with the production push-to-talk
  conversation surface.

### Next slice: stock XE24 custom wake phrase

Use Google's installed XE24 recognizer rather than adding a second wake engine.
The first configured phrase is `JARVIS`; activation is deliberately limited to
the period after tap/head-wake has turned on the display.

- [x] Add authenticated `GET /v1/device-config` with protocol version 1 and a
  bridge-configured `wakePhrase`. Validate one to four English words (32
  characters maximum), and cache the last valid configuration on Glass for
  offline use.
  Implemented in v0.7.1 with shared validation fixtures, bounded requests, and
  cache invalidation on re-enrollment. Host-verified; physical tests pending.
- [ ] Add an isolated API-19 adapter that reflectively invokes the installed
  `VoiceCommandClient` and `VoiceCommand` classes. Do not bundle Google APKs,
  framework JARs, recognizer assets, decompiled output, or patched images.
- [ ] Start recognition only while JGlass Home is foreground and the display is
  awake; stop on screen-off, pause, and destruction. Add bounded recovery for
  service disconnects without making Home depend on voice availability.
- [ ] Register only the configured phrase. Do not use `OkayGlassClient`: the
  inspected XE24 implementation forces a sound effect and stops listening after
  a match, which conflicts with silent activation and the later continuous
  utterance path.
- [ ] On a match, silently open a minimal `JARVIS` / `Activated` assistant card.
  Swipe-down/Back dismisses it immediately; otherwise it returns Home after
  three seconds. This slice does not record, transcribe, contact providers, or
  retain audio.
- [ ] Add host tests for configuration authentication/bounds, phrase
  normalization and cache rollback, recognizer lifecycle/disconnect behavior,
  duplicate callbacks, and assistant-card dismissal.
  Configuration/cache and HTTP cancellation tests are complete; recognizer and
  assistant-card lifecycle tests await those implementations.
- [ ] Physically verify at least 18/20 quiet-room `JARVIS` detections within two
  seconds, 0/20 detections for `OK Glass`, and no false activation during a
  30-minute normal-room observation. Verify cached offline use, phrase changes,
  cold boot, head-wake, Settings access, and crash recovery.
- [ ] Compare screen-off battery, temperature, CPU, and audio-record state with
  the current Home build. Require no persistent screen-off recording or
  material idle-drain regression before proceeding.
- [ ] Record the stock-service dependency and reverse-engineering boundary in
  an ADR and add a dated physical test report. Keep the stock voice service and
  official rollback path intact; do not flash or patch `/system` unless later
  evidence establishes a need and the active task explicitly authorizes it.

After this slice passes, add uninterrupted command capture for utterances such
as `JARVIS, what's the weather?`, ending on trailing silence or tap and
cancelling on swipe-down. The inspected stock service's resampled-audio callback
is the preferred path to investigate so JGlass does not compete for a second
microphone session.

Acceptance: on a trusted LAN, ask a spoken question and hear/see the response on Glass without storing credentials on the device.

## Milestone 2: Intentional vision

- [ ] Add an explicit “look at this” action.
- [ ] Capture and resize one camera frame.
- [ ] Show a preview/consent state before upload where practical.
- [ ] Send the image with the spoken prompt.
- [ ] Delete transient image data after the request by default.

Acceptance: ask a question about a deliberately captured object and receive a grounded response.

## Milestone 3: Codex computer worker

- [ ] Run the Codex SDK in a separate worker process.
- [ ] Configure an allowlist of personal repository roots.
- [ ] Return structured plans and progress events.
- [ ] Add explicit approve/reject interaction on Glass.
- [ ] Keep commits, pushes, messages, dependency installations, and destructive actions separately controlled.
- [ ] Add audit records that contain metadata without leaking repository contents.

Acceptance: request a read-only repository summary, then approve one bounded test change and receive its verified result.

## Milestone 4: iPhone notifications

- [ ] Prove BLE pairing and ANCS discovery from Glass.
- [ ] Decode notification/app attributes.
- [ ] Implement filtering and privacy-safe display.
- [ ] Support only actions advertised by ANCS.
- [ ] Measure reconnection and battery behavior.
- [ ] Explore service-specific voice replies separately.

Acceptance: reliably receive, display, dismiss, and perform supported actions on selected notifications without claiming universal reply support.

## Milestone 5: Away-from-home operation

- [ ] Select an authenticated relay design with outbound bridge/worker connections.
- [ ] Threat-model lost-device and stolen-token scenarios.
- [ ] Add token rotation and device revocation.
- [ ] Verify old-Android TLS compatibility or terminate secure transport in an explicitly trusted companion.
- [ ] Add reconnect, resume, and idempotency behavior.

Acceptance: use the assistant away from the home LAN without exposing raw SSH, VNC, or an unauthenticated agent endpoint.

## Later experiments

- Screen-off or always-on wake-word detection only after battery profiling.
- Navigation and location cards.
- Dedicated desktop viewport or read-only VNC.
- Offline speech fallback.
- Rooted XE24 or AOSP 5.1.1 only when a documented requirement warrants the cost.
