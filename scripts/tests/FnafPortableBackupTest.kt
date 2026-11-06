package com.msa.freedoom.ui.backup

fun main() {
    check(!fnafPortableBackupPreference("base_path"))
    check(!fnafPortableBackupPreference("music_path"))
    check(fnafPortableBackupPreference("vibrate"))
    check(fnafPortableBackupPreference("resolution_height"))
    check(fnafPortableBackupPreference("glow_enabled"))
    println("PASS portable backup: preserve destination sandbox paths, restore other settings")
}
