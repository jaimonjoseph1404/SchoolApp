plugins {
    id("com.android.application") version "8.7.0" apply false
    // Bumped from 2.0.20: com.google.ai.edge.litertlm:litertlm-android's own
    // Kotlin metadata requires a 2.3.0+ compiler to read.
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("com.google.devtools.ksp") version "2.3.0" apply false
}
