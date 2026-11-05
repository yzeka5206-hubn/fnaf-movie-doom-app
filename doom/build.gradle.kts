plugins { alias(libs.plugins.android.application) }

// One-time small offline updater for the pause menu and partial animation test.
// This branch does not build or alter the game itself.
android {
    enableKotlin = false // This small helper contains Java only.
    namespace = "com.fnaf.moviedoom.repair"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fnaf.moviedoom.pausepatch"
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
        val named = dir.resolve("FNAF-Menu-Animasyon-Guncelleyici.apk")
        check(old.isFile && old.renameTo(named)) { "Could not name the repair APK" }
    }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

// Isolated CI branch: test the exact released updater on Android 14.
tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            val log = rootProject.file("android14-pause-helper-ci-output.txt")
            val process = ProcessBuilder("bash", "scripts/android14-pause-helper-ci.sh")
                .directory(rootProject.projectDir).redirectErrorStream(true).redirectOutput(log).start()
            val result = process.waitFor()
            println("\n====== ANDROID 14 UPDATER CHECK ======\n" + log.readText())
            check(result == 0) { "Android 14 updater integration failed (exit $result)" }
        }
    }
}
