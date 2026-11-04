package com.snib.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table. This is the fact that matters: is the phone snibbed right now.
 * Written synchronously on every state change so a reboot cannot lose it.
 *
 * Timing is split deliberately. [snibbedAt] is a wall-clock stamp kept only so the home screen
 * and the notification chronometer can show "snibbed for 2h 14m" — it is display, never a
 * decision. Every *decision* about a break runs on [breakStartedElapsed] / [breakBoot], which the
 * user cannot tamper with (see TrustedClock). The anchor fields let us reconstruct a trusted wall
 * clock for the weekly break reset and the stranded unlock.
 */
@Entity(tableName = "session_state")
data class SessionStateEntity(
    @PrimaryKey val id: Int = 1,
    /** Wall clock at snib. null = unsnibbed. Display only. */
    val snibbedAt: Long? = null,
    /** Last time a valid household token was read. Feeds the stranded unlock. */
    val lastTokenSeenAt: Long? = null,

    // --- trusted-clock anchor, captured at snib (see TrustedClock) ---
    val anchorWall: Long? = null,
    val anchorElapsed: Long? = null,
    val anchorBoot: Int = -1,

    // --- emergency break, timed on elapsedRealtime so the clock cannot extend it ---
    /** elapsedRealtime() when the current break started, or null if not on a break. */
    val breakStartedElapsed: Long? = null,
    /** BOOT_COUNT when the break started. A reboot ends the break — that is fine, it is five minutes. */
    val breakBoot: Int = -1,
)

/**
 * The apps kept while snibbed — at most [SessionRepository.MAX_ALLOWED]. Everything else with a
 * launcher icon is blocked. Replaced the August blocklist in schema v3.
 */
@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
)

/** Completed sessions. Kept only so unsnibbing can say "Snibbed for 2h 14m." No charts. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val endedBy: String,
)

@Entity(tableName = "breaks")
data class BreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    /** ISO week, e.g. "2026-W32". Resets Monday 00:00 by definition. */
    val weekKey: String,
)

@Entity(tableName = "recovery_codes")
data class RecoveryCodeEntity(
    @PrimaryKey val hash: String,
    val issuedAt: Long,
    val usedAt: Long? = null,
)

@Entity(tableName = "tokens")
data class TokenEntity(
    @PrimaryKey val tokenId: String,
    val label: String,
    val addedAt: Long,
)
