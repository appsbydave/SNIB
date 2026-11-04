package com.snib.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The inversion that once locked out SystemUI and the dialler lived right here. These tests exist
 * so it can never come back unnoticed — and so the allowlist can never quietly become a blocklist,
 * or swallow the phone, the clock or the launcher.
 */
class BlockDecisionTest {

    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"
    private val maps = "com.google.android.apps.maps"
    private val dialler = "com.google.android.dialer"
    private val clock = "com.google.android.deskclock"
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val shareSheet = "com.android.intentresolver"

    private val catalog = AppCatalog(
        launchable = setOf(instagram, chrome, maps, dialler, clock),
        floor = setOf("com.snib.app", dialler, clock, "com.android.settings"),
        home = setOf(launcher),
    )
    private val kept = setOf(maps)

    private fun decide(
        pkg: String,
        snibbed: Boolean = true,
        onBreak: Boolean = false,
        allowed: Set<String> = kept,
        apps: AppCatalog = catalog,
    ) = BlockDecision.decide(snibbed, onBreak, pkg, allowed, apps)

    @Test fun `not snibbed lets everything through, the launcher included`() {
        assertEquals(Verdict.ALLOW, decide(instagram, snibbed = false))
        assertEquals(Verdict.ALLOW, decide(launcher, snibbed = false))
    }

    @Test fun `on a break lets everything through`() {
        assertEquals(Verdict.ALLOW, decide(instagram, onBreak = true))
        assertEquals(Verdict.ALLOW, decide(launcher, onBreak = true))
    }

    @Test fun `snibbed blocks an app that isn't kept`() {
        assertEquals(Verdict.BLOCK, decide(instagram))
        assertEquals(Verdict.BLOCK, decide(chrome))
    }

    @Test fun `snibbed lets a kept app through`() {
        assertEquals(Verdict.ALLOW, decide(maps))
    }

    @Test fun `the launcher gets the snibbed home screen, not a block and not a free pass`() {
        assertEquals(Verdict.HOME, decide(launcher))
    }

    @Test fun `the floor is never blocked, even when nothing is kept`() {
        assertEquals(Verdict.ALLOW, decide(dialler, allowed = emptySet()))
        assertEquals(Verdict.ALLOW, decide(clock, allowed = emptySet()))
        assertEquals(Verdict.ALLOW, decide("com.android.settings", allowed = emptySet()))
        assertEquals(Verdict.ALLOW, decide("com.snib.app", allowed = emptySet()))
    }

    @Test fun `a launcher in the floor is treated as the floor`() {
        // Settings' fallback home declares HOME. It must stay usable, not be covered.
        val apps = catalog.copy(home = setOf(launcher, "com.android.settings"))
        assertEquals(Verdict.ALLOW, decide("com.android.settings", apps = apps))
    }

    @Test fun `system plumbing with no launcher icon is never blocked`() {
        assertEquals(Verdict.ALLOW, decide(shareSheet, allowed = emptySet()))
        assertEquals(Verdict.ALLOW, decide("com.android.systemui", allowed = emptySet()))
    }

    @Test fun `an empty allowlist blocks everything with an icon outside the floor`() {
        assertEquals(Verdict.BLOCK, decide(maps, allowed = emptySet()))
        assertEquals(Verdict.BLOCK, decide(instagram, allowed = emptySet()))
    }

    @Test fun `an unknown app list fails open`() {
        // If the package manager couldn't be read, Snib must not guess.
        val unknown = AppCatalog.EMPTY
        assertEquals(Verdict.ALLOW, decide(instagram, apps = unknown))
        assertEquals(Verdict.ALLOW, decide(launcher, apps = unknown))
    }
}
