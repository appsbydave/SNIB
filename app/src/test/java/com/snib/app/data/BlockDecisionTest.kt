package com.snib.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inversion that once locked out SystemUI and the dialler lived right here. These tests exist
 * so it can never come back unnoticed.
 */
class BlockDecisionTest {

    private val blocked = setOf("com.instagram.android", "com.twitter.android")

    @Test fun `not snibbed blocks nothing`() {
        assertFalse(BlockDecision.shouldBlock(snibbed = false, onBreak = false, "com.instagram.android", blocked))
    }

    @Test fun `on a break blocks nothing`() {
        assertFalse(BlockDecision.shouldBlock(snibbed = true, onBreak = true, "com.instagram.android", blocked))
    }

    @Test fun `snibbed blocks a listed app`() {
        assertTrue(BlockDecision.shouldBlock(snibbed = true, onBreak = false, "com.instagram.android", blocked))
    }

    @Test fun `snibbed lets an unlisted app through`() {
        assertFalse(BlockDecision.shouldBlock(snibbed = true, onBreak = false, "com.android.dialer", blocked))
    }

    @Test fun `an empty blocklist never locks the phone`() {
        assertFalse(BlockDecision.shouldBlock(snibbed = true, onBreak = false, "com.android.systemui", emptySet()))
        assertFalse(BlockDecision.shouldBlock(snibbed = true, onBreak = false, "com.instagram.android", emptySet()))
    }
}
