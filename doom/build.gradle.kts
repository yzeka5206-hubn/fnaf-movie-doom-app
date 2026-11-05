plugins { alias(libs.plugins.android.application) }

// One-time small offline updater for the direct-touch test.
// This branch does not build or alter the game itself.
android {
    enableKotlin = false // This small helper contains Java only.
    namespace = "com.fnaf.moviedoom.repair"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fnaf.moviedoom.touchpatch"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
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
        val named = dir.resolve("FNAF-Dokunma-Guncelleyici.apk")
        check(old.isFile && old.renameTo(named)) { "Could not name the repair APK" }
    }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
