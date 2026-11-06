package com.msa.freedoom.ui.backup

/** Paths belong to a particular Android app sandbox; keep the destination's. */
internal fun fnafPortableBackupPreference(key: String): Boolean =
    key != "base_path" && key != "music_path"
