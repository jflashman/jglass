# BLE reliability test log — 2026-08-18

## Test environment

- Glass: physical Explorer Edition XE-C on stock XE24 / Android 4.4.4 API 19.
- Glass app: JGlass diagnostic v0.4.0, version code 4.
- Phone: physical iPhone 16 Pro with JGlass Companion v0.2.
- Host: primary Apple Silicon macOS development machine.
- Transport: draft JGlass 20-byte fragmented BLE GATT control profile.
- Payloads: bounded non-sensitive `gateway.hello` and `gateway.ping` only.

No device identifiers, Bluetooth addresses, packet contents, or user data were
retained in this log.

## Completed results

| Scenario | Glass result | Companion counters after scenario | Result |
|---|---|---|---|
| Physical UI and audio regression | Two-line `Probe:` status fit the display; recording/playback still worked | Not applicable | Pass |
| Foreground one-shot | `oneshot 1/1` | Connected 0; Subscriptions 1; Messages 2; Restorations 0 | Pass |
| Maintained connection under iPhone lock | `connected-wake 6/6` | Connected 0; Subscriptions 2; Messages 9; Restorations 0; last event `Downlink unsubscribed` | Pass |
| Rediscovery after initial foreground exchange, then iPhone lock | `reconnect 1/6 last=not_found` | Connected 0; Subscriptions 3; Messages 11; Restorations 0 | Fail: five background rediscovery cycles were not found |
| Rediscovery with companion already ordinarily backgrounded and unlocked | `reconnect 0/6 last=not_found` | Counters unchanged from the prior completed scenario | Fail: all six background rediscovery cycles were not found |

The connected-wake counts are internally consistent: one new subscription and
seven messages represent one hello plus six pings. The locked reconnect counts
show only the initial foreground hello/ping; failed rediscovery cycles never
reached subscription or delivered control messages.

## Current conclusion

An established GATT connection survived iPhone lock and handled all five delayed
writes. After that connection closed, XE24 could not rediscover the companion
while it was locked or ordinarily backgrounded. The tested gateway direction is
therefore usable only while the connection survives; recovery after a drop is a
confirmed architecture blocker on this physical pair.

This result concerns BLE discovery/reconnection, not JSON validation or
fragmentation. No failed reconnect reached GATT subscription. No Core Bluetooth
restoration callback was observed in the completed scenarios.

## Tests still pending

1. **Maintained connection while ordinarily backgrounded and unlocked:** run
   `connected-wake`, press Home after `1/1`, and keep the phone unlocked through
   attempt 6. Lock-state connected wake passed, but this distinct lifecycle case
   was not run.
2. **Bluetooth interruption and recovery:** begin `reconnect` foregrounded; after
   `1/1`, turn iPhone Bluetooth off. After one failed cycle, restore Bluetooth,
   return the companion to the foreground, confirm `Advertising`, and determine
   whether later attempts recover.
3. **Force-quit and manual relaunch recovery:** begin `reconnect` foregrounded;
   force-quit after `1/1`, observe bounded failures, manually relaunch and enable
   the gateway, and determine whether a remaining attempt recovers. Record this
   separately from ordinary suspension.
4. **Core Bluetooth restoration:** determine whether any interruption or system
   relaunch invokes the companion restoration callback; a restoration count alone
   does not prove an exchange succeeded.
5. **Repeatability:** repeat the successful locked connected-wake case and the
   failed background reconnect cases before making a permanent transport change.

The first Bluetooth interruption attempt was restarted after operator error. The
second attempt was explicitly canceled for a meeting before producing a result.
Neither attempt is evidence. Those partial runs may have changed the companion's
process-lifetime counters, so restart the iPhone app before the next session if a
zeroed baseline is desired.

## Resume procedure

1. Continue from branch `feat/ble-reliability-probe`; its changes are currently
   uncommitted and unpushed.
2. Connect the authorized Glass over ADB. The diagnostic was force-stopped at the
   end of this session, so no probe remains active.
3. Launch JGlass Companion v0.2 on the iPhone, tap **Start BLE Gateway**, and
   confirm **Advertising**.
4. Start a mode from the repository root:

   ```sh
   adb shell am start -n com.jglass.diagnostic/.MainActivity \
     --es gateway_probe_mode connected-wake

   adb shell am start -n com.jglass.diagnostic/.MainActivity \
     --es gateway_probe_mode reconnect
   ```

5. Record the final Glass `successes/attempts` line and the iPhone Connected,
   Subscriptions, Messages, Restorations, and Last Event values.
6. Stop an active probe by leaving/force-stopping only the JGlass diagnostic app;
   do not change firmware, recovery, or device partitions.

## Verification already completed before physical testing

- All four Java self-test suites pass under Homebrew OpenJDK 21.
- Android lint passes and the API 19-compatible v0.4 APK assembles.
- All ten iOS tests pass in the iPhone 17 Pro / iOS 26.5 Simulator.
- The v0.4 APK installs and launches on physical XE24 without a fatal exception.
