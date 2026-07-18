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
        versionCode = 27
        versionName = "1.26"
    }
}

dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
}
