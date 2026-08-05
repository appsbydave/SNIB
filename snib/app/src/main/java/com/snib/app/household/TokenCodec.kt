package com.snib.app.household

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * The wire format of a Snib token.
 *
 *   magic       4   "SNIB"
 *   version     1   0x01
 *   householdId 16  HMAC(secret, "snib/household-id/v1") truncated
 *   tokenId     8   random per token, so "the one by the bed" is a distinguishable object
 *   secret      32  the household secret
 *   mac         32  HMAC(secret, magic || version || householdId || tokenId)
 *   ----------------
 *               93 bytes, inside an NFC Forum external-type record "snib.app:token".
 *               ~113 bytes on tag including NDEF framing, so an NTAG213 (144 B) is enough.
 *
 * On why the secret rides on the tag: holding the tag already means being able to snib and unsnib
 * every phone in the household. Possession is the whole authorisation model, exactly as with a
 * door key. Putting the secret on the tag buys the one-tap join with no account and no server, and
 * costs nothing that possession of the tag did not already cost.
 *
 * The tag UID is never trusted. It is trivially cloned and it is not part of the MAC.
 */
object TokenCodec {

    const val DOMAIN = "snib.app"
    const val TYPE = "token"

    private val MAGIC = byteArrayOf('S'.code.toByte(), 'N'.code.toByte(), 'I'.code.toByte(), 'B'.code.toByte())
    private const val VERSION: Byte = 1
    private const val TOKEN_ID_BYTES = 8
    private const val SIZE = 4 + 1 + HouseholdKey.HOUSEHOLD_ID_BYTES + TOKEN_ID_BYTES +
        HouseholdKey.SECRET_BYTES + 32

    data class Token(
        val householdId: ByteArray,
        val tokenId: String,
        val secret: ByteArray,
    )

    sealed interface Read {
        /** A well-formed Snib token that this phone's household secret authenticates. */
        data class Ours(val token: Token) : Read
        /** A well-formed Snib token from a household this phone is not in. */
        data class Foreign(val token: Token) : Read
        /** Readable and writable, but not a Snib token. A blank tag, or someone's bus pass. */
        data object NotASnibToken : Read
        data class Unreadable(val reason: String) : Read
    }

    fun mint(secret: ByteArray): Token {
        val tokenId = ByteArray(TOKEN_ID_BYTES).also { SecureRandom().nextBytes(it) }
        return Token(HouseholdKey.householdId(secret), tokenId.toHex(), secret)
    }

    fun encode(token: Token): NdefMessage {
        val body = header(token.householdId, token.tokenId.fromHex())
        val mac = HouseholdKey.hmac(token.secret, body)
        val payload = ByteBuffer.allocate(SIZE)
            .put(body)
            .put(token.secret)
            .put(mac)
            .array()
        return NdefMessage(arrayOf(NdefRecord.createExternal(DOMAIN, TYPE, payload)))
    }

    fun decode(message: NdefMessage?, ourSecret: ByteArray?): Read {
        val record = message?.records?.firstOrNull { it.isSnibRecord() }
            ?: return Read.NotASnibToken
        val payload = record.payload
        if (payload.size < SIZE) return Read.Unreadable("Token payload is the wrong size")

        val buffer = ByteBuffer.wrap(payload)
        val magic = ByteArray(4).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) return Read.NotASnibToken
        val version = buffer.get()
        if (version != VERSION) return Read.Unreadable("This token was written by a newer Snib")

        val householdId = ByteArray(HouseholdKey.HOUSEHOLD_ID_BYTES).also { buffer.get(it) }
        val tokenId = ByteArray(TOKEN_ID_BYTES).also { buffer.get(it) }
        val carriedSecret = ByteArray(HouseholdKey.SECRET_BYTES).also { buffer.get(it) }
        val mac = ByteArray(32).also { buffer.get(it) }

        val body = header(householdId, tokenId)
        val token = Token(householdId, tokenId.toHex(), carriedSecret)

        // Integrity first: does the token authenticate under the secret it carries?
        if (!HouseholdKey.constantTimeEquals(mac, HouseholdKey.hmac(carriedSecret, body))) {
            return Read.Unreadable("That token is damaged")
        }
        // Then authority: does it authenticate under *our* secret?
        if (ourSecret != null &&
            HouseholdKey.constantTimeEquals(mac, HouseholdKey.hmac(ourSecret, body))
        ) {
            return Read.Ours(token)
        }
        return Read.Foreign(token)
    }

    private fun header(householdId: ByteArray, tokenId: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + 1 + householdId.size + tokenId.size)
            .put(MAGIC).put(VERSION).put(householdId).put(tokenId)
            .array()

    private fun NdefRecord.isSnibRecord(): Boolean =
        tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
            String(type).equals("$DOMAIN:$TYPE", ignoreCase = true)

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    fun String.fromHex(): ByteArray =
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** Short human-facing name for a token: last four of its id. "Token 4F2A". */
    fun shortName(tokenId: String): String = tokenId.takeLast(4).uppercase()
}
