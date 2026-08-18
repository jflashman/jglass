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
- performs one bounded 10-second BLE scan and software-matches only the public
  JGlass gateway service UUID. Software matching avoids a verified XE24
  platform-filter incompatibility with iOS 128-bit service advertisements.
- transiently retains only the matched peripheral object long enough to connect,
  discover the two control characteristics, subscribe to the downlink, and
  exchange bounded `gateway.hello` and `gateway.ping` messages. It then closes
  the connection without displaying or logging device identifiers.

Camera and GDK gesture semantics remain separate physical-device baseline tasks.
The foreground GATT exchange is implemented and physically verified; background
wake and reconnect behavior still require physical verification.

## Toolchain

- Android Gradle Plugin 8.13.2;
- Gradle 8.13 through the checked-in wrapper;
- JDK 21 on the current Kali host; JDK 17 or newer is supported;
- `compileSdk 35`, `minSdk 19`, and `targetSdk 19`.

The root build pins patched security floors for vulnerable AGP transitive
libraries. They are host-side build/test tooling only; the Glass application has
no third-party runtime dependencies. On 2026-08-18, a clean build using those
floors produced an APK byte-for-byte identical to the previously verified API 19
artifact.

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
7. In the foreground iPhone companion, start the BLE gateway and leave the app
   visible while Glass scans.
8. Confirm the Glass gateway line advances through connection, service
   discovery, subscription, hello, and ping states, ending at either
   `pong / internet reachable` or `pong / internet offline`.
9. Confirm the iPhone reports a Glass subscription and receipt of the diagnostic
   messages. Relaunch the Glass app to repeat the one-shot exchange.

Do not use private speech during diagnostic testing. No recording is written to
storage by the application.

## Verification status

Verified on 2026-08-17:

- dependency-free Java buffer, BLE advertisement, and control-framing self-tests
  pass on the development host;
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
isolated. The bounded BLE gateway scan times out cleanly when no service exists.
With the physical iPhone companion foregrounded and advertising, the corrected
software-matched scan reports `service found`. XE24's platform-level UUID filter
did not deliver that valid custom 128-bit iOS advertisement, so it is not used.
The v0.3 diagnostic then connected, discovered both control characteristics,
subscribed, exchanged the fragmented hello and ping messages, validated their
responses, displayed `pong / internet reachable`, and closed the connection.

Verified on the physical XE24 unit on 2026-08-18 after applying the host-tool
security floors: the byte-identical APK installed successfully, explicitly
launched `MainActivity`, remained foregrounded, rendered the complete 640x360
ready card, and produced no fatal exception during the smoke test.

Not yet verified on the physical device: cancellation while recording or
playing, a real LAN health probe, camera capture, Wi-Fi transport behavior,
microphone isolation during an ordinary phone call, GATT behavior while the
iPhone is backgrounded or locked, reconnection, or audio throughput. A
companion-owned HFP session was tested separately and is not usable as the JGlass
application audio path.

If code is adapted from another project, complete the workflow in `../../docs/OPEN_SOURCE_POLICY.md` and update `../../THIRD_PARTY_NOTICES.md` in the same commit.
