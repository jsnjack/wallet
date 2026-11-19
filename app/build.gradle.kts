plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

android {
    namespace = "io.github.jsnjack.wallet"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.jsnjack.wallet"
        // Android 13 for sensitive clipboard content, typed intent extras and
        // predictive back. Built for a Galaxy on One UI 9 (Android 17), with no
        // version-guard shims for older releases.
        minSdk = 33
        targetSdk = 37
        // Stamped by the Makefile from monova (-PappVersion=x.y.z); the code
        // is derived so every release installs over the previous one.
        val appVersion = (project.findProperty("appVersion") as String?)
            ?.takeIf { it.isNotBlank() && it != "0.0.0" } ?: "0.0.1"
        val (major, minor, patch) = appVersion.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        versionCode = maxOf(1, major * 1_000_000 + minor * 1_000 + patch)
        versionName = appVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.camera:camera-camera2:1.7.0")
    implementation("androidx.camera:camera-lifecycle:1.7.0")
    implementation("androidx.camera:camera-view:1.7.0")
    // Reads codes from camera frames and images; open source and offline.
    implementation("io.github.zxing-cpp:android:3.1.1")
    // Draws codes in every supported format.
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // AuthorizationClient: the Google Drive app-folder permission.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
