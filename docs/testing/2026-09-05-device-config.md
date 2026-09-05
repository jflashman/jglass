# Device configuration and transport verification — 2026-09-05

## Scope

Glass 0.7.1 (version code 8), authenticated device configuration, cache behavior,
and shared startup/configuration transport fixes. No recognizer implementation,
provider calls, firmware writes, or device installation were performed.

## Host checks

From the repository root:

```sh
apps/glass/scripts/test-java.sh
apps/glass/scripts/gradle.sh :apps:glass:lintDebug :apps:glass:assembleDebug --offline
cd services/bridge
npm test
npm run test:glass
```

- Seven Java suites: existing buffer/BLE/startup behavior plus configuration
  validation/cache rollback and HTTP transport regressions.
- Fourteen bridge tests: authentication, exact response fields, safe logs,
  shared phrase fixtures, invalid configuration, and existing startup behavior.
- The optional integration check starts an ephemeral loopback bridge with
  synthetic credentials, executes the Java HTTP client, and verifies `JARVIS`.
  It never contacts a provider and requires the existing JDK toolchain.
- Transport cases include redirects, unauthorized responses, wrong MIME,
  missing/oversized/truncated/mismatched lengths, overlapping cancellation,
  late completion, and an overall deadline. JSON nesting is bounded even for
  otherwise byte-bounded payloads.

The startup loader also reuses its three arc arrays and drawing bounds instead
of allocating three arrays and five `RectF` objects each frame. Geometry and
timing are unchanged; no physical smoothness or battery improvement is claimed.

Android lint and APK assembly pass with existing SDK/JDK compatibility and
resource warnings. Node HTTP tests require loopback networking; the sandboxed Node 24.2.0
run aborted internally, while the same baseline passed with loopback access.

## Physical status

Not verified on XE24 or an emulator. ADB started but reported no connected
device; the owner confirmed Glass needed time to charge and requested continued
host work. Prior physical results apply to the earlier builds only.

## Resume on charged Glass

1. Verify ADB connectivity and API 19/XE24 without recording device identifiers.
   Install the debug APK as a normal application update; do not flash a system
   image, uninstall the system Home, or clear app data.
2. Use the existing private startup enrollment and a trusted-LAN bridge with
   `JGLASS_WAKE_PHRASE=JARVIS`. Resume Home. Confirm the clock, tap-to-assistant,
   Back-to-Home, and Home-Back-to-Settings paths still work.
3. Inspect only the `device_config` preference through an authorized private
   diagnostic. Never dump the containing preferences file: it also holds the
   device token. Confirm canonical version-1 `JARVIS` is present.
4. Stop the bridge and return Home. Confirm the cached value survives failed
   refreshes and a normal app restart. Restore the bridge with a changed valid
   phrase, return Home, and verify the cache changes.
5. Using a provider-free fixture server and synthetic enrollment, exercise 401,
   404, malformed JSON, oversized data, and delayed replies. Confirm failures
   preserve the cache; leaving Home prevents late replies from overwriting it.
   Restore the existing enrollment privately afterward.
6. Change enrollment and confirm the old phrase is cleared. Restore enrollment
   and confirm the next successful refresh repopulates it. No recognition or
   activation is expected in this build.
7. Recheck loader appearance, startup greeting cancellation, return-to-Home idle status, and the
   existing recording/playback diagnostic. Any provider-backed greeting run
   requires the existing authorized provider setup; host tests used mocks.

Physical SharedPreferences persistence, Home lifecycle races, XE24 network
timeouts, display sleep/wake, and audio cancellation still require these tests.
