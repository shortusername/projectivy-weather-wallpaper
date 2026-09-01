import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/**
 * Signing.
 *
 * Without this, Gradle generates a throwaway debug keystore whenever one isn't
 * present. CI runners start clean every time, so every build was signed with a
 * different key — and Android refuses to update an app whose signature changed,
 * which is why installs needed uninstalling first.
 *
 * The keystore is supplied by CI through environment variables rather than
 * committed, because this repo is public: anyone holding the key could build an
 * APK that installs over someone's copy of this one.
 *
 * When the variables are absent (a fork, or a local build) it falls back to the
 * normal debug key, so the project still builds for everyone.
 */
// Names deliberately don't match the signing DSL properties (keyAlias,
// keyPassword): shadowing them forces awkward qualified-this references inside
// the config block.
val pinnedStorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")
val pinnedStorePassword: String? = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val pinnedAlias: String? = System.getenv("ANDROID_KEY_ALIAS")
val pinnedAliasPassword: String? = System.getenv("ANDROID_KEY_PASSWORD")
val hasPinnedKey = !pinnedStorePath.isNullOrBlank() && file(pinnedStorePath).exists()

android {
    namespace = libs.versions.appId.get()
    compileSdk = libs.versions.compileSdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = libs.versions.appId.get()
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasPinnedKey) {
            create("pinned") {
                storeFile = file(pinnedStorePath!!)
                storePassword = pinnedStorePassword
                keyAlias = pinnedAlias
                keyPassword = pinnedAliasPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Sideloaded builds are what people actually install, so the debug
            // variant gets the stable key when one is available.
            if (hasPinnedKey) {
                signingConfig = signingConfigs.getByName("pinned")
            }
        }
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasPinnedKey) {
                signingConfig = signingConfigs.getByName("pinned")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":api"))
}
