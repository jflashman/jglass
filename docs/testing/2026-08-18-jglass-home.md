# JGlass Home physical smoke test

- Date: 2026-08-18
- Device: connected XE-C on official XE24 / Android 4.4.4
- APK: JGlass 0.7.0 (`versionCode` 7), debug
- Scope: activity/input routing followed by owner-authorized system-Home flash

## Result

The APK installed over v0.6 without clearing app data. Android explicitly
started `com.jglass.diagnostic/.JGlassHomeActivity` and reported a successful
activity launch. The following routes were observed through ActivityManager:

- center-key input opened `com.jglass.diagnostic/.MainActivity`;
- Back from the assistant returned to `JGlassHomeActivity`;
- Back from JGlass Home opened the signed
  `com.google.glass.settings.ui/.SettingsActivity`.

The standard `android.settings.SETTINGS` action is not registered on this XE24
image. Read-only package inspection verified
`com.google.glass.action.ACTION_GO_TO_SETTINGS` as the supported Settings action,
and the installed v0.7 fallback successfully used it. A physical touchpad hold
did not reliably generate the plain Android long-press callback, so the visible
control and implementation now use swipe down/Back. The user still needs to
confirm the clock/status layout while wearing Glass.

## Default-Home finding

Read-only package inspection showed a persistent preferred activity for
`MAIN`/`DEFAULT`/`HOME`:

```text
com.google.glass.home/.timeline.MainTimelineActivity
mAlways=true
```

The stored candidate set predates JGlass, so installing a new Home activity did
not replace the stock timeline or display a chooser. This test did not clear the
preference, disable a package, erase app data, reboot, root, or modify a
partition.

## System-Home result

The owner approved a system-only Fastboot change after XE24 refused package and
component disable requests and treated the resolver choice as one-time. A
verified image removed only `/priv-app/GlassHome.apk`, embedded the exact tested
APK as `/priv-app/JGlass.apk`, and retained signed Settings and System UI. Final
image SHA-256:

```text
e9dd28f7051ca5a48bc29e53c74de8a6b115e83f04310a8de8237d2f3be07a1f
```

Two flash attempts preceded by a separate successful `fastboot getvar product`
stalled before any send/write report. The previously flashed system remained
intact after normal boots. In the successful session, the chunked flash was the
first Fastboot process after `adb reboot bootloader`. All ten `-S 64M` send/write
pairs reported `OKAY`, and Fastboot exited after 103.713 seconds. The host-side
reboot acknowledgement hung as previously documented; a physical power cycle
booted Android successfully.

Post-boot ADB verification reported:

- `sys.boot_completed=1`;
- `/system/priv-app/JGlass.apk` present with root ownership and mode 0644;
- `/system/priv-app/GlassHome.apk` absent;
- `com.jglass.diagnostic/.JGlassHomeActivity` resumed as Home.

Display sleep/wake, crash recovery, and a physical rollback to the preserved
branded/official system image remain unverified.

## Post-flash cleanup

Read-only inspection confirmed PackageManager runs
`/system/priv-app/JGlass.apk` directly with `SYSTEM` and `PRIVILEGED` flags. No
duplicate `/data/app` update or temporary installer APK remained, and there were
no `dynamic.mp3` or `dynamic.mp3.tmp` files. The two existing app-private audio
files are current `bundled-en-US-v2` licensed fallbacks and were retained.

The obsolete 4.6 KiB app-owned OpenGL shader cache was removed. The debug replay
policy used for boot testing was reset from `always` to `daily`, and Android
reported `JGlassHomeActivity` resumed afterward. Stock Home userdata and all
host rollback images were intentionally preserved.
