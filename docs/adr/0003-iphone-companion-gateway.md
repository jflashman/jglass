# ADR 0003: Use an iPhone companion as the first mobile gateway

- Status: Accepted
- Date: 2026-08-17
- Amended: 2026-08-17 after physical BLE, GATT, and HFP testing

## Context

JGlass must work away from a trusted home Wi-Fi network. The owner does not want
Glass to join an iPhone Personal Hotspot or receive general-purpose tethered
internet access. The desired experience resembles Apple Watch: Glass remains a
thin accessory and the nearby phone relays only application traffic.

Apple's Watch Connectivity transport is available only between an iOS app and
its paired watchOS app. A third-party accessory cannot join that private pairing
and Continuity stack. Public Core Bluetooth APIs can support a custom BLE data
link, subject to iOS background-execution and advertising constraints.

The connected XE24 device declares Bluetooth Classic and BLE support. Its
installed system components include GATT, A2DP, Headset/HFP, PAN, MAP client,
and a privileged Glass Bluetooth companion service. Component presence does not
prove which audio roles work with an iPhone; physical pairing tests are required.

## Decision

- Build an iPhone companion as the first mobile gateway.
- Do not use Personal Hotspot, Bluetooth PAN, or general IP tethering as the
  product architecture.
- Use a project-specific BLE GATT service for authenticated JGlass control,
  compressed microphone data, response text, progress, and approvals.
- Glass will be the BLE central because Android API 19 supports that role. The
  iPhone companion will publish the JGlass GATT service as a BLE peripheral.
- Use bounded compressed BLE audio chunks for application speech in both
  directions. Ordinary A2DP media is unavailable on the tested pair, and the
  companion-owned HFP spike did not provide a stable full-duplex application
  channel. HFP remains an ordinary phone-call profile only.
- The iPhone will make authenticated outbound requests to the bridge over its
  normal Wi-Fi or cellular connection. Glass will never receive bridge or
  provider credentials.
- Initial BLE enrollment will require the companion app in the foreground.
  Background reconnection and wake behavior must be measured on the owner's
  actual iPhone before being treated as reliable.
- Work that requires compiling, signing, or running the iOS companion will wait
  until development moves to macOS with Xcode.

## Physical audio findings

On 2026-08-17, a stock XE24 Glass was paired with the owner's iPhone 16 Pro.
The JGlass diagnostic observed one bonded device. Ordinary iPhone music stayed
on the phone speaker, while Glass continued to report both its headset and
media profiles idle; iOS did not offer Glass as an ordinary media output.
During a voice call, however, iOS did offer Glass in the call-audio selector.
Selecting it routed call audio through the Glass speaker. Microphone input was
not independently isolated, although the call behaved normally and two-way HFP
is an intended Glass function. This verifies pairing and call-audio output, but
not the microphone independently. No device address or pairing code is retained
in the repository.

The signed iPhone companion then tested a `.playAndRecord` / `.voiceChat`
session on the same physical pair. In the first variant, iOS reported both input
and output as `BluetoothHFP` with an 8 kHz, mono, Float32 planar input format,
but `AVAudioEngine` delivered zero input buffers. In a second variant, a source
node actively rendered silence and voice-processing I/O was disabled. The
engine delivered one silent transition buffer, then iOS changed the routes to
the phone's built-in microphone and receiver. Glass briefly displayed its call
UI during each attempt and then returned home. This is sufficient evidence not
to use HFP as the JGlass application audio transport on stock XE24.

## Physical GATT findings

On the same physical pair, the stock-XE24 Glass client discovered and connected
to the foreground companion's custom peripheral service. It discovered the
control characteristics, enabled downlink indications, fragmented and wrote a
bounded `gateway.hello`, validated the matching acknowledgement, fragmented and
wrote `gateway.ping`, and reassembled and validated the pong. The companion
reported internet reachability, after which Glass closed the one-shot
connection. This verifies the basic foreground GATT direction and 20-byte
framing contract, but not enrollment, background wake, reconnection, cellular
relay under lock, or audio throughput.

## Consequences

Glass can operate away from home without becoming a general network client or
exposing a local bridge. The phone can absorb modern TLS, cellular networking,
credential storage, and provider protocol changes.

The design depends on behavior iOS does not guarantee indefinitely. A Core
Bluetooth peripheral app can be woken for connected read, write, and subscription
events, but it cannot run forever. If the user force-quits it, state restoration
does not relaunch it. In the background, the advertised local name is omitted
and service UUIDs move to an overflow area discoverable only by iOS scanners.
Consequently, reconnect after a dropped background connection is a primary
feasibility risk.

Both speech directions now remain on the GATT data path. Bounded, compressed,
interruptible audio over BLE must be measured for throughput, latency, battery,
and stability before a codec or chunk size is selected.

The diagnostic BLE protocol is not authorized to carry sensitive audio or
actions until an enrollment and application-layer authentication design is
accepted. Bluetooth pairing alone is not the complete trust boundary.

## Revisit when

- Glass cannot reconnect to the companion after ordinary iOS backgrounding;
- neither A2DP nor HFP can route iPhone application audio through Glass;
- measured BLE throughput cannot carry short compressed microphone requests;
- background cellular round trips are too slow or unreliable for push-to-talk;
- a future public Apple accessory mechanism provides a materially stronger path.
