package com.snib.app.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.snib.app.block.BlockOverlay
import com.snib.app.block.SelfDefence
import com.snib.app.data.SessionRepository

/**
 * The enforcement surface. One tier, hardened accessibility, no Device Owner, ever.
 */
class BlockingService : AccessibilityService() {

    companion object {
        @Volatile private var instance: BlockingService? = null
        val isConnected: Boolean get() = instance != null
        /** Called when a session ends by any route, so the overlay does not outlive the snib. */
        fun clearBlock() = instance?.overlay?.hide()
    }

    private lateinit var overlay: BlockOverlay
    private val labels = mutableMapOf<String, String>()

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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        if (SelfDefence.intercept(this, event, packageName)) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (!SessionRepository.isSnibbed || SessionRepository.onBreak()) {
            if (overlay.isShowing) overlay.hide()
            return
        }

        // The foreground package is the common case. The window sweep catches the awkward ones:
        // freeform and mini-window launches (a documented real-world Brick failure), split-screen,
        // and picture-in-picture, where the blocked app is visible without being foreground.
        val offender = packageName.takeIf { SessionRepository.shouldBlock(it) } ?: sweepWindows()

        if (offender != null) {
            overlay.show(labelOf(offender))
        } else if (overlay.isShowing) {
            overlay.hide()
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // The user has switched the service off in Settings. That is allowed; it is the stated
        // ceiling. Leave nothing hanging on the screen.
        if (::overlay.isInitialized) overlay.hide()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (::overlay.isInitialized) overlay.hide()
        instance = null
        super.onDestroy()
    }
}
