package com.snib.app.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedClockTest {

    private val anchor = TrustedClock.Anchor(wall = 1_000_000L, elapsed = 5_000L, boot = 7)

    @Test fun `reconstructs wall time from elapsed within the same boot`() {
        // 2s of elapsed time passed → wall time advanced by exactly 2s, whatever the raw clock says.
        val now = TrustedClock.project(anchor, nowElapsed = 7_000L, nowBoot = 7, wallFallback = 9_999_999L)
        assertEquals(1_002_000L, now)
    }

    @Test fun `falls back to wall clock after a reboot`() {
        val now = TrustedClock.project(anchor, nowElapsed = 500L, nowBoot = 8, wallFallback = 42L)
        assertEquals(42L, now)
    }

    @Test fun `falls back when there is no anchor`() {
        val now = TrustedClock.project(null, nowElapsed = 7_000L, nowBoot = 7, wallFallback = 42L)
        assertEquals(42L, now)
    }

    @Test fun `falls back if elapsed went backwards`() {
        val now = TrustedClock.project(anchor, nowElapsed = 4_000L, nowBoot = 7, wallFallback = 42L)
        assertEquals(42L, now)
    }
}
