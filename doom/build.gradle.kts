plugins { alias(libs.plugins.android.application) }

// One-time small offline updater for the 2026-11-05 Android 14 menu test.
// This branch does not build or alter the game itself.
android {
    namespace = "com.fnaf.moviedoom.repair"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fnaf.moviedoom.repair"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    sourceSets.getByName("main") {
        manifest.srcFile("repair/AndroidManifest.xml")
        java.setSrcDirs(listOf("repair/java"))
        assets.setSrcDirs(listOf("repair/assets"))
        res.setSrcDirs(emptyList<String>())
        jniLibs.setSrcDirs(emptyList<String>())
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("assembleDebug") {
    doLast {
        val dir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val old = dir.resolve("doom-debug.apk")
        val named = dir.resolve("FNAF-Menu-Onarici.apk")
        check(old.isFile && old.renameTo(named)) { "Could not name the repair APK" }
    }
}
