# ADR 0003: Use an iPhone companion as the first mobile gateway

- Status: Accepted
- Date: 2026-08-17

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
- Prefer a companion-owned HFP session for assistant speech if the Xcode spike
  confirms reliable full-duplex application audio. Ordinary A2DP media is not
  available on the tested pair. BLE audio chunks remain the fallback.
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
not yet a companion-owned audio session. No device address or pairing code is
retained in the repository.

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

An HFP application route would remove speech audio from the GATT data path, but
the verified lack of A2DP means arbitrary iPhone media cannot be the downlink.
The Xcode feasibility spike must test a companion-owned full-duplex HFP session.
If it is unusable, the fallback is bounded, compressed, interruptible audio over
BLE. That fallback must be measured for latency, battery, and stability before
adoption.

The diagnostic BLE protocol is not authorized to carry sensitive audio or
actions until an enrollment and application-layer authentication design is
accepted. Bluetooth pairing alone is not the complete trust boundary.

## Revisit when

- Glass cannot reconnect to the companion after ordinary iOS backgrounding;
- neither A2DP nor HFP can route iPhone application audio through Glass;
- measured BLE throughput cannot carry short compressed microphone requests;
- background cellular round trips are too slow or unreliable for push-to-talk;
- a future public Apple accessory mechanism provides a materially stronger path.
