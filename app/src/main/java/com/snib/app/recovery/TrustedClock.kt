package com.snib.app.recovery

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * A clock the user cannot wind back.
 *
 * Every timed defence in Snib — the five-minute break, the weekly break allowance, the stranded
 * unlock — used to run on the wall clock. The wall clock is attacker-controlled: wind it back an
 * hour mid-break and the break lasts an hour; wind the date forward a week and three fresh breaks
 * appear. Digital Detox needed several releases of "time meddling protection" for exactly this.
 *
 * Durations are therefore measured on [SystemClock.elapsedRealtime], which counts milliseconds
 * since boot and cannot be set. It resets to zero on every boot, so we also record which boot an
 * anchor was taken in ([Settings.Global.BOOT_COUNT]); a value from an earlier boot is not trusted.
 * Wall-clock time, when we genuinely need it (which ISO week is it), is reconstructed from a
 * trusted anchor rather than read raw.
 *
 * Honest limit, as everywhere in Snib: wind the clock *and* reboot and the anchor is gone, so we
 * fall back to the raw wall clock. That takes deliberate intent, which is the same ceiling as the
 * rest of the product.
 */
object TrustedClock {

    data class Anchor(val wall: Long, val elapsed: Long, val boot: Int)

    /** Capture the current trusted position. Store this when a session starts. */
    fun anchor(context: Context): Anchor =
        Anchor(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(context))

    /** Wall-clock "now", reconstructed from [a] when the anchor is still trustworthy. */
    fun now(context: Context, a: Anchor?): Long =
        project(a, SystemClock.elapsedRealtime(), bootCount(context), System.currentTimeMillis())

    /**
     * Pure, and the only interesting logic here, so it is unit-tested directly.
     *
     * If the anchor is from this boot and elapsed time has moved forward since it was taken, the
     * real wall time is the anchored wall time plus however much elapsed time has passed. If the
     * anchor is missing, from another boot, or elapsed somehow went backwards, we cannot trust it
     * and fall back to the raw wall clock.
     */
    fun project(a: Anchor?, nowElapsed: Long, nowBoot: Int, wallFallback: Long): Long =
        if (a != null && a.boot == nowBoot && nowElapsed >= a.elapsed) {
            a.wall + (nowElapsed - a.elapsed)
        } else {
            wallFallback
        }

    fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
