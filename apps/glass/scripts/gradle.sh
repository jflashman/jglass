#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/../../.." && pwd)

if [ -n "${JGLASS_JAVA_HOME:-}" ]; then
    java_home=$JGLASS_JAVA_HOME
elif [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]; then
    java_home=/usr/lib/jvm/java-21-openjdk-amd64
elif [ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/java ]; then
    java_home=/usr/lib/jvm/java-17-openjdk-amd64
else
    echo "JGlass requires JDK 17-23 to run Gradle 8.13." >&2
    echo "Set JGLASS_JAVA_HOME to a compatible JDK directory." >&2
    exit 1
fi

if [ ! -x "$java_home/bin/java" ]; then
    echo "No Java executable found at $java_home/bin/java" >&2
    exit 1
fi

JAVA_HOME=$java_home
export JAVA_HOME

exec "$repo_dir/gradlew" "$@"
