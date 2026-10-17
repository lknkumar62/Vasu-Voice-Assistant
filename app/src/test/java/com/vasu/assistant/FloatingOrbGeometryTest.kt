package com.vasu.assistant

import com.vasu.assistant.ui.overlay.FloatingOrbGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/** FIX-9: pure geometry for the floating orb (drag clamp + edge snap). */
class FloatingOrbGeometryTest {

    @Test
    fun `clamp keeps the orb fully on screen`() {
        assertEquals(0, FloatingOrbGeometry.clamp(-50, 1080, 200))
        assertEquals(880, FloatingOrbGeometry.clamp(2000, 1080, 200))
        assertEquals(430, FloatingOrbGeometry.clamp(430, 1080, 200))
        assertEquals(0, FloatingOrbGeometry.clamp(100, 150, 200)) // screen smaller than orb
    }

    @Test
    fun `snapX snaps to the nearest edge`() {
        assertEquals(0, FloatingOrbGeometry.snapX(10, 1080, 200))
        assertEquals(0, FloatingOrbGeometry.snapX(440, 1080, 200))   // centre-left goes left
        assertEquals(880, FloatingOrbGeometry.snapX(460, 1080, 200)) // centre-right goes right
        assertEquals(880, FloatingOrbGeometry.snapX(1000, 1080, 200))
        assertEquals(0, FloatingOrbGeometry.snapX(-5, 1080, 200))
    }
}
