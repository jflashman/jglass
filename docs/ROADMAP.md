# Roadmap

The roadmap is ordered to produce a usable vertical slice before adding difficult integrations.

## Milestone 0: Baseline and reproducibility

- [ ] Confirm ADB connectivity to stock XE24 from the chosen development host.
- [ ] Record `adb shell getprop` fields needed for builds without committing the device serial.
- [ ] Verify microphone recording, speaker playback, camera capture, Wi-Fi, Bluetooth Classic, and BLE behavior with minimal test apps.
- [ ] Select and document the Android build toolchain that can reliably target API 19.
- [ ] Decide the bridge language/runtime based on a small audio round-trip spike.
- [ ] Create repeatable build, lint, and test commands.

Acceptance: a new Codex task can build and install a harmless diagnostic APK using documented commands.

## Milestone 1: Local voice loop

- [ ] Glass push-to-talk UI with recording indication.
- [ ] Authenticated local bridge connection.
- [ ] Speech-to-text.
- [ ] OpenAI text response.
- [ ] Compact streaming HUD output.
- [ ] ElevenLabs response playback with cancellation.
- [ ] Redacted structured logs and latency measurements.

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

- Wake-word detection after battery profiling.
- Navigation and location cards.
- Dedicated desktop viewport or read-only VNC.
- Offline speech fallback.
- Rooted XE24 or AOSP 5.1.1 only when a documented requirement warrants the cost.
