pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TinyContacts"

// The pure-JVM expiry policy module is ALWAYS included: it carries the sync1<now
// selection logic (deliverable #3) and its unit tests run headless with no Android
// SDK — exactly what CI / this sandbox can execute.
include(":expiry-policy")

// The Android :app module is only wired in when an Android SDK is actually available.
// Configuring `com.android.application` requires the SDK; in an SDK-less environment
// (e.g. a headless sandbox where dl.google.com is blocked) we skip it so that
// `./gradlew :expiry-policy:test` still works instead of failing at configuration time.
val hasAndroidSdk =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (hasAndroidSdk) {
    include(":app")
} else {
    gradle.rootProject {
        logger.warn(
            "TinyContacts: no Android SDK detected (ANDROID_HOME / local.properties absent). " +
                "Skipping the :app module; only :expiry-policy is configured. " +
                "Set up the SDK to build the Android app with ./gradlew assembleDebug.",
        )
    }
}
