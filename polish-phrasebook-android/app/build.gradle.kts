import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

// Signing credentials are resolved, in order, from:
//   1. keystore.properties in the module directory (git-ignored)
//   2. environment variables P4B_KEYSTORE / P4B_KEYSTORE_PASSWORD /
//      P4B_KEY_ALIAS / P4B_KEY_PASSWORD
// If neither is present the release build still runs, but unsigned — useful
// for CI verification. See docs/PLAY_STORE_RELEASE.md.
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProperties.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val storeFilePath = signingValue("storeFile", "P4B_KEYSTORE")
val storePasswordValue = signingValue("storePassword", "P4B_KEYSTORE_PASSWORD")
val keyAliasValue = signingValue("keyAlias", "P4B_KEY_ALIAS")
val keyPasswordValue = signingValue("keyPassword", "P4B_KEY_PASSWORD")
val hasReleaseSigning =
    storeFilePath != null && storePasswordValue != null &&
        keyAliasValue != null && keyPasswordValue != null &&
        file(storeFilePath).exists()

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
        versionCode = 40
        versionName = "1.39"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}

// Fails the build early if a release artifact would go out unsigned.
tasks.register("checkReleaseSigning") {
    doLast {
        if (!hasReleaseSigning) {
            throw GradleException(
                "No release signing configured. Create app/keystore.properties or set " +
                    "P4B_KEYSTORE / P4B_KEYSTORE_PASSWORD / P4B_KEY_ALIAS / P4B_KEY_PASSWORD. " +
                    "See docs/PLAY_STORE_RELEASE.md."
            )
        }
        println("Release signing configured: ${file(storeFilePath!!).name}")
    }
}
