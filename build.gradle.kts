// Root build script intentionally declares no plugins.
//
// Plugin versions are centralized in gradle/libs.versions.toml; each module applies the
// plugins it needs via the catalog aliases (e.g. `alias(libs.plugins.android.application)`).
// Keeping the Android Gradle Plugin *out* of the root means an SDK-less build (which excludes
// the :app module in settings.gradle.kts) never tries to resolve AGP from the google() repo —
// so `./gradlew :expiry-policy:test` runs cleanly with just a JVM.
