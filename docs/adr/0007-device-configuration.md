# ADR 0007: Configure the wake phrase through the existing bridge

- Status: implemented; host-verified, physical verification pending
- Date: 2026-09-05

## Context

The wake-phrase investigation selected the installed XE24 voice stack for a
future awake-only `JARVIS` experiment. Configuration must be available offline
without hardcoding enrollment or provider credentials into the APK. That
requires a small independently testable configuration path before recognition.

## Decision

Add authenticated `GET /v1/device-config` on the existing trusted-LAN HTTP
transport (ADR 0004), using `jglass/device-config/1`. The bridge supplies one
bounded English phrase; Glass validates and caches its canonical representation
in app-private preferences. Each Home resume attempts one bounded refresh.
Failed refreshes preserve the last valid value. Re-enrollment clears the cache,
and cancellation/enrollment checks prevent stale requests from replacing it.

Startup greeting and configuration share the dependency-free API-19 HTTP
client. Each request owns its connection and cancellation state, checks response
type and exact bounded length, rejects redirects, and has an overall deadline.
The JSON reader caps container nesting at 32 to prevent stack exhaustion.

## Consequences and boundaries

The endpoint calls no AI, weather, or speech provider. It returns only protocol
and phrase; normal logs remain metadata-only. Existing trusted-LAN HTTP risks
remain: a bearer token authenticates the client but does not encrypt the link or
protect against an active network attacker. This does not authorize public
exposure or replace future enrollment/TLS work.

An older bridge's 404 is a safe cache-preserving failure. An unconfigured client
has no implicit phrase. The APK adds no dependency, Google binary, recognizer
asset, reflected voice API, or microphone session. Implementing the installed
recognizer adapter and establishing its exact interface and lifecycle remain
future work; no detection or battery claim follows from configuration tests.

The bridge and Java code consume shared validation fixtures. An optional
provider-free Java-to-Node loopback test checks the actual HTTP transport.
Physical verification is documented separately in
`docs/testing/2026-09-05-device-config.md`.
