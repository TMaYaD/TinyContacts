plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
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
