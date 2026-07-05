plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.polishphrasebook"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.polishphrasebook"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
    }
}
