# XE24 boot greeting verification

Date: 2026-08-18
Device baseline: physical Google Glass XE-C on official XE24 / Android 4.4.4
Application: JGlass debug v0.6.0 (`versionCode` 6)

## Procedure

1. Generate the six licensed ElevenLabs fallback MP3s and verify their recorded
   SHA-256 hashes.
2. Run Java self-tests, bridge tests under Node 22, Android lint, and debug APK
   assembly with the fallback-asset guard enabled.
3. Install the APK over authorized ADB and set debug replay mode to `always`.
4. Reboot normally with `adb reboot`; do not flash, erase, root, or modify a
   partition.
5. Observe Android boot/activity timing without recording identifiers or audio.
6. Have the owner confirm whether the greeting is audible while wearing Glass.

## Results

- An immediate `BOOT_COMPLETED` Activity launch was created but did not produce
  audible playback before the Glass home lifecycle intervened.
- The corrected receiver scheduled an `ELAPSED_REALTIME_WAKEUP` Activity launch
  three seconds after `BOOT_COMPLETED`; boot-only window flags requested the
  screen.
- Stock Glass home was created at 15:35:30, JGlass received the boot broadcast
  at 15:35:41, the alarm launched JGlass at 15:35:44, and Android reported the
  Activity displayed at 15:35:45.
- No bridge configuration was present, Bluetooth remained enabled, and Joshua
  heard the bundled period-specific “local assistant is unavailable” greeting.
- The stock home was visible during the approximately 14-second interval before
  JGlass displayed. Removing that transition requires a separately approved,
  reversible home/launcher design rather than further boot-receiver tuning.

## Remaining cases

- Provider-backed weather and licensed dynamic speech.
- Bluetooth-disabled bundled wording.
- Production-like same-day skip after a second reboot.
- Cancellation during boot wait, download, and playback.
- Reversible JGlass home replacement while preserving access to the stock Glass
  timeline, settings, recovery, and ordinary application launching.
