#!/usr/bin/env sh
set -eu

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 BRANDED_SYSTEM_IMG JGLASS_APK OUTPUT_SPARSE_IMG" >&2
    exit 2
fi

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
source_image=$1
jglass_apk=$2
output_image=$3
work_dir=$(mktemp -d /tmp/jglass-xe24-home.XXXXXX)
raw_image="$work_dir/system.raw.img"
verified_raw_image="$work_dir/verified.raw.img"
source_animation="$work_dir/source-bootanimation.zip"
verified_animation="$work_dir/verified-bootanimation.zip"
verified_apk="$work_dir/verified-jglass.apk"
source_listing="$work_dir/source-priv-app.txt"
verified_listing="$work_dir/verified-priv-app.txt"
expected_source_sha256=88f8122fd58aba274339fcd44a2ead79500f486869047bc10a7124b965d99363
debugfs=/opt/homebrew/opt/e2fsprogs/sbin/debugfs
e2fsck=/opt/homebrew/opt/e2fsprogs/sbin/e2fsck
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

if [ -n "${JGLASS_AAPT:-}" ]; then
    aapt=$JGLASS_AAPT
elif [ -x /opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/aapt ]; then
    aapt=/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/aapt
elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/build-tools/35.0.0/aapt" ]; then
    aapt="$ANDROID_HOME/build-tools/35.0.0/aapt"
else
    echo "Set JGLASS_AAPT to Android Build Tools 35.0.0 aapt." >&2
    exit 1
fi

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
test -f "$jglass_apk"
test -x "$debugfs"
test -x "$e2fsck"
test -x "$aapt"

source_sha256=$(shasum -a 256 "$source_image" | awk '{print $1}')
if [ "$source_sha256" != "$expected_source_sha256" ]; then
    echo "Refusing unverified branded system image: $source_sha256" >&2
    exit 1
fi

"$aapt" dump badging "$jglass_apk" | grep -q "package: name='com.jglass.diagnostic' versionCode='7' versionName='0.7.0'"
"$aapt" dump xmltree "$jglass_apk" AndroidManifest.xml | grep -q 'com.jglass.diagnostic.JGlassHomeActivity'
"$aapt" dump xmltree "$jglass_apk" AndroidManifest.xml | grep -q 'android.intent.category.HOME'

python3 "$script_dir/sparse_image.py" expand "$source_image" "$raw_image"
repair_filesystem "$raw_image"
"$debugfs" -R "ls -l /priv-app" "$raw_image" > "$source_listing"
grep -q 'GlassHome.apk' "$source_listing"
if grep -q 'JGlass.apk' "$source_listing"; then
    echo "Source image already contains JGlass.apk" >&2
    exit 1
fi
"$debugfs" -R "dump /media/bootanimation.zip $source_animation" "$raw_image"

"$debugfs" -w -R "rm /priv-app/GlassHome.apk" "$raw_image"
"$debugfs" -w -R "write $jglass_apk /priv-app/JGlass.apk" "$raw_image"
"$debugfs" -w -R "set_inode_field /priv-app/JGlass.apk uid 0" "$raw_image"
"$debugfs" -w -R "set_inode_field /priv-app/JGlass.apk gid 0" "$raw_image"
"$debugfs" -w -R "set_inode_field /priv-app/JGlass.apk mode 0100644" "$raw_image"
"$debugfs" -w -R "ea_set /priv-app/JGlass.apk security.selinux u:object_r:system_file:s0" "$raw_image"
repair_filesystem "$raw_image"

python3 "$script_dir/sparse_image.py" compact "$raw_image" "$output_image"
python3 "$script_dir/sparse_image.py" expand "$output_image" "$verified_raw_image"
"$e2fsck" -fn "$verified_raw_image"
"$debugfs" -R "ls -l /priv-app" "$verified_raw_image" > "$verified_listing"
if grep -q 'GlassHome.apk' "$verified_listing"; then
    echo "Verified image still contains GlassHome.apk" >&2
    exit 1
fi
grep -q 'JGlass.apk' "$verified_listing"
"$debugfs" -R "dump /priv-app/JGlass.apk $verified_apk" "$verified_raw_image"
cmp "$jglass_apk" "$verified_apk"
"$debugfs" -R "dump /media/bootanimation.zip $verified_animation" "$verified_raw_image"
cmp "$source_animation" "$verified_animation"
"$debugfs" -R "stat /priv-app/JGlass.apk" "$verified_raw_image" | grep -q 'Mode:  0644'
"$debugfs" -R "stat /priv-app/JGlass.apk" "$verified_raw_image" | grep -q 'User:     0   Group:     0'
"$debugfs" -R "ea_list /priv-app/JGlass.apk" "$verified_raw_image" | grep -q 'u:object_r:system_file:s0'

shasum -a 256 "$source_image" "$jglass_apk" "$source_animation" "$output_image"
