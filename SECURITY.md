# Security policy

JGlass is currently a private personal experiment, not a supported public product.

## Sensitive material

Never commit or publish:

- OpenAI, ElevenLabs, GitHub, relay, or cloud credentials;
- device tokens, private keys, signing keys, or provisioning profiles;
- Wi-Fi credentials or personal network details;
- notification bodies, transcripts, recordings, camera captures, or computer context;
- the Glass device serial number.

If a secret is committed, treat it as compromised: revoke or rotate it first, then remove it from history as a separate cleanup step.

## Remote access

Do not expose raw SSH, VNC, ADB, the bridge development server, or a Codex worker directly to the public internet. Remote designs require authentication, encryption, revocation, rate limiting, replay protection, scoped workspaces, explicit action approval, and a documented threat model.

The startup bridge binds to loopback unless the operator explicitly selects a
trusted-LAN address. Its device bearer token, ElevenLabs key/voice ID, weather
coordinates, spoken text, and audio are excluded from normal logs. Glass stores
only its bridge URL, revocable device token, last greeting date, debug replay
mode, and app-private copies of fixed APK fallback MP3s. Dynamic weather audio
is deleted after playback; camera, microphone, and notification capture are not
part of startup. The boot receiver performs only a local daily-rule check and
Activity launch; it never handles credentials, networking, or audio.

## Device safety

Firmware flashing, partition erasure, rooting, recovery replacement, and bootloader operations can brick or wipe irreplaceable hardware. They require explicit approval and a verified recovery path.

## Android build tooling

The Glass APK has no third-party runtime dependencies. Android Gradle Plugin
8.13.2 does, however, resolve host-side libraries used by its build, signing,
lint, gRPC, and Unified Test Platform tooling. Security floors in the root
`build.gradle.kts` keep the alerting transitive families at patched versions:

- Netty `4.1.136.Final`;
- Bouncy Castle `1.80.2`;
- jose4j `0.9.6`;
- JDOM `2.0.6.1`;
- protobuf Java/Kotlin `3.25.5`.

These libraries run on the development or CI host and are not packaged into the
API 19 APK. Keep the overrides narrowly scoped to the root buildscript and AGP's
isolated Unified Test Platform configurations. Re-audit them whenever AGP is
upgraded, and remove an override only after the resolved dependency graph and
GitHub alerts both confirm the upstream toolchain is patched.

## Reporting

While the repository is private, document suspected vulnerabilities in a private GitHub issue without including live secrets or personal captured data.
