package com.snib.app.nfc

import android.content.Context
import android.nfc.Tag
import com.snib.app.data.SessionRepository
import com.snib.app.data.TokenEntity
import com.snib.app.household.HouseholdKey
import com.snib.app.household.TokenCodec
import com.snib.app.service.BlockingService
import com.snib.app.service.SessionService

/** What the app is expecting the next tap to mean. */
enum class TapMode {
    /** Tap the token. That's the flow, both directions. */
    Toggle,
    /** "Hold a blank tag against the phone" — only reachable while unsnibbed. */
    WriteNewToken,
    /** This phone has no household yet and is being offered one. */
    JoinHousehold,
}

sealed interface TapOutcome {
    data class Snibbed(val message: String = "Snibbed.") : TapOutcome
    data class Unsnibbed(val duration: String) : TapOutcome
    data class TokenWritten(val name: String) : TapOutcome
    data class HouseholdJoined(val name: String) : TapOutcome
    data class Offered(val message: String) : TapOutcome
    data class Rejected(val message: String) : TapOutcome
}

/**
 * All the policy about what a tap means. Errors explain and instruct; they don't apologise and
 * they're never vague.
 */
object TokenController {

    fun handle(context: Context, tag: Tag, mode: TapMode): TapOutcome {
        SessionRepository.ensureLoaded(context)
        val ourSecret = HouseholdKey.secretOrNull(context)
        val read = TagIo.read(tag, ourSecret)

        return when (mode) {
            TapMode.Toggle -> toggle(context, read)
            TapMode.WriteNewToken -> writeNew(context, tag, read, ourSecret)
            TapMode.JoinHousehold -> join(context, read, ourSecret)
        }
    }

    private fun toggle(context: Context, read: TokenCodec.Read): TapOutcome = when (read) {
        is TokenCodec.Read.Ours -> {
            SessionRepository.markTokenSeen()
            if (SessionRepository.isSnibbed) {
                val elapsed = SessionRepository.unsnib(endedBy = "token")
                BlockingService.clearBlock()
                SessionService.sync(context)
                TapOutcome.Unsnibbed(formatDuration(elapsed ?: 0L))
            } else {
                SessionRepository.snib()
                SessionService.sync(context)
                TapOutcome.Snibbed()
            }
        }

        is TokenCodec.Read.Foreign ->
            TapOutcome.Rejected("That's not your token.")

        TokenCodec.Read.NotASnibToken -> when {
            // Never offer to pair a tag mid-session.
            SessionRepository.isSnibbed ->
                TapOutcome.Rejected("That's not your token.")
            HouseholdKey.exists(context) ->
                TapOutcome.Offered("Blank tag. Add it as a token from Tokens.")
            else ->
                TapOutcome.Rejected("That's not a Snib token yet. Set up Snib first.")
        }

        is TokenCodec.Read.Unreadable -> TapOutcome.Rejected(read.reason)
    }

    private fun writeNew(
        context: Context,
        tag: Tag,
        read: TokenCodec.Read,
        ourSecret: ByteArray?,
    ): TapOutcome {
        if (SessionRepository.isSnibbed) {
            return TapOutcome.Rejected("Tokens can only be added while your phone is open.")
        }
        val secret = ourSecret ?: HouseholdKey.create(context)

        if (read is TokenCodec.Read.Ours) {
            return TapOutcome.Rejected(
                "That's already one of your tokens — ${TokenCodec.shortName(read.token.tokenId)}."
            )
        }
        if (read is TokenCodec.Read.Foreign) {
            return TapOutcome.Rejected("That token belongs to another household. Use a blank tag.")
        }

        val token = TokenCodec.mint(secret)
        return when (val result = TagIo.write(tag, TokenCodec.encode(token))) {
            TagIo.WriteResult.Written -> {
                val name = TokenCodec.shortName(token.tokenId)
                SessionRepository.dao.putToken(
                    TokenEntity(
                        tokenId = token.tokenId,
                        label = "Token $name",
                        addedAt = System.currentTimeMillis(),
                    )
                )
                TapOutcome.TokenWritten(name)
            }
            is TagIo.WriteResult.Failed -> TapOutcome.Rejected(result.message)
        }
    }

    private fun join(
        context: Context,
        read: TokenCodec.Read,
        ourSecret: ByteArray?,
    ): TapOutcome {
        if (ourSecret != null) {
            return TapOutcome.Rejected("This phone is already in a household.")
        }
        return when (read) {
            is TokenCodec.Read.Foreign -> {
                HouseholdKey.adopt(context, read.token.secret)
                SessionRepository.dao.putToken(
                    TokenEntity(
                        tokenId = read.token.tokenId,
                        label = "Token ${TokenCodec.shortName(read.token.tokenId)}",
                        addedAt = System.currentTimeMillis(),
                    )
                )
                TapOutcome.HouseholdJoined(TokenCodec.shortName(read.token.tokenId))
            }
            is TokenCodec.Read.Ours -> TapOutcome.Rejected("This phone is already in a household.")
            TokenCodec.Read.NotASnibToken ->
                TapOutcome.Rejected("That's a blank tag. Tap a token that already works.")
            is TokenCodec.Read.Unreadable -> TapOutcome.Rejected(read.reason)
        }
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            totalMinutes > 0 -> "${minutes}m"
            else -> "less than a minute"
        }
    }
}
