package com.snib.app.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.snib.app.block.BlockOverlay
import com.snib.app.block.SelfDefence
import com.snib.app.data.SessionRepository
import com.snib.app.data.Verdict
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
 * so they never move [foreground], and the full decision re-runs whenever session state changes or
 * a break ends.
 *
 * Three outcomes (see BlockDecision): let it through, cover it with the block screen, or — when
 * the launcher is in front — cover it with the snibbed home screen. The last one is what closes
 * "swipe up and open anything": on a Pixel the launcher *is* the app drawer and recents.
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
    private lateinit var keyguard: KeyguardManager
    private val labels = mutableMapOf<String, String>()
    private var receiversRegistered = false

    /** Package whose activity is in front. Keyboards, the shade, toasts and dialogs never set it. */
    private var foreground: String? = null

    /**
     * Screen on and unlock re-decide straight away, so a block hidden under the lock screen comes
     * back the moment you're in. App installs, removals and changes refresh the app catalog, so an
     * app installed mid-session is blocked like any other.
     */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> enforce()
                else -> {
                    // The first half of an update. PACKAGE_REPLACED follows once it's back.
                    val updating = intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (updating) return
                    SessionRepository.refreshCatalog()
                    activityCache.clear()
                    labels.clear()
                    enforce()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SessionRepository.ensureLoaded(this)
        SessionRepository.refreshCatalog()
        keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        overlay = BlockOverlay(this, onDismiss = {
            // One dismiss, returning home — which, while snibbed, is the snibbed home screen.
            // No count of failed attempts: counting turns a moment of failure into a score.
            overlay.hide()
            performGlobalAction(GLOBAL_ACTION_HOME)
        })
        registerReceivers()
        instance = this
        // If a session is running, tell the foreground service we are connected so its "snibbed
        // but not blocking" warning clears. (Nothing to show when not snibbed, and starting the
        // service only to stop it would be wasteful.) Any snib, unsnib or break start re-decides
        // immediately — the flow emits once on collect, and again on every change.
        if (SessionRepository.isSnibbed) SessionService.sync(this)
        scope.launch { SessionRepository.state.collect { enforce() } }
        scope.launch { SessionRepository.allowed.collect { enforce() } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (SelfDefence.intercept(this, event, packageName)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Only an activity coming to the front changes anything. Tracked in every state —
        // unsnibbed, snibbed, on a break — so that the moment a break ends or a session starts,
        // the right answer is already in hand.
        //
        // Windows that aren't activities — the keyboard, the shade, the volume panel, toasts,
        // our own overlay, and other apps' floating overlays such as chat heads and caller-ID
        // pop-ups — change nothing. With an allowlist, letting an unknown app's floating window
        // raise a block would mean a caller-ID app could put the block screen over a ringing
        // phone. Activities in split-screen, freeform and picture-in-picture are still caught by
        // the window sweep.
        if (isActivity(packageName, event.className)) {
            foreground = packageName
            enforce()
        }
    }

    /** The whole decision, in one place, safe to call at any time. */
    fun enforce() {
        if (!::overlay.isInitialized) return
        if (!SessionRepository.isSnibbed || SessionRepository.onBreak()) {
            if (overlay.isShowing) overlay.hide()
            return
        }

        val front = foreground
        val verdict = front?.let(SessionRepository::verdict) ?: Verdict.ALLOW
        // The foreground activity is the common case. The window sweep catches the awkward ones:
        // freeform and mini-window launches, split-screen and picture-in-picture, where a blocked
        // app is visible without being the single foreground activity.
        val offender = if (verdict == Verdict.BLOCK) front else sweepWindows()

        when {
            // The lock screen is already doing the blocking, and the block screen sits above it —
            // leaving it up would cover the PIN pad. It comes back on unlock (USER_PRESENT).
            offender != null && keyguard.isKeyguardLocked -> overlay.hide()
            offender != null -> overlay.show(labelOf(offender))
            // The launcher: home screen, app drawer, recents. Cover it with the kept apps. This
            // window sits below the lock screen, so it can stay up while locked.
            verdict == Verdict.HOME -> overlay.showHome()
            else -> overlay.hide()
        }
    }

    /**
     * Is `pkg/cls` an activity? A window-state event from an activity is a real foreground change;
     * one from a dialog, input method or system window is not. Cached because it is asked on the
     * hot path. A class in a package we cannot see under package visibility reads as "not an
     * activity", which fails safe: such a window cannot clear a block.
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

    /**
     * A blocked app visible in any *application* window — split-screen, freeform, a mini-window,
     * picture-in-picture. System windows, the keyboard and other apps' overlays are skipped: see
     * [onAccessibilityEvent] for why.
     */
    private fun sweepWindows(): String? {
        val windows = try { windows } catch (e: Exception) { return null } ?: return null
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root: AccessibilityNodeInfo = window.root ?: continue
            val pkg = root.packageName?.toString()
            @Suppress("DEPRECATION")
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

    private fun registerReceivers() {
        if (receiversRegistered) return
        val screen = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // System broadcasts only; nothing outside Snib needs to reach these.
        ContextCompat.registerReceiver(this, receiver, screen, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, receiver, packages, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiversRegistered = true
    }

    private fun unregisterReceivers() {
        if (!receiversRegistered) return
        runCatching { unregisterReceiver(receiver) }
        receiversRegistered = false
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // The user has switched the service off in Settings. That is allowed; it is the stated
        // ceiling. Tell the foreground service, which will raise the "snibbed but not blocking"
        // warning and, where it can, keep blocking via the Usage Access fallback — but only if a
        // session is actually running.
        instance = null
        unregisterReceivers()
        if (::overlay.isInitialized) overlay.hide()
        if (SessionRepository.isSnibbed) SessionService.sync(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterReceivers()
        if (::overlay.isInitialized) overlay.hide()
        instance = null
        super.onDestroy()
    }
}
