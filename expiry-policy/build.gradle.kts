plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a plain Kotlin/JVM module — NO Android plugin, NO Android dependencies.
// This keeps the expiry selection policy pure and lets its tests run on any JVM
// (CI, this headless sandbox) without an Android SDK or emulator.
dependencies {
    testImplementation(libs.junit)
}

// No jvmToolchain pin here on purpose: this module must compile and test with whatever
// JDK runs Gradle (CI or a headless sandbox), so we don't force a toolchain download.
// The Android :app module pins JVM 17 via its own compileOptions.
