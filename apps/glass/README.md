# Glass client

The first implementation is a provider-free diagnostic APK for the physical
Google Glass XE24 unit. It proves the voice-critical device path before the
bridge or provider integrations are added.

Baseline constraints:

- stock XE24;
- Android API 19 / GDK;
- 640x360 display;
- no provider API keys;
- push-to-talk before wake word;
- one-shot camera capture before continuous vision.

The current diagnostic app:

- renders a 640x360-friendly status card;
- uses tap/center-key input to start and stop a five-second maximum recording;
- plays captured 16 kHz mono PCM back through the device;
- keeps captured PCM only in a bounded in-memory buffer and clears it after use;
- optionally probes an explicit bridge health URL without logging its body.
- reports Bluetooth/BLE readiness, paired-device count, and headset/media
  connection state without displaying device names or addresses.
- performs one bounded 10-second scan for only the public JGlass gateway service
  UUID, retaining no device object, address, name, signal strength, or
  advertisement payload.

Camera, GDK gesture semantics, positive BLE discovery, and GATT traffic remain
separate physical-device baseline tasks.

## Toolchain

- Android Gradle Plugin 8.13.2;
- Gradle 8.13 through the checked-in wrapper;
- JDK 21 on the current Kali host; JDK 17 or newer is supported;
- `compileSdk 35`, `minSdk 19`, and `targetSdk 19`.

The host needs Android SDK Platform 35 and SDK Build Tools 35.0.0. Set
`ANDROID_HOME` or create an uncommitted `local.properties` containing
`sdk.dir=/absolute/path/to/android-sdk`.

## Build and test

From the repository root:

```sh
apps/glass/scripts/test-java.sh
apps/glass/scripts/gradle.sh :apps:glass:lintDebug :apps:glass:assembleDebug
```

The helper chooses the host's JDK 21 or JDK 17 installation. Set
`JGLASS_JAVA_HOME` when the compatible JDK is installed elsewhere. The standard
`./gradlew` entry point remains available when `JAVA_HOME` is already suitable.

Install only while an authorized Glass device is connected:

```sh
adb install -r apps/glass/build/outputs/apk/debug/glass-debug.apk
adb shell am start -n com.jglass.diagnostic/.MainActivity
```

To include a LAN health probe, pass a trusted-LAN URL explicitly. Do not put
tokens or credentials in the URL:

```sh
adb shell am start -n com.jglass.diagnostic/.MainActivity \
  --es bridge_health_url http://BRIDGE_LAN_IP:8787/health
```

## Physical test procedure

1. Launch the app and confirm the complete card fits the display.
2. Tap once and confirm the state changes to `RECORDING`.
3. Speak a short non-sensitive test phrase, then tap again.
4. Confirm playback is intelligible and the UI returns to `READY`.
5. Start another recording, leave the app, and confirm capture stops.
6. If a health URL was supplied, confirm only reachability is displayed.
7. Confirm the gateway line changes from `scanning` to `service not found` when
   no foreground iPhone companion is publishing the JGlass service.

Do not use private speech during diagnostic testing. No recording is written to
storage by the application.

## Verification status

Verified on 2026-08-17:

- dependency-free Java buffer self-test passes on the Kali host;
- Android lint passes with zero errors;
- the debug APK assembles and is API 19 compatible;
- permissions are limited to `INTERNET`, `RECORD_AUDIO`, `BLUETOOTH`, and
  `BLUETOOTH_ADMIN` for API 19, plus the forward-compatible
  `BLUETOOTH_CONNECT` and non-location `BLUETOOTH_SCAN` declarations used by
  modern lint;
- the APK installs on the physical XE24 unit and `MainActivity` remains running
  in the foreground.

Verified by Joshua while wearing the physical Glass on 2026-08-17:

- the diagnostic card is visible and readable;
- tap input starts and stops capture;
- the card visibly transitions through `READY`, `RECORDING`, and playback;
- a short spoken phrase is captured and played back as expected;
- the card returns to `READY` after playback.

Also verified on the physical device: Bluetooth Classic pairing to an iPhone 16
Pro, bonded-device count, and idle headset/media profile reporting. iOS offered
Glass as a voice-call output but not as an ordinary media output. Selecting the
call route produced audio through Glass; microphone routing was not independently
isolated. The filtered BLE gateway scan started, timed out after 10 seconds to
the expected `service not found` state, preserved the phone bond, fit the card,
and produced no runtime error. A positive service match cannot be tested until
the iPhone companion exists.

Not yet verified on the physical device: cancellation while recording or
playing, a real LAN health probe, camera capture, Wi-Fi transport behavior,
Bluetooth HFP microphone isolation, a positive application BLE discovery, or
GATT traffic.

If code is adapted from another project, complete the workflow in `../../docs/OPEN_SOURCE_POLICY.md` and update `../../THIRD_PARTY_NOTICES.md` in the same commit.
