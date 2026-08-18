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
