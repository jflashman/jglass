# ADR 0002: Use a modern pinned Android build toolchain for API 19

- Status: Accepted
- Date: 2026-08-17

## Context

The physical Glass XE24 device reports Android 4.4.4 / API 19. The Kali
development host has ADB and several JDKs but no complete Android SDK platform
or Gradle installation. Relying on obsolete global build tools would make the
prototype difficult to reproduce on the eventual Linux VM and would expose the
build to known toolchain defects.

The client must remain runnable on API 19 even when it is compiled with a
maintained toolchain. The first diagnostic slice does not require the GDK and
must not pull in AndroidX or community Glass code.

## Decision

- Use Java for the Glass client.
- Pin Android Gradle Plugin 8.13.2 and Gradle 8.13.
- Run Gradle with JDK 21 on the current Kali host. JDK 17 is the documented
  minimum for this AGP line and is also acceptable on another Linux host.
- Compile against Android API 35 while keeping both `minSdk` and `targetSdk` at
  API 19. Android lint remains enabled to detect accidental use of newer APIs.
- Commit and verify the Gradle wrapper so developers do not need a global
  Gradle installation.
- Keep the diagnostic app dependency-free beyond the Android and Java
  platforms. Add the GDK only after its exact artifact and license are verified.

## Consequences

The build uses maintained dexing, packaging, and lint tools while producing an
APK installable on the stock device. Compiling against API 35 does not make API
35 calls safe; source must continue to pass the API-level lint checks and must
be tested on physical Glass.

Keeping `targetSdk` at 19 preserves the legacy behavior expected by XE24. This
APK is a private, trusted-LAN diagnostic artifact, not a Play-distributed app.
The target should be revisited if later platform behavior or distribution
requirements change.

AGP 8.13 requires Gradle 8.13 and JDK 17 or newer according to the official
Android release notes. The wrapper distribution checksum is pinned alongside
the wrapper configuration.
