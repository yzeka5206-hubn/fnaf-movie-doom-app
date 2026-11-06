plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release the optimized package with a stable key held only in CI secrets.
// Local unsigned/test builds may use Android's normal debug key; CI refuses
// to publish unless these values are present and the certificate matches.
val fnafStableStore = System.getenv("FNAF_SIGNING_STORE")
val fnafStablePassword = System.getenv("FNAF_SIGNING_PASSWORD")

android {
    namespace = "com.msa.freedoom"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fnaf.moviedoom"
        minSdk = 33
        targetSdk = 36
        versionCode = 12
        versionName = "0.9.3-optimize-test"
        manifestPlaceholders["fnafAppLabel"] = "FNAF Movie Doom"

        // This animation-test candidate targets the Xiaomi 11T (ARM64).
        // Do not package the old unpatched 32-bit engine under the new shader.
        ndk { abiFilters += listOf("arm64-v8a") }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!fnafStableStore.isNullOrBlank() && !fnafStablePassword.isNullOrBlank()) {
            create("fnafStable") {
                storeFile = file(fnafStableStore)
                storePassword = fnafStablePassword
                keyAlias = "fnaf"
                keyPassword = fnafStablePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // The previous animation-test signing key is unavailable. Never
            // uninstall it: a separate package preserves all old data. This new
            // package uses the stable CI key for subsequent updates.
            applicationIdSuffix = ".optimized"
            manifestPlaceholders["fnafAppLabel"] = "FNAF Optimize Testi"
            if (!fnafStableStore.isNullOrBlank() && !fnafStablePassword.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("fnafStable")
            }
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/libs")
        }
    }

    packaging {
        jniLibs {
            // Store .so uncompressed and page-aligned so they can be mmap'd directly on
            // 16 KB-page devices (Android 15+). Combined with the 16 KB ELF LOAD alignment
            // (see jni/Application.mk), this makes the app 16 KB-compatible.
            useLegacyPackaging = false
        }
    }

    buildFeatures {
        resValues = true
        compose = true
        buildConfig = true // BuildConfig.VERSION_NAME feeds the idgames API User-Agent
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "../libs", "include" to listOf("*.jar"))))
    implementation(project(":touchcontrols"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // PNG->WAD generator (native libpng2wad.so via CMake) used by the map editor.
    implementation(project(":png2wad-sdk"))

    testImplementation(libs.junit)
}
