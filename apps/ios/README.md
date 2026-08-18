# JGlass iPhone companion

This directory contains the first provider-free iPhone gateway diagnostic. It
is a native SwiftUI application with no third-party dependencies, API keys,
backend endpoint, enrollment identity, notification access, or persistent user
data.

The diagnostic currently provides two explicit, user-controlled probes:

- a Core Bluetooth peripheral that advertises the draft JGlass GATT service and
  exchanges only bounded `gateway.hello` and `gateway.ping` messages;
- an `AVAudioSession` HFP route probe that discards microphone buffers
  immediately and displays only route, format, buffer-count, and transient-level
  diagnostics. Physical testing established that this is not a viable
  application audio transport on stock XE24.

For reliability testing, the BLE card also shows ephemeral process-lifetime
counts for downlink subscriptions, completed control messages, Core Bluetooth
state-restoration callbacks, and the relative time of the latest event. These
values are not written to storage and reset when the process terminates.

The companion is not a universal notification reader. ANCS consumption belongs
on Glass. Service-specific replies remain separate integrations using public,
permitted APIs.

## Requirements and build

The current checked baseline is Xcode 26.6 with the iOS 26.5 Simulator runtime.
The deployment target is iOS 17. Open
`JGlassCompanion.xcodeproj`, select the shared `JGlassCompanion` scheme, and
choose a simulator or a signed physical iPhone target.

Command-line build:

```sh
xcodebuild \
  -project apps/ios/JGlassCompanion.xcodeproj \
  -scheme JGlassCompanion \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Command-line tests:

```sh
xcodebuild \
  -project apps/ios/JGlassCompanion.xcodeproj \
  -scheme JGlassCompanion \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5' \
  test
```

The simulator validates the UI, message processor, and 20-byte fragmentation,
but does not implement BLE peripheral hardware or prove HFP routing.

## Physical iPhone procedure

No paid Apple capability is required for this probe. A free or paid development
team may still be needed to sign the app for the owner's iPhone.

1. In Xcode, select a development team and the paired iPhone, then build and run
   the app. Accept Bluetooth and microphone permission only when each probe is
   intentionally started.
2. Tap **Start BLE Gateway**. From the existing Glass BLE diagnostic, start the
   bounded JGlass connection probe. Confirm it progresses through discovery,
   subscription, hello, and ping and ends with a pong result.
3. With Glass paired as a call-audio device, tap **Start HFP Probe**. The probe
   reproduces the measured failure mode for diagnostics; do not expect it to
   provide application audio. Tap **Stop HFP Probe** when finished. No audio is
   retained.
4. Record service discovery behavior. Do not infer background reliability from
   this foreground test.

For the bounded background test matrix and exact Glass launch commands, follow
`../glass/README.md`. A force-quit/manual-relaunch result must be recorded
separately from normal iOS background suspension. The displayed restoration
count means Core Bluetooth invoked the restoration callback; it does not by
itself prove that a ping completed.

Background wake, restoration, reconnect behavior, screen-lock latency,
cellular relay, authentication, and real audio transport remain separate
physical-device milestones.

Foreground BLE advertising, service discovery, GATT connection, characteristic
discovery, downlink subscription, and the fragmented hello/ping exchange were
verified on the physical iPhone 16 Pro and stock-XE24 Glass pair on 2026-08-17.
The Glass display ended at `pong / internet reachable`; this result does not yet
establish background or reconnect reliability.

The v0.2 companion reliability counters compile against the iOS 26.5 simulator
SDK, and all ten companion unit tests pass in the iPhone 17 Pro / iOS 26.5
Simulator. On the physical iPhone, their values matched each verified exchange:
one subscription and two messages for one-shot; one additional subscription and
seven messages for connected-wake; and no new events during six failed
background rediscovery cycles. No restoration callback occurred.

The companion-owned HFP experiment was also completed on the physical pair.
iOS initially reported `BluetoothHFP` input/output and an 8 kHz mono Float32
input format but delivered zero input buffers. Keeping output rendering active
produced one silent transition buffer and changed the route to the iPhone's
built-in microphone and receiver. HFP is therefore retained only as documented
ordinary call behavior; compressed BLE is the application audio path to test.
