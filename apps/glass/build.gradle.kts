plugins {
    id("com.android.application")
}

android {
    namespace = "com.jglass.diagnostic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jglass.diagnostic"
        minSdk = 19
        targetSdk = 19
        versionCode = 5
        versionName = "0.5.0"
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
