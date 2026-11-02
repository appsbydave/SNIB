package com.snib.app

import android.content.Context
import com.snib.app.nfc.TapMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The bits of UI state that outlive a screen: what the next tap means, and the one line of
 * feedback the last tap produced.
 */
object AppState {

    private const val PREFS = "snib.prefs"
    private const val KEY_SETUP_DONE = "setup_complete"

    private val _tapMode = MutableStateFlow(TapMode.Toggle)
    val tapMode: StateFlow<TapMode> = _tapMode.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete.asStateFlow()

    fun load(context: Context) {
        _setupComplete.value = prefs(context).getBoolean(KEY_SETUP_DONE, false)
    }

    fun completeSetup(context: Context) {
        prefs(context).edit().putBoolean(KEY_SETUP_DONE, true).apply()
        _setupComplete.value = true
    }

    fun expect(mode: TapMode) { _tapMode.value = mode }

    fun say(message: String?) { _notice.value = message }

    fun clearNotice() { _notice.value = null }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
