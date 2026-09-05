# JGlass protocol

Versioned message contracts, compatibility fixtures, and protocol documentation.

The Glass implementation must remain compatible with Android API 19, so shared schemas do not imply a shared runtime package. Keep canonical, language-neutral fixtures and generate or hand-maintain small platform-specific models as appropriate.

Requirements:

- explicit protocol version;
- request, session, and action IDs;
- idempotent handling of retried side effects;
- forward-compatible unknown-field behavior;
- bounded payload sizes;
- cancellation and interruption events;
- no secrets in normal logs.

## Current transport profiles

- [BLE gateway draft 1](BLE_GATEWAY_V1.md): Glass-to-iPhone application relay,
  diagnostic messages, GATT UUIDs, safety limits, and the security work required
  before sensitive data is permitted.
- [Startup greeting v1](STARTUP_GREETING_V1.md): authenticated trusted-LAN
  weather, speech, and legacy fixed-fallback provisioning compatibility.
- [Device configuration v1](DEVICE_CONFIG_V1.md): authenticated wake-phrase
  configuration, validation, and offline cache rules.

Canonical examples live in `fixtures/`. Fixtures contain synthetic identifiers
only and must never be replaced with captured production traffic.
