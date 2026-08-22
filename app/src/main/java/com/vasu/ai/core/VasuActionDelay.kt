package com.vasu.ai.core

import android.os.SystemClock

/** Bounded, interruptible delay used only between autonomous UI actions. */
object VasuActionDelay {
    fun pause(milliseconds: Long): Boolean {
        val duration = milliseconds.coerceIn(50L, 1500L)
        return try {
            SystemClock.sleep(duration)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
