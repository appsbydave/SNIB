package com.snib.app.data

/**
 * What Snib knows about the apps on this phone, resolved at runtime. Pure data so the decision can
 * be unit-tested on the JVM with no Android around it.
 *
 * @property launchable every package with a launcher icon. Only these are ever blocked.
 * @property floor never blocked, whatever the allowlist says: Snib, Settings, the dialler and
 *   in-call screen, Messages, the clock (an alarm must always be dismissable), emergency apps and
 *   the keyboard.
 * @property home the launcher packages. On a Pixel the launcher also hosts recents and the app
 *   drawer, so while snibbed these get the snibbed home screen drawn over them.
 */
data class AppCatalog(
    val launchable: Set<String>,
    val floor: Set<String>,
    val home: Set<String>,
) {
    companion object {
        val EMPTY = AppCatalog(emptySet(), emptySet(), emptySet())
    }
}

enum class Verdict {
    /** Let it through. */
    ALLOW,
    /** Cover it with the block screen. */
    BLOCK,
    /** It's the launcher: cover it with the snibbed home screen, which lists the kept apps. */
    HOME,
}

/**
 * The single rule for what happens to a package that is in front right now.
 *
 * This is an **allowlist**. While snibbed, every app with a launcher icon is blocked unless it is
 * one of the (up to five) kept apps or in the protected floor. That is what makes swiping up to
 * the app drawer pointless: whatever you tap there is blocked, and the drawer itself is covered.
 *
 * The rule, in order:
 *   1. Not snibbed, or on a break → ALLOW.
 *   2. App list unknown           → ALLOW. Fail open: if we can't see the phone's apps, block
 *                                   nothing rather than guess. An empty allowlist is fine — the
 *                                   floor still keeps the phone, messages, clock and Settings.
 *   3. In the floor               → ALLOW.
 *   4. A launcher                 → HOME.
 *   5. Kept by the user           → ALLOW.
 *   6. Has a launcher icon        → BLOCK.
 *   7. Anything else              → ALLOW. System plumbing with no icon: the share sheet,
 *                                   permission dialogs, the package installer, sign-in screens.
 *
 * An inversion here is the most dangerous bug in the app — the August code once treated the
 * blocklist as an allowlist and locked out SystemUI, the launcher and the dialler — so every
 * branch is pinned by BlockDecisionTest.
 */
object BlockDecision {

    fun decide(
        snibbed: Boolean,
        onBreak: Boolean,
        packageName: String,
        allowed: Set<String>,
        catalog: AppCatalog,
    ): Verdict = when {
        !snibbed || onBreak -> Verdict.ALLOW
        catalog.launchable.isEmpty() -> Verdict.ALLOW
        packageName in catalog.floor -> Verdict.ALLOW
        packageName in catalog.home -> Verdict.HOME
        packageName in allowed -> Verdict.ALLOW
        packageName in catalog.launchable -> Verdict.BLOCK
        else -> Verdict.ALLOW
    }
}
