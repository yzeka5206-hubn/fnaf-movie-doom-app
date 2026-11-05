package com.msa.freedoom

import java.io.File

/** Exact bundle from the working v7 build. The loading order is intentional. */
object FnafBundledGame {
    const val IWAD = "FNAFMovie.ipk3"
    const val WAD = "mods/FiveNightsAtFreddysDoomMOVIE.wad"
    const val PK3 = "mods/FiveNightsAtFreddysDoomMOVIE.pk3"
    const val TOUCH = "mods/fnaf-touch-v1.pk3"
    val modPaths = listOf(WAD, PK3, TOUCH)

    private val requiredSizes = linkedMapOf(
        IWAD to 66_904L,
        WAD to 37_464_359L,
        PK3 to 207_026_277L,
        TOUCH to 1800L,
    )

    /** Refuse to start the engine with a missing/partial extraction. Never deletes user files. */
    fun verifyExtracted(baseDir: File): String? {
        for ((relative, size) in requiredSizes) {
            val file = File(baseDir, relative)
            if (!file.isFile || file.length() != size) {
                return "Oyun dosyası eksik veya yarım: ${file.name}. " +
                    "Kayıtlarını yedekleyip uygulamayı yeniden kurman gerekebilir."
            }
        }
        return null
    }
}
