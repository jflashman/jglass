# Optional iPhone companion

This component is intentionally deferred.

An iPhone companion may manage JGlass-owned settings, selected service integrations, location, relay enrollment, or explicit commands. It must not be designed around the false assumption that an ordinary iOS app can read all other applications' notifications.

Notification mirroring should first investigate Glass acting as an Apple Notification Center Service (ANCS) BLE client. Service-specific replies are separate integrations and must use public, permitted APIs.
