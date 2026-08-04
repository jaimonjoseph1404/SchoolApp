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
        versionCode = 4
        versionName = "1.0.3"
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

// AGP's default output filename ("app-debug.apk") is the same for every
// project — indistinguishable from any other app's debug build sitting in
// the same Downloads/outputs folder. Name the actual built artifact itself,
// not just a manually-copied duplicate.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set("SchoolApp-v${android.defaultConfig.versionName}-${variant.name}.apk")
            }
        }
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

    // Live document scanner (Play Services) — real-time framing, auto-focus/
    // auto-capture, edge detection + perspective correction, multi-page
    // capture in one session. Replaces a single static camera photo, which
    // is what was producing low-quality OCR input in the first place.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // On-device AI (LiteRT-LM) — structures OCR'd/photographed report cards
    // into JSON via a local LLM, as a smarter layer on top of (not a
    // replacement for) the regex parser in OcrService.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.15.0")

    testImplementation("junit:junit:4.13.2")
}
