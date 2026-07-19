plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

android {
    namespace = "dev.loonybin.tempcontacts"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.loonybin.tempcontacts"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // The pure-JVM expiry selection policy (sync1<now) lives in its own module so its
    // tests run without an Android SDK. The Android worker delegates to it.
    implementation(project(":expiry-policy"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.work.runtime.ktx)

    // NOTE: vestrel00/contacts-android is the recommended library for ContactsContract batching
    // against an arbitrary account, but it deliberately does no syncing and does not surface
    // RawContacts.sync1 nor the CALLER_IS_SYNCADAPTER URI flag — both non-negotiable here. So
    // the sync-critical CRUD uses ContentResolver directly in ContactsContractRepository.
    // See README ("Why not the vestrel00 library everywhere?") for the full rationale.

    testImplementation(libs.junit)
}

// Publishes the built APK to the GitHub Packages Maven registry as a versioned artifact.
// GitHub Packages has no native "APK" type, so the APK is attached to a Maven publication
// (temp-contacts-<version>.apk + a generated POM). Driven by .github/workflows/publish-apk.yml
// on every merged PR.
publishing {
    publications {
        register<MavenPublication>("apk") {
            groupId = "dev.loonybin.tempcontacts"
            artifactId = "temp-contacts"
            // CI passes -PappVersion=1.0.<run_number> so each merge publishes a unique version
            // (GitHub Packages rejects re-publishing an existing release version). Falls back to
            // a SNAPSHOT for local runs, which may be overwritten freely.
            version = (project.findProperty("appVersion") as String?) ?: "${android.defaultConfig.versionName}-SNAPSHOT"

            // Publish the debug APK: it is signed with the debug keystore, so CI needs no signing
            // secrets. Switch to outputs/apk/release + a signingConfig for a release-signed build.
            artifact(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")) {
                extension = "apk"
                builtBy("assembleDebug")
            }

            pom { packaging = "apk" }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/TMaYaD/TinyContacts")
            credentials {
                // Supplied by the Actions runner (github.actor + the automatic GITHUB_TOKEN).
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
