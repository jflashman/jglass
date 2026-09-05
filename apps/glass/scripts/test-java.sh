#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
glass_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
test_classes=$(mktemp -d /tmp/jglass-java-tests.XXXXXX)

if [ -n "${JGLASS_JAVA_HOME:-}" ]; then
    java_home=$JGLASS_JAVA_HOME
elif [ -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
    java_home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
elif [ -x /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
    java_home=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
elif [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]; then
    java_home=/usr/lib/jvm/java-21-openjdk-amd64
elif [ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/java ]; then
    java_home=/usr/lib/jvm/java-17-openjdk-amd64
else
    echo "JGlass requires JDK 17-23 for Java self-tests." >&2
    echo "Set JGLASS_JAVA_HOME to a compatible JDK directory." >&2
    exit 1
fi

"$java_home/bin/javac" \
    -d "$test_classes" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/BoundedPcmBuffer.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/BleAdvertisementMatcher.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/BleControlFraming.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/GatewayProbeCoordinator.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/StrictJson.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/BridgeJsonClient.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/DeviceConfigProtocol.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/DeviceConfigCache.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/Base64Codec.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/StartupGreetingRules.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/StartupGreetingRetryPolicy.java" \
    "$glass_dir/src/main/java/com/jglass/diagnostic/StartupGreetingProtocol.java" \
    "$glass_dir/src/testsupport/java/com/jglass/diagnostic/BoundedPcmBufferSelfTest.java" \
    "$glass_dir/src/testsupport/java/com/jglass/diagnostic/BleAdvertisementMatcherSelfTest.java" \
    "$glass_dir/src/testsupport/java/com/jglass/diagnostic/BleControlFramingSelfTest.java" \
    "$glass_dir/src/testsupport/java/com/jglass/diagnostic/GatewayProbeCoordinatorSelfTest.java" \
    "$glass_dir/src/testsupport/java/com/jglass/diagnostic/StartupGreetingProtocolSelfTest.java" \
    "$glass_dir/src/testsupport/java/com/jglass/diagnostic/DeviceConfigSelfTest.java" \
    "$glass_dir/src/testsupport/java/com/jglass/diagnostic/BridgeJsonClientSelfTest.java"

"$java_home/bin/java" -cp "$test_classes" com.jglass.diagnostic.BoundedPcmBufferSelfTest
"$java_home/bin/java" -cp "$test_classes" com.jglass.diagnostic.BleAdvertisementMatcherSelfTest
"$java_home/bin/java" -cp "$test_classes" com.jglass.diagnostic.BleControlFramingSelfTest
"$java_home/bin/java" -cp "$test_classes" com.jglass.diagnostic.GatewayProbeCoordinatorSelfTest
"$java_home/bin/java" -cp "$test_classes" com.jglass.diagnostic.StartupGreetingProtocolSelfTest \
    "$glass_dir/../../packages/protocol/fixtures/startup-greeting/success-with-fallbacks.json"
"$java_home/bin/java" -cp "$test_classes" com.jglass.diagnostic.DeviceConfigSelfTest \
    "$glass_dir/../../packages/protocol/fixtures/device-config/cases.json"
"$java_home/bin/java" -cp "$test_classes" com.jglass.diagnostic.BridgeJsonClientSelfTest "$@"
