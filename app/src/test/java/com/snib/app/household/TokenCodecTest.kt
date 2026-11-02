package com.snib.app.household

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The token wire format, round-tripped and attacked, with no Android around it.
 *
 * The property that matters: a token authenticates only under the household secret it was written
 * with. A foreign household's token, or a tampered one, must never read as ours.
 */
class TokenCodecTest {

    private fun secret(fill: Byte) = ByteArray(HouseholdKey.SECRET_BYTES) { fill }

    @Test fun `our own token round-trips`() {
        val secret = secret(0x11)
        val token = TokenCodec.mint(secret)
        val payload = TokenCodec.encodePayload(token)

        val read = TokenCodec.decodePayload(payload, secret)
        assertTrue(read is TokenCodec.Read.Ours)
        assertEquals(token.tokenId, (read as TokenCodec.Read.Ours).token.tokenId)
    }

    @Test fun `another household's token reads as foreign`() {
        val ours = secret(0x11)
        val theirs = secret(0x22)
        val payload = TokenCodec.encodePayload(TokenCodec.mint(theirs))

        assertTrue(TokenCodec.decodePayload(payload, ours) is TokenCodec.Read.Foreign)
    }

    @Test fun `with no household yet a valid token reads as foreign`() {
        val payload = TokenCodec.encodePayload(TokenCodec.mint(secret(0x33)))
        assertTrue(TokenCodec.decodePayload(payload, null) is TokenCodec.Read.Foreign)
    }

    @Test fun `a tampered token is rejected`() {
        val secret = secret(0x11)
        val payload = TokenCodec.encodePayload(TokenCodec.mint(secret))
        payload[payload.size - 1] = (payload[payload.size - 1] + 1).toByte()  // flip a MAC byte

        assertTrue(TokenCodec.decodePayload(payload, secret) is TokenCodec.Read.Unreadable)
    }

    @Test fun `a blank tag is not a snib token`() {
        val blank = ByteArray(128)  // large enough, but no SNIB magic
        assertTrue(TokenCodec.decodePayload(blank, secret(0x11)) is TokenCodec.Read.NotASnibToken)
    }
}
