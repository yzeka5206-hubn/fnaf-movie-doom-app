package com.msa.freedoom

import java.io.File
import java.nio.file.Files

fun main() {
    val dir = Files.createTempDirectory("fnaf-ui-reader").toFile()
    try {
        val file = File(dir, "engine.log")
        val reader = FnafUiStateReader(file, 87654)
        check(reader.poll() == null)
        file.writeText("old FNAF_TOUCH_STATE:12345:1\nFNAF_TOUCH_STATE:87654:")
        check(reader.poll() == null)
        file.appendText("1\n")
        check(reader.poll() == 1)
        check(reader.poll() == null)
        file.appendText("FNAF_TOUCH_STATE:87654:7\nFNAF_TOUCH_STATE:87654:2\nFNAF_TOUCH_STATE:87654:0\n")
        check(reader.poll() == 0)
        file.writeText("FNAF_TOUCH_STATE:87654:2\n")
        check(reader.poll() == 2)
        file.appendText("x".repeat(70_000) + "\nFNAF_TOUCH_STATE:87654:1\n")
        check(reader.poll() == 1)
        check(fnafTouchCoordinate(-10f, 100) == 0)
        check(fnafTouchCoordinate(50f, 100) == 32768)
        check(fnafTouchCoordinate(200f, 100) == 65535)
        check(fnafTouchCoordinate(Float.NaN, 100) == 0)
        check(fnafTouchCoordinate(10f, 0) == 0)
        println("PASS session-specific state reader, partial lines, truncation, bounded tail and normalized coordinates")
    } finally { dir.deleteRecursively() }
}
