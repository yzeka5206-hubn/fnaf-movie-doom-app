package com.msa.freedoom

/** A physical hold represented as a tap-on/tap-off switch in the overlay. */
internal class LatchingSwitch {
    var active: Boolean = false
        private set

    fun toggle(): Boolean {
        active = !active
        return active
    }

    fun clear() {
        active = false
    }
}

/** Camera direction correction; cursor mode deliberately uses raw deltas instead. */
internal fun orientCameraDelta(delta: Float, reverse: Boolean): Float =
    if (reverse) -delta else delta
