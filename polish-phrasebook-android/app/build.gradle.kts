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
        versionCode = 24
        versionName = "1.23"
    }
}

dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
}
