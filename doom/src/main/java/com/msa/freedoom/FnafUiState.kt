package com.msa.freedoom

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Convert full-screen Android coordinates into the game's 16-bit UI space. */
internal fun fnafTouchCoordinate(position: Float, extent: Int): Int {
    if (extent <= 0 || !position.isFinite()) return 0
    return ((position / extent.toFloat()).coerceIn(0f, 1f) * 65535f).roundToInt()
}

/** Read only this launch's state markers, tolerating partial writes/truncation. */
internal class FnafUiStateReader(private val file: File, private val session: Int) {
    private var offset = 0L
    private var partial = ""
    private val marker = "FNAF_TOUCH_STATE:$session:"

    fun poll(): Int? {
        return try {
            if (!file.isFile) return null
            RandomAccessFile(file, "r").use { input ->
                val size = input.length()
                if (size < offset) { offset = 0; partial = "" }
                if (size - offset > MAX_READ) { offset = size - MAX_READ; partial = "" }
                val count = (size - offset).toInt()
                if (count <= 0) return null
                input.seek(offset)
                val bytes = ByteArray(count)
                input.readFully(bytes)
                offset += count
                val lines = (partial + String(bytes, Charsets.UTF_8)).split('\n')
                partial = lines.last().takeLast(256)
                var newest: Int? = null
                for (line in lines.dropLast(1)) {
                    val index = line.indexOf(marker)
                    if (index < 0) continue
                    val mode = line.substring(index + marker.length).trim().toIntOrNull()
                    if (mode != null && mode in 0..2) newest = mode
                }
                newest
            }
        } catch (_: IOException) { null }
    }

    companion object { private const val MAX_READ = 64 * 1024L }
}
