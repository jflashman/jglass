# JGlass client

The v0.7 implementation is the JGlass client and first replacement shell for the
physical XE24 unit. Its provider-free diagnostics remain available behind an
opt-in JGlass Home activity and the automatic once-daily weather and speech
experience.

Baseline constraints:

- stock XE24;
- Android API 19 / GDK;
- 640x360 display;
- no provider API keys;
- push-to-talk before wake word;
- one-shot camera capture before continuous vision.

When JGlass is selected as Android's Home app, Android launches its clock and
assistant surface directly and the daily greeting begins there; the stock Glass
timeline does not need to settle first. Tap opens the assistant/diagnostic
activity. Swipe down/Back opens system Settings; Menu remains a secondary
recovery input. The APK alone does not delete or disable the signed stock
launcher. The separately verified Home-shell system image removes only the stock
`GlassHome.apk` after explicit owner approval.

When JGlass is not the selected Home, a short-lived boot receiver preserves the
v0.6 compatibility behavior: it schedules JGlass three seconds after
`BOOT_COMPLETED` only when the local daily greeting is due. The Activity runs the
concentric-ring loader, waits
up to 30 seconds for networking, makes no more than three authenticated requests
to the trusted-LAN bridge, displays the weather summary and Open-Meteo
attribution, and plays the returned MP3. It then exposes the existing diagnostic
features:

- renders a 640x360-friendly status card;
- uses tap/center-key input to start and stop a five-second maximum recording;
- plays captured 16 kHz mono PCM back through the device;
- keeps captured PCM only in a bounded in-memory buffer and clears it after use;
- optionally probes an explicit bridge health URL without logging its body.
- reports Bluetooth/BLE readiness, paired-device count, and headset/media
  connection state without displaying device names or addresses.
- performs a bounded 10-second BLE scan and software-matches only the public
  JGlass gateway service UUID. Software matching avoids a verified XE24
  platform-filter incompatibility with iOS 128-bit service advertisements.
- transiently retains only the matched peripheral object long enough to connect,
  discover the two control characteristics, subscribe to the downlink, and
  exchange bounded `gateway.hello` and `gateway.ping` messages. The default mode
  then closes the connection without displaying or logging device identifiers.
- accepts an explicit `gateway_probe_mode` launch extra for bounded reliability
  tests. `connected-wake` retains one subscribed connection for five additional
  pings at 15-second intervals; `reconnect` performs one initial exchange plus
  five rediscovery/reconnection cycles separated by 10 seconds.
- displays only probe mode, successful/attempted exchange counts, and a stable
  last-failure category. Probe state is in memory and is discarded when the app
  process stops.

Camera and GDK gesture semantics remain separate physical-device baseline tasks.
The foreground GATT exchange is implemented and physically verified; background
wake and reconnect behavior still require physical verification.

Morning is 05:00–11:59, afternoon 12:00–17:59, and evening 18:00–04:59 in the
Glass local timezone. The date is recorded only when dynamic or bundled fallback
playback begins. A request cancelled before playback remains eligible on the
next launch. Dynamic MP3s are deleted after playback. Six fixed licensed MP3s
are bundled for the three periods and two causes. A confirmed disabled Bluetooth
adapter uses the Bluetooth-specific line; all other local bridge/provider
failures use the local-assistant-unavailable line.

## Private startup provisioning

The debug APK accepts bridge configuration only through explicit debug launch
extras, immediately removes those extras from the retained Activity intent, and
persists the values in app-private preferences. Neither value is built into the
APK. Use a trusted USB/ADB session and avoid saving the token in shell history:

```sh
read -s JGLASS_DEVICE_TOKEN
adb shell am start -n com.jglass.diagnostic/.MainActivity \
  --es startup_bridge_url http://BRIDGE_LAN_IP:8787 \
  --es startup_device_token "$JGLASS_DEVICE_TOKEN"
unset JGLASS_DEVICE_TOKEN
```

Force a same-day replay only in a debug build:

```sh
adb shell am start -n com.jglass.diagnostic/.MainActivity \
  --ez force_startup_greeting true
```

Set the persisted debug boot policy to replay on every boot:

```sh
adb shell am start -n com.jglass.diagnostic/.MainActivity \
  --es startup_replay_mode always
```

Restore production-like daily behavior with `--es startup_replay_mode daily`.
Release builds ignore this extra and always enforce the daily rule. Android does
not deliver boot broadcasts to a newly installed force-stopped package until it
has been launched once, so provisioning also activates the installed debug APK.

Release builds ignore provisioning, replay-mode, and force-greeting extras. A
production enrollment and token-rotation UI remains future work.

Force the Home-owned greeting in a debug build without changing the daily rule:

```sh
adb shell am start -n com.jglass.diagnostic/.JGlassHomeActivity \
  --ez force_startup_greeting true
```

Installing v0.7 only registers JGlass as a Home candidate. Selecting it as the
default is an intentional device-side action. Do not clear stock launcher data,
disable its package, or delete signed XE24 applications to make the selection.
The original XE24 image retained an `mAlways=true` preferred activity for the
stock timeline, so installation alone did not make JGlass permanent. The
physically flashed Home-shell system image embeds this exact APK and removes only
the stock Home APK, making JGlass the sole Home candidate while retaining signed
Settings and System UI.

## XE24 system branding

The reproducible boot-animation generator and official-system-image patch flow
are documented in
[`../../firmware/xe24/branding/README.md`](../../firmware/xe24/branding/README.md).
It changes only the stock system boot animation. The patched system partition
was physically flashed and the animated JGlass system phase was visually
verified on 2026-08-18. The earlier stock `GLASS` boot phase remains visible for
a few seconds and is outside this system-partition-only change.

Unknown `gateway_probe_mode` values safely fall back to the existing `oneshot`
behavior. A connected-wake failure is terminal because opening a new connection
would no longer measure wake behavior on the maintained GATT link. Reconnect
failures consume one bounded cycle and continue until all six cycles finish.

## Toolchain

- Android Gradle Plugin 8.13.2;
- Gradle 8.13 through the checked-in wrapper;
- Homebrew OpenJDK 21 on the primary macOS host and JDK 21 on the Kali host;
  JDK 17 through 23 is supported by the selected Gradle version;
- `compileSdk 35`, `minSdk 19`, and `targetSdk 19`.

The root build pins patched security floors for vulnerable AGP transitive
libraries. They are host-side build/test tooling only; the Glass application has
no third-party runtime dependencies. On 2026-08-18, a clean build using those
floors produced an APK byte-for-byte identical to the previously verified API 19
artifact.

The host needs Android SDK Platform 35 and SDK Build Tools 35.0.0. Set
`ANDROID_HOME` or create an uncommitted `local.properties` containing
`sdk.dir=/absolute/path/to/android-sdk`. On Homebrew macOS, the Gradle helper
automatically uses `/opt/homebrew/share/android-commandlinetools` (Apple Silicon)
or `/usr/local/share/android-commandlinetools` (Intel) when `ANDROID_HOME` is
unset.

## Build and test

From the repository root:

```sh
apps/glass/scripts/test-java.sh
apps/glass/scripts/gradle.sh :apps:glass:lintDebug :apps:glass:assembleDebug
```

The helpers choose Homebrew OpenJDK 21 on Apple Silicon/Intel macOS or JDK 21/17
in the documented Linux locations. Set `JGLASS_JAVA_HOME` when a compatible JDK
is installed elsewhere. The standard `./gradlew` entry point remains available
when `JAVA_HOME` is already suitable.

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

Reliability probes are selected only through an explicit ADB launch extra:

```sh
adb shell am start -n com.jglass.diagnostic/.MainActivity \
  --es gateway_probe_mode connected-wake

adb shell am start -n com.jglass.diagnostic/.MainActivity \
  --es gateway_probe_mode reconnect
```

Launching without the extra preserves the one-shot foreground diagnostic. A
new launch updates an already-running activity, and leaving the Glass activity
cancels all pending pings, scans, and retries.

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

For the startup slice, additionally:

1. Match bridge `JGLASS_TIME_ZONE` to the Glass timezone and start the bridge on
   an explicitly trusted-LAN address.
2. Provision the debug APK over authorized ADB, set replay mode to `always`, and
   reboot normally. Do not flash or erase any partition.
3. Confirm JGlass opens after the system animation, the rings fit 640×360 and
   animate smoothly, then confirm weather text
   and `Weather data by Open-Meteo.com` are legible while speech plays on Glass.
4. With the bridge stopped and Bluetooth enabled, force replay and confirm the
   period-correct local-assistant-unavailable clip.
5. Disable Bluetooth, force replay again, and confirm the period-correct
   Bluetooth-disabled clip; then restore Bluetooth.
6. Set replay mode to `daily`, force one greeting, and reboot again; confirm the
   same-day boot remains silent and does not open JGlass.
7. Leave the activity during animation, download, and playback in separate runs;
   confirm cancellation and dynamic-audio deletion.
8. Return to ready and repeat audio loopback plus the bounded BLE probe to guard
   the existing physical baseline.

The automatic boot launch and bundled local-assistant-unavailable greeting were
verified on the physical XE24 on 2026-08-18. Android displayed JGlass after the
three-second scheduled wakeup and Joshua heard the correct period greeting. The
provider-backed greeting, Bluetooth-disabled fallback, same-day skip, and
cancellation matrix remain unverified.

The v0.7 Home activity was also installed and explicitly launched on the same
unit. Center-key input opened `MainActivity`, Back returned to JGlass Home, and
swipe down/Back opened the signed XE24 Settings activity through the verified
Glass action. The owner accepted the clock layout. The system-Home image was then
flashed and cold-booted successfully; ADB verified JGlass as the resumed Home.

## BLE reliability test matrix

Use only the existing non-sensitive hello/ping messages. Start each scenario
with the iPhone gateway enabled and record the final Glass success/attempt count,
last stable failure, iPhone subscription/message/restoration counts, and rough
elapsed time. Do not record Bluetooth identifiers or packet contents.

1. Run `oneshot` with the companion foregrounded to establish the baseline.
2. Run `connected-wake`; after the initial success, press the iPhone side button
   to lock it. Expect five additional pings on the maintained connection.
3. Repeat `connected-wake`, but return to the iPhone Home Screen without locking.
4. Run `reconnect`; after the initial success, lock the iPhone before the next
   10-second cycle. This separately tests background discovery and connection.
5. Repeat `reconnect` with the companion ordinarily backgrounded but unlocked.
6. During a `reconnect` run, turn iPhone Bluetooth off for at least one cycle,
   then restore it before the bounded run ends. Record failure and recovery as
   distinct outcomes.
7. During a `reconnect` run, force-quit the companion after the initial success.
   Record failed cycles, manually relaunch and enable the gateway, and record
   whether a later bounded cycle recovers. Do not treat force-quit behavior as
   equivalent to ordinary background suspension.

The new reliability state machine passes its host-side self-tests under Homebrew
OpenJDK 21. Android lint passes and the v0.4 APK assembles on the primary macOS
host with Platform 35 and Build Tools 35.0.0. The APK was installed and launched
on the physical XE24 unit, where `MainActivity` remained the resumed activity and
ADB showed no fatal exception. The new reliability modes still require the
remaining interruption, termination, and relaunch portions of the interactive
iPhone/Glass matrix below.

Do not use private speech during diagnostic testing. No recording is written to
storage by the application.

## Verification status

Verified on 2026-08-17:

- dependency-free Java buffer, BLE advertisement, and control-framing self-tests
  pass on the development host;
- Android lint passes with zero errors;
- the debug APK assembles and is API 19 compatible;
- permissions are limited to `INTERNET`, `ACCESS_NETWORK_STATE`,
  `RECEIVE_BOOT_COMPLETED`, `RECORD_AUDIO`, `BLUETOOTH`, and
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

Also verified on 2026-08-18 from the primary macOS host: all four Java self-test
suites pass, Android lint passes, v0.4.0 assembles as an API 19-compatible debug
APK, package version 4 installs over the existing diagnostic, and the activity
launches and remains resumed without a fatal exception. Visual fit and the new
multi-cycle BLE states were then verified manually by Joshua: the complete card
fit, the two-line probe result was readable, and audio recording/playback still
worked.

The foreground one-shot completed `1/1`. Connected-wake completed `6/6` while
the iPhone stayed locked, proving five delayed writes on the maintained link.
Reconnect after locking completed `1/6 last=not_found`: only the initial
foreground cycle succeeded. A run begun with the companion ordinarily
backgrounded and unlocked completed `0/6 last=not_found`. Companion counters
confirmed that the failed cycles never subscribed or delivered a control
message, and no Core Bluetooth restoration callback occurred.

Not yet verified on the physical device: provider-backed startup speech, the
Bluetooth-disabled fallback, cancellation while recording or playing, a real
LAN health probe, camera capture, Wi-Fi transport behavior,
microphone isolation during an ordinary phone call, or audio throughput. A
companion-owned HFP session was tested separately and is not usable as the JGlass
application audio path.

Also not yet verified on the physical device: Bluetooth interruption recovery or
companion termination/manual-relaunch behavior. The attempted interruption test
was explicitly canceled before producing a result.

See [`../../docs/testing/2026-08-18-ble-reliability.md`](../../docs/testing/2026-08-18-ble-reliability.md)
for the completed counter snapshots, pending cases, and exact resume procedure.

If code is adapted from another project, complete the workflow in `../../docs/OPEN_SOURCE_POLICY.md` and update `../../THIRD_PARTY_NOTICES.md` in the same commit.
