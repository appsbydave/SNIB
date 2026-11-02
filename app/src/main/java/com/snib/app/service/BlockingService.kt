package com.snib.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.snib.app.block.BlockOverlay
import com.snib.app.block.SelfDefence
import com.snib.app.data.SessionRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The enforcement surface. One tier, hardened accessibility, no Device Owner, ever.
 *
 * The decision is driven by which *activity* is in front, not by whichever window last sent an
 * event. This is the Digital Detox model, and it is what fixes the Chrome bug: once the block
 * screen is up, events still arrive from windows that are not the blocked app — the keyboard, the
 * notification shade, the volume panel, a heads-up notification. Those windows are not activities,
 * so they can *raise* a block but they can never *clear* one. Only an activity coming to the front
 * moves [foreground], and the full decision re-runs whenever session state changes or a break ends.
 */
class BlockingService : AccessibilityService() {

    companion object {
        @Volatile private var instance: BlockingService? = null
        val isConnected: Boolean get() = instance != null

        /** Called when a session ends by any route, so the overlay does not outlive the snib. */
        fun clearBlock() = instance?.let { it.handler.post { it.overlay.hide() } }

        /** Re-run the decision now. Call when a break ends; state changes re-decide on their own. */
        fun recheck() = instance?.let { s -> s.handler.post { s.enforce() } }

        /**
         * A second source for the foreground package: the Usage Access poll in [SessionService].
         * It catches app launches that the accessibility event stream missed — the exact class of
         * bug behind Digital Detox's "block not returning after a call" regression.
         */
        fun offerForeground(packageName: String) =
            instance?.let { s -> s.handler.post { s.foreground = packageName; s.enforce() } }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val activityCache = HashMap<String, Boolean>()

    private lateinit var overlay: BlockOverlay
    private val labels = mutableMapOf<String, String>()

    /** Package whose activity is in front. Keyboards, the shade, toasts and dialogs never set it. */
    private var foreground: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        SessionRepository.ensureLoaded(this)
        overlay = BlockOverlay(this) {
            // One dismiss, returning home. No count of failed attempts: counting turns a moment
            // of failure into a score.
            overlay.hide()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        instance = this
        // If a session is running, tell the foreground service we are connected so its "snibbed
        // but not blocking" warning clears. (Nothing to show when not snibbed, and starting the
        // service only to stop it would be wasteful.) Any snib, unsnib or break start re-decides
        // immediately — the flow emits once on collect, and again on every change.
        if (SessionRepository.isSnibbed) SessionService.sync(this)
        scope.launch { SessionRepository.state.collect { enforce() } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (SelfDefence.intercept(this, event, packageName)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        when {
            isActivity(packageName, event.className) -> {
                // Tracked in every state — unsnibbed, snibbed, on a break — so that the moment a
                // break ends or a session starts, the right answer is already in hand.
                foreground = packageName
                enforce()
            }
            packageName != this.packageName && SessionRepository.shouldBlock(packageName) -> {
                // A blocked app's own floating window or dialog (not an activity). It may add a
                // block; it must never take one down.
                if (SessionRepository.isSnibbed && !SessionRepository.onBreak()) {
                    overlay.show(labelOf(packageName))
                }
            }
            // Anything else — IME, shade, volume panel, toast, our own overlay — changes nothing.
        }
    }

    /** The whole decision, in one place, safe to call at any time. */
    fun enforce() {
        if (!SessionRepository.isSnibbed || SessionRepository.onBreak()) {
            if (overlay.isShowing) overlay.hide()
            return
        }
        // The foreground activity is the common case. The window sweep catches the awkward ones:
        // freeform and mini-window launches, split-screen and picture-in-picture, where a blocked
        // app is visible without being the single foreground activity.
        val offender = foreground?.takeIf { SessionRepository.shouldBlock(it) } ?: sweepWindows()
        if (offender != null) overlay.show(labelOf(offender)) else overlay.hide()
    }

    /**
     * Is `pkg/cls` an activity? A window-state event from an activity is a real foreground change;
     * one from a dialog, input method or system window is not. Cached because it is asked on the
     * hot path. A class in a package we cannot see under package visibility reads as "not an
     * activity", which fails safe: such a window can raise a block but not clear one.
     */
    private fun isActivity(pkg: String, cls: CharSequence?): Boolean {
        cls ?: return false
        return activityCache.getOrPut("$pkg/$cls") {
            @Suppress("DEPRECATION")
            runCatching {
                packageManager.getActivityInfo(ComponentName(pkg, cls.toString()), 0)
            }.isSuccess
        }
    }

    private fun sweepWindows(): String? {
        val windows = try { windows } catch (e: Exception) { return null } ?: return null
        for (window in windows) {
            val root: AccessibilityNodeInfo = window.root ?: continue
            val pkg = root.packageName?.toString()
            runCatching { root.recycle() }
            if (pkg != null && pkg != packageName && SessionRepository.shouldBlock(pkg)) return pkg
        }
        return null
    }

    private fun labelOf(packageName: String): String = labels.getOrPut(packageName) {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "That app"
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // The user has switched the service off in Settings. That is allowed; it is the stated
        // ceiling. Tell the foreground service, which will raise the "snibbed but not blocking"
        // warning and, where it can, keep blocking via the Usage Access fallback.
        instance = null
        if (::overlay.isInitialized) overlay.hide()
        // Raise the "snibbed but not blocking" warning and hand enforcement to the Usage Access
        // fallback — but only if a session is actually running.
        if (SessionRepository.isSnibbed) SessionService.sync(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        if (::overlay.isInitialized) overlay.hide()
        instance = null
        super.onDestroy()
    }
}
