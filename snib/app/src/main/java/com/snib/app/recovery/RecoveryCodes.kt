package com.snib.app.recovery

import android.content.Context
import com.snib.app.data.RecoveryCodeEntity
import com.snib.app.data.SessionRepository
import com.snib.app.household.HouseholdKey
import com.snib.app.household.TokenCodec.toHex
import java.security.SecureRandom

/**
 * Eight single-use codes, generated at setup and shown exactly once.
 *
 * This is the wallet-card answer to "token's at home, I'm in Glasgow, I need my banking app."
 * Offline, no server, and unreachable from inside a snibbed phone by accident.
 *
 * Stored as HMAC(household secret, code) — a stolen database gives up nothing usable, and the
 * codes cannot be recovered by us either, which is why the setup flow makes you write them down.
 */
object RecoveryCodes {

    const val COUNT = 8
    private const val GROUPS = 2
    private const val GROUP_LEN = 4

    /** Crockford base32: no I, L, O or U. Nothing that a person can mis-copy off a card. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun generate(context: Context): List<String> {
        val secret = HouseholdKey.secret(context)
        val random = SecureRandom()
        val codes = List(COUNT) {
            (0 until GROUPS).joinToString("-") {
                (0 until GROUP_LEN)
                    .map { ALPHABET[random.nextInt(ALPHABET.length)] }
                    .joinToString("")
            }
        }
        val now = System.currentTimeMillis()
        val dao = SessionRepository.dao
        dao.clearCodes()
        dao.putCodes(codes.map { RecoveryCodeEntity(hash = hash(secret, it), issuedAt = now) })
        return codes
    }

    /** Consumes a code and unsnibs. Returns false for a wrong or already-spent code. */
    fun redeem(context: Context, entered: String): Boolean {
        val secret = HouseholdKey.secretOrNull(context) ?: return false
        val normalised = normalise(entered)
        if (normalised.isBlank()) return false
        val spent = SessionRepository.dao.spendCode(
            hash = hash(secret, normalised),
            now = System.currentTimeMillis(),
        )
        if (spent == 0) return false
        SessionRepository.unsnib(endedBy = "recovery-code")
        return true
    }

    fun canRegenerate(): Boolean = !SessionRepository.isSnibbed

    private fun normalise(raw: String): String {
        val cleaned = raw.uppercase().filter { it in ALPHABET }
        if (cleaned.length != GROUPS * GROUP_LEN) return ""
        return cleaned.chunked(GROUP_LEN).joinToString("-")
    }

    private fun hash(secret: ByteArray, code: String): String =
        HouseholdKey.hmac(secret, ("snib/recovery-code/v1:$code").toByteArray()).toHex()
}
