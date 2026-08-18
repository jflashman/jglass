# XE24 system branding

This directory replaces the stock 640×360 `GLASS` boot frame with a JGlass
concentric-ring animation while leaving the kernel, recovery, bootloader,
framework, launcher, and application signatures unchanged. It targets only the
verified XE-C `glass_1` XE24 / `XRH35` system image.

Build the animation and patch a verified copy of the official image:

```sh
firmware/xe24/branding/build-boot-animation.sh

firmware/xe24/branding/patch-system-image.sh \
  /absolute/path/to/official/system.img \
  firmware/xe24/branding/build/bootanimation.zip \
  firmware/xe24/branding/build/system-jglass.img
```

The patch helper requires Homebrew `e2fsprogs`, expands the Android sparse
image, normalizes and checks the stock ext4 filesystem with `e2fsck`, replaces only
`/media/bootanimation.zip`, restores root ownership and mode `0644`, compacts
the image, expands it again, and byte-compares the embedded animation. Generated
images stay under ignored `build/` directories.

Before flashing, verify the official factory archive SHA-1 is
`46430bc827267796566f0a73d9b503059759561c`, confirm `fastboot getvar product`
reports `glass_1`, and record the generated SHA-256 printed by the helper. Use
the known-reliable Kali host when available.

Current Fastboot must be given an explicit sparse transfer limit for this legacy
bootloader. An unbounded Fastboot 37.0.1 operation on macOS stalled before the
first send/write status, while the following system-only command completed all
ten chunks successfully on 2026-08-18:

```sh
fastboot -S 64M --verbose --unbuffered flash system \
  /absolute/path/to/firmware/xe24/branding/build/system-jglass.img
```

Do not omit `-S 64M`. The same host may wait indefinitely for the final
acknowledgement to `fastboot reboot`; if that happens after the flash command has
already exited successfully, disconnect USB and physically power-cycle Glass.
Do not interrupt a send/write operation in progress.

Physical XE24 verification on 2026-08-18 showed two visible stages: the stock
`GLASS` animation appears first, then the new animated `JGLASS` phase replaces it
after a few seconds and Android boots successfully. The first phase occurs
before this patched system animation; its storage location has not been
verified. Replacing it would require a separate, evidence-backed boot-stage
inventory and is intentionally deferred.

Rollback is the unmodified official XE24 `system.img`. Do not erase userdata,
replace recovery, or flash boot/bootloader partitions for this branding-only
change.

## JGlass Home shell image

XE24 retains a factory `mAlways=true` preferred Home entry for
`com.google.glass.home/.timeline.MainTimelineActivity`. The stock API-19 package
manager refused both package and component disable requests, and the Glass
resolver treated a JGlass selection as one-time. To make JGlass the only Home
candidate while preserving Settings, build a second-layer system image from the
exact branded image physically verified in ADR 0005:

```sh
firmware/xe24/branding/patch-home-shell-image.sh \
  firmware/xe24/branding/build/system-jglass.img \
  apps/glass/build/outputs/apk/debug/glass-debug.apk \
  firmware/xe24/branding/build/system-jglass-home.img
```

The helper refuses any source image whose SHA-256 differs from the previously
flashed branded image. It requires JGlass 0.7.0 with the expected Home activity,
expands and checks ext4, removes only `/priv-app/GlassHome.apk`, writes the exact
tested APK as `/priv-app/JGlass.apk`, restores root ownership, mode 0644, and the
stock `system_file` SELinux label, then compacts, re-expands, checks, and
byte-compares both JGlass and the existing boot animation.

This deliberately removes the stock timeline package from the active system
partition. It does not erase userdata and retains the signed XE24 Settings and
System UI packages. Rollback is the already verified branded image above, or the
unmodified official XE24 system image for a complete system-partition rollback.
Use the same `fastboot -S 64M --verbose --unbuffered flash system ...` safeguards;
never flash an image that has not completed the helper's final verification.

On 2026-08-18, the resulting image SHA-256 was
`e9dd28f7051ca5a48bc29e53c74de8a6b115e83f04310a8de8237d2f3be07a1f` and it
was physically flashed and cold-booted successfully. All ten sparse send/write
pairs reported `OKAY`; the flash exited after 103.713 seconds. Post-boot ADB
verified JGlass as the resumed Home, `JGlass.apk` present, and `GlassHome.apk`
absent.

On this specific macOS/Fastboot 37.0.1 combination, do not run a separate
Fastboot command immediately before the flash. Two sessions in which
`fastboot getvar product` succeeded first left the following flash stalled before
any send/write report. The successful sequence was:

1. Verify `ro.product.device=glass-1` from healthy Android and verify the image
   hash on the host.
2. Run `adb reboot bootloader`.
3. Make the `fastboot -S 64M ... flash system` command the first Fastboot process
   in that USB session.

The device's bootloader identity had already been physically verified as
`glass_1` in prior isolated sessions. Prefer the established Kali host when a
fresh bootloader identity check and flash must be performed as separate commands.
