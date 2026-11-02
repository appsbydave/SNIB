package com.snib.app.recovery

import com.snib.app.data.SnibDao
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * Three per calendar week, five minutes each, resetting Monday 00:00.
 *
 * Never sell extra breaks, never trade an ad for one, never allow an extension.
 * The scarcity is the product.
 */
object Breaks {

    const val ALLOWANCE = 3
    const val DURATION_MS = 5 * 60 * 1000L

    fun weekKey(now: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val fields = WeekFields.ISO   // ISO weeks begin Monday, which is the reset we promised
        return "%d-W%02d".format(
            date.get(fields.weekBasedYear()),
            date.get(fields.weekOfWeekBasedYear()),
        )
    }

    fun remaining(dao: SnibDao, now: Long = System.currentTimeMillis()): Int =
        (ALLOWANCE - dao.breaksTakenNow(weekKey(now))).coerceAtLeast(0)

    /** "This uses one of three. Two left until Monday." */
    fun costSentence(remainingBefore: Int): String {
        val left = (remainingBefore - 1).coerceAtLeast(0)
        val leftWord = when (left) {
            0 -> "None"
            1 -> "One"
            2 -> "Two"
            else -> left.toString()
        }
        return "This uses one of $ALLOWANCE. $leftWord left until Monday."
    }
}
