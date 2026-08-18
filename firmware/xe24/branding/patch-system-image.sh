#!/usr/bin/env sh
set -eu

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 OFFICIAL_SYSTEM_IMG BOOTANIMATION_ZIP OUTPUT_SPARSE_IMG" >&2
    exit 2
fi

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
source_image=$1
boot_animation=$2
output_image=$3
work_dir=$(mktemp -d /tmp/jglass-xe24-system.XXXXXX)
raw_image="$work_dir/system.raw.img"
verified_animation="$work_dir/bootanimation.zip"
debugfs=/opt/homebrew/opt/e2fsprogs/sbin/debugfs
e2fsck=/opt/homebrew/opt/e2fsprogs/sbin/e2fsck
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

repair_filesystem() {
    set +e
    "$e2fsck" -fy "$1"
    status=$?
    set -e
    if [ "$status" -gt 1 ]; then
        return "$status"
    fi
}

test -f "$source_image"
test -f "$boot_animation"
test -x "$debugfs"
test -x "$e2fsck"
unzip -tq "$boot_animation"

python3 "$script_dir/sparse_image.py" expand "$source_image" "$raw_image"
repair_filesystem "$raw_image"
"$debugfs" -w -R "rm /media/bootanimation.zip" "$raw_image"
"$debugfs" -w -R "write $boot_animation /media/bootanimation.zip" "$raw_image"
"$debugfs" -w -R "set_inode_field /media/bootanimation.zip uid 0" "$raw_image"
"$debugfs" -w -R "set_inode_field /media/bootanimation.zip gid 0" "$raw_image"
"$debugfs" -w -R "set_inode_field /media/bootanimation.zip mode 0100644" "$raw_image"
repair_filesystem "$raw_image"
"$debugfs" -R "dump /media/bootanimation.zip $verified_animation" "$raw_image"
cmp "$boot_animation" "$verified_animation"

python3 "$script_dir/sparse_image.py" compact "$raw_image" "$output_image"
python3 "$script_dir/sparse_image.py" expand "$output_image" "$work_dir/verified.raw.img"
"$e2fsck" -fn "$work_dir/verified.raw.img"
"$debugfs" -R "dump /media/bootanimation.zip $work_dir/verified-bootanimation.zip" \
    "$work_dir/verified.raw.img"
cmp "$boot_animation" "$work_dir/verified-bootanimation.zip"
shasum -a 256 "$source_image" "$boot_animation" "$output_image"
