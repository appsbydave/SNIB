package com.snib.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table. This is the fact that matters: is the phone snibbed right now.
 * Written synchronously on every state change so a reboot cannot lose it.
 */
@Entity(tableName = "session_state")
data class SessionStateEntity(
    @PrimaryKey val id: Int = 1,
    /** null = unsnibbed. */
    val snibbedAt: Long? = null,
    /** Non-null while an emergency break is running. Blocking resumes when the clock passes it. */
    val breakUntil: Long? = null,
    /** Last time a valid household token was read. Feeds the stranded unlock. */
    val lastTokenSeenAt: Long? = null,
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
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
