package com.msa.freedoom

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.ArrayDeque
import kotlin.math.roundToInt

/** Convert full-screen Android coordinates into the game's 16-bit UI space. */
internal fun fnafTouchCoordinate(position: Float, extent: Int): Int {
    if (extent <= 0 || !position.isFinite()) return 0
    return ((position / extent.toFloat()).coerceIn(0f, 1f) * 65535f).roundToInt()
}

/** Android view pixels -> actual SDL surface pixels; the engine then handles letterboxing. */
internal fun fnafSurfaceCoordinate(position: Float, viewExtent: Int, surfaceExtent: Int): Float {
    if (viewExtent <= 0 || !position.isFinite()) return 0f
    val extent = if (surfaceExtent > 0) surfaceExtent else viewExtent
    return ((position / viewExtent.toFloat()).coerceIn(0f, 1f) * extent)
        .coerceAtMost((extent - 1).coerceAtLeast(0).toFloat())
}

/** Convert a view tap to the logical framebuffer, respecting output letterboxing. */
internal fun fnafLogicalTouch(
    viewX: Float, viewY: Float, viewWidth: Int, viewHeight: Int,
    surfaceWidth: Int, surfaceHeight: Int, screenWidth: Int, screenHeight: Int,
): Pair<Int, Int>? {
    if (!viewX.isFinite() || !viewY.isFinite() || viewWidth <= 0 || viewHeight <= 0 ||
        screenWidth <= 0 || screenHeight <= 0) return null
    val sw = if (surfaceWidth > 0) surfaceWidth.toFloat() else viewWidth.toFloat()
    val sh = if (surfaceHeight > 0) surfaceHeight.toFloat() else viewHeight.toFloat()
    val scale = minOf(sw / screenWidth, sh / screenHeight)
    if (scale <= 0f) return null
    val x = (viewX / viewWidth * sw - (sw - screenWidth * scale) / 2f) / scale
    val y = (viewY / viewHeight * sh - (sh - screenHeight * scale) / 2f) / scale
    if (x < 0f || y < 0f || x >= screenWidth || y >= screenHeight) return null
    return fnafTouchCoordinate(x, screenWidth) to fnafTouchCoordinate(y, screenHeight)
}

internal data class FnafPauseTapAck(val serial: Int, val accepted: Boolean)

/** Back arrow's rectangle in the engine's logical screen coordinates. */
internal data class FnafBackBounds(
    val x: Int, val y: Int, val width: Int, val height: Int,
    val screenWidth: Int, val screenHeight: Int,
) {
    fun contains(viewX: Float, viewY: Float, viewWidth: Int, viewHeight: Int,
                 surfaceWidth: Int, surfaceHeight: Int): Boolean {
        if (width <= 0 || height <= 0 || screenWidth <= 0 || screenHeight <= 0 ||
            viewWidth <= 0 || viewHeight <= 0 || !viewX.isFinite() || !viewY.isFinite()) return false
        val sw = if (surfaceWidth > 0) surfaceWidth.toFloat() else viewWidth.toFloat()
        val sh = if (surfaceHeight > 0) surfaceHeight.toFloat() else viewHeight.toFloat()
        // UZDoom's output fits the logical screen into the surface, centered.
        val scale = minOf(sw / screenWidth, sh / screenHeight)
        if (scale <= 0f) return false
        val left = (sw - screenWidth * scale) / 2f
        val top = (sh - screenHeight * scale) / 2f
        val screenX = (viewX / viewWidth * sw - left) / scale
        val screenY = (viewY / viewHeight * sh - top) / scale
        // A small safety margin prevents rounding at the edge from accidentally
        // sending a normal left-click through the engine's broken arrow path.
        val margin = 2f
        return screenX >= x - margin && screenX < x + width + margin &&
            screenY >= y - margin && screenY < y + height + margin
    }
}

/** Read only this launch's state markers, tolerating partial writes/truncation. */
internal class FnafUiStateReader(private val file: File, private val session: Int) {
    private var offset = 0L
    private var partial = ""
    private val marker = "FNAF_TOUCH_STATE:$session:"
    private val tapMarker = "FNAF_PAUSE_TAP:$session:"
    private val tapAcks = ArrayDeque<FnafPauseTapAck>()
    var backBounds: FnafBackBounds? = null
        private set
    var isPauseMenu: Boolean = false
        private set

    fun drainTapAcks(): List<FnafPauseTapAck> = buildList {
        while (tapAcks.isNotEmpty()) add(tapAcks.removeFirst())
    }

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
                    val tapIndex = line.indexOf(tapMarker)
                    if (tapIndex >= 0) {
                        val values = line.substring(tapIndex + tapMarker.length).trim().split(':')
                        val serial = values.getOrNull(0)?.toIntOrNull()
                        val accepted = values.getOrNull(1)?.toIntOrNull()
                        if (values.size == 2 && serial != null && serial > 0 && (accepted == 0 || accepted == 1)) {
                            if (tapAcks.size >= 16) tapAcks.removeFirst()
                            tapAcks.addLast(FnafPauseTapAck(serial, accepted == 1))
                        }
                    }
                    val index = line.indexOf(marker)
                    if (index < 0) continue
                    val parts = line.substring(index + marker.length).trim().split(':')
                    val mode = parts.firstOrNull()?.toIntOrNull()
                    if (mode != null && mode in 0..2) {
                        newest = mode
                        val values = parts.drop(1).map { it.toIntOrNull() }
                        isPauseMenu = mode == 2 && values.size == 7 && values[6] == 1
                        backBounds = if (mode == 2 && values.size in 6..7 && values.take(6).all { it != null }) {
                            val v = values.take(6).map { it!! }
                            if (v[0] >= 0 && v[1] >= 0 && v[2] >= 0 && v[3] >= 0 &&
                                v[4] > 0 && v[5] > 0 && v[0] + v[2] <= v[4] && v[1] + v[3] <= v[5])
                                FnafBackBounds(v[0], v[1], v[2], v[3], v[4], v[5])
                            else null
                        } else null
                    }
                }
                newest
            }
        } catch (_: IOException) { null }
    }

    companion object { private const val MAX_READ = 64 * 1024L }
}
