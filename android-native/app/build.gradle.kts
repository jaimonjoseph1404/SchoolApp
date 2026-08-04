import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "org.familytools.educationtracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.familytools.educationtracker"
        minSdk = 24
        targetSdk = 34
        // Bump the 3rd (patch) digit of versionName, and versionCode by 1,
        // on every build handed off for install — not on every incidental
        // Gradle invocation during dev/test.
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Settings persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Biometric auth (fingerprint/face) — the native capability Kivy couldn't reach
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.4")

    // On-device OCR — replaces Tesseract, no bundled binary needed
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Camera capture (system camera app via intent) + gallery/file picking
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // On-device AI (LiteRT-LM) — structures OCR'd/photographed report cards
    // into JSON via a local LLM, as a smarter layer on top of (not a
    // replacement for) the regex parser in OcrService.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.15.0")

    testImplementation("junit:junit:4.13.2")
}
