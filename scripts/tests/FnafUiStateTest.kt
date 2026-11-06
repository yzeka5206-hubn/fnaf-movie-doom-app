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
        file.appendText("FNAF_TOUCH_STATE:87654:2:0:0:48:39:960:540\n")
        check(reader.poll() == 2)
        val bounds = reader.backBounds!!
        check(!reader.isPauseMenu) // old marker remains backwards compatible
        file.appendText("FNAF_TOUCH_STATE:87654:2:0:0:48:39:960:540:1\n")
        file.appendText("FNAF_PAUSE_TAP:111:1:1\nFNAF_PAUSE_TAP:87654:7:")
        check(reader.poll() == 2 && reader.isPauseMenu && reader.drainTapAcks().isEmpty())
        file.appendText("1\nFNAF_PAUSE_TAP:87654:8:0\nFNAF_PAUSE_TAP:87654:-1:1\n")
        check(reader.poll() == null)
        check(reader.drainTapAcks() == listOf(FnafPauseTapAck(7, true), FnafPauseTapAck(8, false)))
        check(reader.drainTapAcks().isEmpty())
        check(bounds.contains(45f, 20f, 960, 540, 960, 540))
        check(!bounds.contains(100f, 20f, 960, 540, 960, 540))
        // 16:9 game letterboxed horizontally in a 20:9 Android surface.
        check(bounds.contains(140f, 20f, 1200, 540, 1200, 540))
        check(!bounds.contains(20f, 20f, 1200, 540, 1200, 540))
        check(FnafBackBounds(912, 501, 48, 39, 960, 540).contains(1060f, 520f, 1200, 540, 1200, 540))
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
        check(fnafSurfaceCoordinate(50f, 100, 40) == 20f)
        check(fnafSurfaceCoordinate(100f, 100, 40) == 39f)
        check(fnafSurfaceCoordinate(-1f, 100, 40) == 0f)
        check(fnafSurfaceCoordinate(50f, 100, 0) == 50f)
        check(fnafSurfaceCoordinate(Float.NaN, 100, 40) == 0f)
        check(fnafLogicalTouch(600f, 270f, 1200, 540, 1200, 540, 960, 540) == (32768 to 32768))
        check(fnafLogicalTouch(10f, 270f, 1200, 540, 1200, 540, 960, 540) == null)
        check(fnafLogicalTouch(600f, 270f, 1200, 540, 600, 270, 960, 540) == (32768 to 32768))
        check(fnafLogicalTouch(Float.NaN, 0f, 1200, 540, 1200, 540, 960, 540) == null)
        // Xiaomi 11T surface and the explicitly selected scaled buffers.
        check(fnafLogicalTouch(1158f, 540f, 2316, 1080, 2316, 1080, 1028, 480) == (32768 to 32768))
        check(fnafLogicalTouch(1158f, 540f, 2316, 1080, 2316, 1080, 1544, 720) == (32768 to 32768))
        println("PASS session-specific state/ack reader, 480p/720p touch coordinates, letterboxing and bounded tail")
    } finally { dir.deleteRecursively() }
}
