# ADR 0006: Make JGlass the primary device shell

- Status: accepted and physically verified
- Date: 2026-08-18

## Context

The receiver-based startup greeting cannot appear until XE24 broadcasts
`BOOT_COMPLETED`. Physical logs showed the stock Glass home at 15:35:30, the
broadcast at 15:35:41, and JGlass displayed at 15:35:45. The resulting stock-home
gap is inherent to launching an ordinary activity after the broadcast.

The owner clarified that JGlass should ultimately replace nearly all stock
user-facing behavior. Preserving the stock timeline is not a requirement, but
system Settings and a recovery route must remain accessible. This direction does
not require provider credentials on Glass and does not change the thin-client
boundary for modern AI, policy, networking, or computer actions.

## Decision

Register `JGlassHomeActivity` for Android's `MAIN`/`HOME`/`DEFAULT` intent. When
the user selects it as Home, Android enters the JGlass clock/status surface
directly and that activity owns the once-daily startup greeting. Tap opens the
assistant activity. Swipe down/Back opens the generic system Settings intent
when available; Menu remains a secondary recovery input. Physical XE24 package
inspection showed that its
signed Settings UI instead registers
`com.google.glass.action.ACTION_GO_TO_SETTINGS`; JGlass uses that verified action
as a narrow fallback.

Keep the existing boot receiver as compatibility behavior when JGlass is not the
resolved default Home. The receiver exits immediately when JGlass is default,
preventing a delayed duplicate launch. Installing the APK only adds a Home
candidate; it does not programmatically seize the role.

The APK-only release initially retained the stock launcher. After physical tests
showed that XE24's package manager refused package/component disable requests and
its resolver treated JGlass as a one-time choice, the owner explicitly approved
a system-only Fastboot change. Build from the exact ADR-0005 branded image,
remove only `/priv-app/GlassHome.apk`, and embed the exact tested JGlass APK as
`/priv-app/JGlass.apk`. Retain signed Settings, System UI, framework, hardware
services, boot, recovery, bootloader, and userdata. Rollback remains the verified
branded system image or official XE24 system image.

## Consequences

JGlass can replace the visible stock timeline without waiting for
`BOOT_COMPLETED`, while XE24 continues to supply its verified hardware stack and
Settings. The first Home surface is deliberately small; stock cards, voice menu,
notifications, and launcher behavior do not automatically carry over and must be
implemented as explicit JGlass features.

The Home role is high impact even though it is reversible. Physical acceptance
requires cold boot, display sleep/wake, Settings entry, assistant entry, crash
recovery, and restoration of the stock Home choice. AOSP or removal of signed
packages remains a later decision based on measured XE24 framework blockers.

## Initial physical result

Version 0.7.0 installed successfully on the XE24 unit. An explicit launch of
`JGlassHomeActivity` succeeded; center opened the JGlass assistant activity,
Back returned Home, and Back from Home opened the signed Glass Settings
activity. A touchpad hold did not reliably produce the plain Android long-press
callback, so swipe down/Back replaced hold as the documented Settings gesture.
The owner accepted the layout.

XE24 retains an `mAlways=true` preferred Home entry for
`com.google.glass.home/.timeline.MainTimelineActivity`. Its stored candidate set
contains the stock timeline and the retired stock alternative, not JGlass. The
new APK therefore does not receive ordinary Home intents merely by being
installed. Later package/component disable requests left those components
enabled but changed the factory preferred entry from permanent to one-time; the
resolver still did not establish JGlass permanently.

## System-Home physical result

The second-layer system image passed ext4 validation, re-expansion, embedded APK
byte comparison, boot-animation byte comparison, ownership/mode checks, and
SELinux-label verification. Its SHA-256 is
`e9dd28f7051ca5a48bc29e53c74de8a6b115e83f04310a8de8237d2f3be07a1f`.

On the verified macOS host, a separate successful `fastboot getvar` left the
legacy USB interface unable to service the following flash process. Two such
attempts were stopped before any reported send/write and left the system intact.
The successful sequence verified `glass-1` in Android, rebooted to bootloader,
and made `fastboot -S 64M --verbose --unbuffered flash system ...` the first
Fastboot process. All ten send/write pairs reported `OKAY`; the command exited
successfully after 103.713 seconds. The known post-flash reboot acknowledgement
hang recurred, so Glass was physically power-cycled.

The device booted successfully. ADB then verified Android boot complete,
`JGlass.apk` present under `/system/priv-app`, `GlassHome.apk` absent, and
`JGlassHomeActivity` resumed as Home. Signed XE24 Settings had already been
physically verified through swipe down/Back before the flash and remains present
in the verified system image.
