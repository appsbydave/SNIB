package com.snib.app.data

/**
 * The single rule for whether a package should be blocked right now, pulled out of
 * [SessionRepository] so it can be unit-tested on the JVM with no Android around it.
 *
 * An inversion here is the most dangerous bug in the app — the August code once treated the
 * blocklist as an allowlist and locked out SystemUI, the launcher and the dialler — so this is
 * the highest-value thing to pin down with tests.
 *
 * The rule, in order:
 *   1. Not snibbed  → nothing is blocked.
 *   2. On a break   → nothing is blocked (the break is the whole point).
 *   3. Otherwise    → blocked iff the package is on the blocklist.
 *
 * Protected packages (launcher, dialler, Settings, Snib itself) are never placed on the blocklist
 * in the first place, so they are handled upstream, not here.
 */
object BlockDecision {

    fun shouldBlock(
        snibbed: Boolean,
        onBreak: Boolean,
        packageName: String,
        blockedPackages: Set<String>,
    ): Boolean {
        if (!snibbed) return false
        if (onBreak) return false
        return packageName in blockedPackages
    }
}
