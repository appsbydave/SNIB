package com.snib.app.recovery

import com.snib.app.data.SessionRepository

/**
 * Layer 4. If a session has run past 24 hours with no token seen, the block screen offers a way
 * out that requires the screen to stay on and untouched for ten minutes. Long enough that nobody
 * uses it out of impatience, reliable enough that the phone can never permanently trap you.
 *
 * Built, but off. Brief says v1.1 unless dogfooding proves it is needed sooner — flipping this
 * constant is the whole change.
 *
 * "Past 24 hours" is measured on the trusted clock (see TrustedClock via
 * [SessionRepository.strandedSinceMs]), so winding the wall clock forward a day does not conjure
 * the escape hatch into existence early.
 */
object StrandedUnlock {

    const val ENABLED = false

    const val STRANDED_AFTER_MS = 24 * 60 * 60 * 1000L
    const val VIGIL_MS = 10 * 60 * 1000L

    fun isStranded(): Boolean {
        if (!ENABLED) return false
        if (!SessionRepository.isSnibbed) return false
        return SessionRepository.strandedSinceMs() >= STRANDED_AFTER_MS
    }

    fun complete() {
        SessionRepository.unsnib(endedBy = "stranded")
    }
}
