plugins {
    id("com.android.application")
}

val startupFallbackNames = listOf(
    "startup_morning_bluetooth_disabled.mp3",
    "startup_morning_assistant_unavailable.mp3",
    "startup_afternoon_bluetooth_disabled.mp3",
    "startup_afternoon_assistant_unavailable.mp3",
    "startup_evening_bluetooth_disabled.mp3",
    "startup_evening_assistant_unavailable.mp3",
)

val verifyStartupFallbackAssets by tasks.registering {
    val fallbackFiles = startupFallbackNames.map { layout.projectDirectory.file("src/main/res/raw/$it") }
    inputs.files(fallbackFiles)
    doLast {
        fallbackFiles.forEach { regularFile ->
            val file = regularFile.asFile
            require(file.isFile) { "Missing licensed startup fallback: ${file.name}" }
            val bytes = file.readBytes()
            val mp3Header = bytes.size >= 3 && (
                (bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) ||
                    (bytes[0] == 0xff.toByte() && (bytes[1].toInt() and 0xe0) == 0xe0)
                )
            require(mp3Header) { "Startup fallback is not an MP3: ${file.name}" }
            require(bytes.size <= 128 * 1024) { "Startup fallback exceeds 128 KiB: ${file.name}" }
        }
    }
}

android {
    namespace = "com.jglass.diagnostic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jglass.diagnostic"
        minSdk = 19
        targetSdk = 19
        versionCode = 7
        versionName = "0.7.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += "ExpiredTargetSdkVersion"
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyStartupFallbackAssets)
}
