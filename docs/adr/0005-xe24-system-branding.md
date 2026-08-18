# ADR 0005: reversible XE24 JGlass system branding

- Status: accepted and physically verified
- Date: 2026-08-18

## Context

The product is intended to replace the stock Glass presentation rather than run
as a separately branded diagnostic app. The connected XE-C reports official
XE24 `XRH35`; its stock boot animation is one 640×360 frame at
`/system/media/bootanimation.zip`. The production user build is not rooted and
mounts `/system` read-only.

## Decision

Retain the official XE24 kernel, recovery, bootloader, framework, launcher, and
signatures. Patch only `/media/bootanimation.zip` inside a verified copy of the
official sparse `system.img`, replacing the `GLASS` frame with a deterministic
JGlass concentric-ring animation. Do not live-remount `/system`, root the unit,
or alter signed/odexed system APKs for branding.

The build expands and checks the ext4 image, replaces the one file with root
ownership and mode 0644, compacts it, expands it again, checks the filesystem,
and byte-compares the embedded animation. Flashing requires an explicit sparse
chunk size because current Fastboot otherwise stalls while negotiating with the
legacy XE24 bootloader.

On 2026-08-18, Fastboot 37.0.1 on macOS successfully wrote all ten chunks with
`-S 64M --verbose --unbuffered`; the operation exited successfully after 103.8
seconds. The flashed sparse image SHA-256 was
`88f8122fd58aba274339fcd44a2ead79500f486869047bc10a7124b965d99363`.
The same host's unbounded flash and reboot commands stall without a final
bootloader acknowledgement, so the unit was physically power-cycled after the
successful write. The physical XE24 then booted successfully and displayed the
new animated JGlass system phase. An earlier stock `GLASS` phase remains visible
for a few seconds before the Android system animation starts. Its storage
location has not been verified; it is likely an earlier boot stage and is out of
scope for this system-partition-only change. The owner accepted this sequence.
Kali remains the established fallback bootloader host.

## Consequences

The device remains XE24 but no longer has a byte-identical stock system
partition after flashing. Rollback is the untouched official XE24 `system.img`
from the archive whose SHA-1 is
`46430bc827267796566f0a73d9b503059759561c`. A branding rollback does not require
erasing userdata or replacing boot, recovery, or bootloader partitions.
