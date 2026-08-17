# JGlass iPhone companion

The iPhone companion is the selected first mobile gateway. It provides an
application-level relay rather than Personal Hotspot, Bluetooth PAN, or general
internet tethering.

Planned responsibilities:

- publish the JGlass BLE GATT service while the user enrolls Glass;
- relay authenticated, bounded JGlass messages over the iPhone's normal Wi-Fi
  or cellular connection;
- keep provider and bridge credentials out of the APK;
- expose explicit connection, microphone-transfer, and retention state;
- route assistant speech through a companion-owned HFP session if the Xcode
  spike confirms reliable full-duplex application audio; physical testing found
  no ordinary A2DP media route to XE24;
- restore BLE state where public iOS APIs allow it and clearly surface when the
  user must reopen the app;
- store enrollment identity in Keychain and support revocation.

The companion is not a universal notification reader. ANCS consumption belongs
on Glass. Service-specific replies remain separate integrations using public,
permitted APIs.

## Xcode handoff gate

The following work requires macOS and Xcode and has not been implemented:

- create and sign the iOS application target;
- enable the `bluetooth-peripheral` background mode;
- publish and advertise the GATT service in `../../packages/protocol/BLE_GATEWAY_V1.md`;
- verify foreground enrollment, background writes, state restoration, and
  reconnection on the owner's actual iPhone;
- exercise an `AVAudioSession` play-and-record configuration over the verified
  HFP call-audio route;
- perform a cellular-backed ping and measure locked-phone latency.

Do not write speculative Swift that cannot be compiled or exercised before the
Xcode handoff. No paid Apple capability or service may be enabled without
Joshua's approval.
