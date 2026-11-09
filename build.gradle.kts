// Security floors for host-side Android build tooling only. None of these
// libraries are packaged into the Glass APK. Keep related library families on
// one version to avoid mixing binary-incompatible transitive artifacts.
buildscript {
    configurations.classpath {
        resolutionStrategy.force(
            "com.google.protobuf:protobuf-java:3.25.5",
            "com.google.protobuf:protobuf-java-util:3.25.5",
            "com.google.protobuf:protobuf-kotlin:3.25.5",
            "io.netty:netty-buffer:4.1.136.Final",
            "io.netty:netty-codec:4.1.136.Final",
            "io.netty:netty-codec-http:4.1.137.Final",
            "io.netty:netty-codec-http2:4.1.136.Final",
            "io.netty:netty-codec-socks:4.1.136.Final",
            "io.netty:netty-common:4.1.136.Final",
            "io.netty:netty-handler:4.1.137.Final",
            "io.netty:netty-handler-proxy:4.1.136.Final",
            "io.netty:netty-resolver:4.1.136.Final",
            "io.netty:netty-transport:4.1.136.Final",
            "io.netty:netty-transport-native-unix-common:4.1.136.Final",
            "org.bitbucket.b_c:jose4j:0.9.6",
            "org.bouncycastle:bcpkix-jdk18on:1.84",
            "org.bouncycastle:bcprov-jdk18on:1.85",
            "org.bouncycastle:bcutil-jdk18on:1.80.2",
            "org.jdom:jdom2:2.0.6.1",
        )
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
}

// AGP creates isolated Unified Test Platform configurations that do not inherit
// the root buildscript resolution strategy. Apply the same host-tool security
// floors there. The Glass application configurations currently have no runtime
// dependencies, so these artifacts still cannot enter the APK.
allprojects {
    configurations.configureEach {
        if (name.startsWith("_internal-unified-test-platform-")) {
            resolutionStrategy.force(
                "com.google.protobuf:protobuf-java:3.25.5",
                "com.google.protobuf:protobuf-java-util:3.25.5",
                "com.google.protobuf:protobuf-kotlin:3.25.5",
                "io.netty:netty-buffer:4.1.136.Final",
                "io.netty:netty-codec:4.1.136.Final",
                "io.netty:netty-codec-http:4.1.137.Final",
                "io.netty:netty-codec-http2:4.1.136.Final",
                "io.netty:netty-codec-socks:4.1.136.Final",
                "io.netty:netty-common:4.1.136.Final",
                "io.netty:netty-handler:4.1.137.Final",
                "io.netty:netty-handler-proxy:4.1.136.Final",
                "io.netty:netty-resolver:4.1.136.Final",
                "io.netty:netty-transport:4.1.136.Final",
                "io.netty:netty-transport-native-unix-common:4.1.136.Final",
            )
        }
    }
}
