package com.msa.freedoom

import com.msa.freedoom.ui.launch.ModEntry
import com.msa.freedoom.ui.launch.WadEntry
import com.msa.freedoom.ui.launch.buildLaunchArgs
import com.msa.freedoom.ui.launch.buildModArgs
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

fun main() {
    val dir = Files.createTempDirectory("fnaf-menu-test").toFile()
    fun sparse(relative: String, size: Long) {
        val file = File(dir, relative)
        file.parentFile!!.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(size) }
    }
    try {
        check(FnafBundledGame.verifyExtracted(dir)?.contains("FNAFMovie.ipk3") == true)
        sparse(FnafBundledGame.IWAD, 66_904)
        sparse(FnafBundledGame.WAD, 37_464_359)
        sparse(FnafBundledGame.PK3, 207_026_277 - 1)
        check(FnafBundledGame.verifyExtracted(dir)?.contains("FiveNightsAtFreddysDoomMOVIE.pk3") == true)
        check(File(dir, FnafBundledGame.PK3).length() == 207_026_276L) // verification never deletes
        sparse(FnafBundledGame.PK3, 207_026_277)
        check(FnafBundledGame.verifyExtracted(dir)?.contains("fnaf-touch-v1.pk3") == true)
        val touchAsset = File("doom/src/main/assets/game/fnaf-touch-v1.pk3")
        check(touchAsset.isFile && touchAsset.length() > 0)
        File(dir, FnafBundledGame.TOUCH).writeBytes(touchAsset.readBytes())
        check(FnafBundledGame.verifyExtracted(dir)?.contains("fnaf-model-static-v1.pk3") == true)
        val modelAsset = File("doom/src/main/assets/game/fnaf-model-static-v1.pk3")
        check(modelAsset.isFile && modelAsset.length() > 0)
        sparse(FnafBundledGame.STATIC_MODELS, modelAsset.length() - 1)
        check(FnafBundledGame.verifyExtracted(dir)?.contains("fnaf-model-static-v1.pk3") == true)
        File(dir, FnafBundledGame.STATIC_MODELS).writeBytes(modelAsset.readBytes())
        check(FnafBundledGame.verifyExtracted(dir) == null)
        val mods = buildModArgs(FnafBundledGame.modPaths.map(::ModEntry))
        check(mods == "-file ${FnafBundledGame.WAD} -file ${FnafBundledGame.PK3} -file ${FnafBundledGame.TOUCH} -file ${FnafBundledGame.STATIC_MODELS} ")
        val args = buildLaunchArgs(WadEntry(FnafBundledGame.IWAD, 66_904).iwadArgs, mods, "", "/game/config")
        check(args.startsWith("-iwad FNAFMovie.ipk3 -file mods/FiveNightsAtFreddysDoomMOVIE.wad -file mods/FiveNightsAtFreddysDoomMOVIE.pk3 -file mods/fnaf-touch-v1.pk3 -file mods/fnaf-model-static-v1.pk3 "))
        check(args.contains("-savedir /game/config/gzdoom_saves"))
        check(args.contains("+set fluid_patchset gzdoom.sf2"))
        println("PASS missing/truncated game data refused without deletion; real WAD → PK3 order and v7 savedir retained")
    } finally { dir.deleteRecursively() }
}
