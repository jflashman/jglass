#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
glass_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
test_classes=$(mktemp -d /tmp/jglass-java-tests.XXXXXX)

javac \
    -d "$test_classes" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/BoundedPcmBuffer.java" \
    "$glass_dir/src/testsupport/java/com/jglass/diagnostic/BoundedPcmBufferSelfTest.java"

java -cp "$test_classes" com.jglass.diagnostic.BoundedPcmBufferSelfTest
