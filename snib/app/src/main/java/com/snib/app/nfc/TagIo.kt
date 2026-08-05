package com.snib.app.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.snib.app.household.TokenCodec

/** Raw tag reads and writes. No policy here; policy lives in TokenController. */
object TagIo {

    fun read(tag: Tag, ourSecret: ByteArray?): TokenCodec.Read {
        val ndef = Ndef.get(tag) ?: return TokenCodec.Read.NotASnibToken
        return try {
            ndef.connect()
            TokenCodec.decode(ndef.ndefMessage, ourSecret)
        } catch (e: Exception) {
            TokenCodec.Read.Unreadable("The token didn't read. Hold the phone flat against it.")
        } finally {
            runCatching { ndef.close() }
        }
    }

    sealed interface WriteResult {
        data object Written : WriteResult
        data class Failed(val message: String) : WriteResult
    }

    fun write(tag: Tag, message: NdefMessage): WriteResult {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable ->
                        WriteResult.Failed("That tag is locked. It can't be made into a token.")
                    ndef.maxSize < message.toByteArray().size ->
                        WriteResult.Failed("That tag is too small. A Snib token needs 144 bytes.")
                    else -> {
                        ndef.writeNdefMessage(message)
                        WriteResult.Written
                    }
                }
            } catch (e: Exception) {
                WriteResult.Failed("The token didn't finish writing. Hold it still and try again.")
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag)
            ?: return WriteResult.Failed("That tag can't hold a Snib token.")
        return try {
            formatable.connect()
            formatable.format(message)
            WriteResult.Written
        } catch (e: Exception) {
            WriteResult.Failed("The token didn't finish writing. Hold it still and try again.")
        } finally {
            runCatching { formatable.close() }
        }
        // Lock bytes are deliberately not set here. Tokens are locked at the factory before
        // shipping; locking during development turns every mistake into landfill.
    }
}
