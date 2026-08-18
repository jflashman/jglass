#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
output_dir=${1:-"$script_dir/build"}
work_dir=$(mktemp -d /tmp/jglass-boot-animation.XXXXXX)
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

if [ -n "${JGLASS_JAVA_HOME:-}" ]; then
    java_home=$JGLASS_JAVA_HOME
elif [ -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
    java_home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
elif [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]; then
    java_home=/usr/lib/jvm/java-21-openjdk-amd64
else
    echo "Set JGLASS_JAVA_HOME to a JDK 17 or newer." >&2
    exit 1
fi

mkdir -p "$output_dir"
mkdir -p "$work_dir/classes"
"$java_home/bin/javac" -d "$work_dir/classes" "$script_dir/BootAnimationGenerator.java"
"$java_home/bin/java" -Djava.awt.headless=true -cp "$work_dir/classes" \
    com.jglass.firmware.BootAnimationGenerator "$work_dir/animation"

find "$work_dir/animation" -type f -exec touch -t 200901010000 {} \;
rm -f "$output_dir/bootanimation.zip"
(cd "$work_dir/animation" && find . -type f | LC_ALL=C sort | \
    zip -0 -X -q "$output_dir/bootanimation.zip" -@)
unzip -t "$output_dir/bootanimation.zip"
shasum -a 256 "$output_dir/bootanimation.zip"
