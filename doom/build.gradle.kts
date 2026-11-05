plugins { alias(libs.plugins.android.application) }

// One-time small offline updater for the 2026-11-05 Android 14 menu test.
// This branch does not build or alter the game itself.
android {
    enableKotlin = false // This small helper contains Java only.
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

tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        val dir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val old = dir.resolve("doom-debug.apk")
        val named = dir.resolve("FNAF-Menu-Onarici.apk")
        check(old.isFile && old.renameTo(named)) { "Could not name the repair APK" }
    }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

// Isolated on-device integration diagnostic with small test fixtures only.
tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            val p = ProcessBuilder("bash", "scripts/android14-helper-ci-check.sh")
                .directory(rootProject.projectDir).inheritIO().start()
            check(p.waitFor() == 0) { "Android 14 helper integration diagnostic failed" }
        }
    }
}
