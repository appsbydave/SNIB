package com.snib.app

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.snib.app.data.SessionRepository
import com.snib.app.nfc.TapMode
import com.snib.app.nfc.TapOutcome
import com.snib.app.nfc.TokenController
import com.snib.app.ui.Haptics
import com.snib.app.ui.SnibRoot
import com.snib.app.ui.theme.SnibTheme

/**
 * Single activity. Its only real job beyond hosting the UI is turning a tag into a decision.
 *
 * Reader mode while foregrounded (faster, and it gives us the Tag for writes as well as reads);
 * the manifest NDEF intent filter covers taps when Snib is not on screen.
 */
class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SessionRepository.ensureLoaded(this)
        AppState.load(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            SnibTheme { SnibRoot() }
        }

        handleTagIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            { tag -> onTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    override fun onPause() {
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTagIntent(intent)
    }

    private fun handleTagIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            intent?.action != NfcAdapter.ACTION_TAG_DISCOVERED
        ) return
        @Suppress("DEPRECATION")
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        onTag(tag)
    }

    /** Called on a binder thread by reader mode. */
    private fun onTag(tag: Tag) {
        val mode = AppState.tapMode.value
        val outcome = TokenController.handle(this, tag, mode)

        runOnUiThread {
            when (outcome) {
                is TapOutcome.Snibbed -> {
                    Haptics.tick(this)
                    AppState.say(outcome.message)
                }
                is TapOutcome.Unsnibbed -> {
                    Haptics.tick(this)
                    AppState.say("Snibbed for ${outcome.duration}.")
                }
                is TapOutcome.TokenWritten -> {
                    Haptics.tick(this)
                    AppState.expect(TapMode.Toggle)
                    AppState.say("Token ${outcome.name} is yours.")
                }
                is TapOutcome.HouseholdJoined -> {
                    Haptics.tick(this)
                    AppState.expect(TapMode.Toggle)
                    AppState.say("Joined. Your household's tokens work on this phone.")
                }
                is TapOutcome.Offered -> AppState.say(outcome.message)
                is TapOutcome.Rejected -> {
                    Haptics.tick(this, heavy = false)
                    AppState.say(outcome.message)
                }
            }
        }
    }
}
