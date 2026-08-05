package com.snib.app.household

import android.content.Context
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The household secret. Generated on this phone at first setup, or received from a token when
 * joining someone else's household. No account, no email, no server.
 */
object HouseholdKey {

    private const val SECRET_NAME = "household.secret"
    const val SECRET_BYTES = 32
    const val HOUSEHOLD_ID_BYTES = 16

    private const val ID_CONTEXT = "snib/household-id/v1"

    @Volatile private var cached: ByteArray? = null

    fun exists(context: Context): Boolean = secretOrNull(context) != null

    fun secretOrNull(context: Context): ByteArray? =
        cached ?: KeystoreVault.open(context, SECRET_NAME)?.also { cached = it }

    fun secret(context: Context): ByteArray =
        secretOrNull(context) ?: error("No household on this phone yet")

    /** First setup: this phone starts a household. */
    fun create(context: Context): ByteArray {
        val secret = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }
        adopt(context, secret)
        return secret
    }

    /** Joining: this phone takes on a household secret carried by a token. */
    fun adopt(context: Context, secret: ByteArray) {
        require(secret.size == SECRET_BYTES) { "Bad household secret length" }
        KeystoreVault.seal(context, SECRET_NAME, secret)
        cached = secret
    }

    fun forget(context: Context) {
        KeystoreVault.clear(context, SECRET_NAME)
        cached = null
    }

    /** Stable, non-secret identifier derived from the secret. Used for a fast "is this ours". */
    fun householdId(secret: ByteArray): ByteArray =
        hmac(secret, ID_CONTEXT.toByteArray()).copyOf(HOUSEHOLD_ID_BYTES)

    fun hmac(secret: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message)
    }

    /** Constant-time. Do not replace with contentEquals on a hot comparison of secrets. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
