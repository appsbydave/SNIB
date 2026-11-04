package com.snib.app.block

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.snib.app.ui.BlockScreen
import com.snib.app.ui.HomeLinks
import com.snib.app.ui.SnibbedHomeOverlay

/**
 * The block screen and the snibbed home screen, as real windows rather than activities.
 *
 * An overlay is preferred to GLOBAL_ACTION_HOME because being silently bounced home is confusing,
 * and the block screen is where the product's voice lives. It is preferred to an activity because
 * an activity enters the recents stack and can be swiped away mid-session.
 *
 * Two modes, two window types:
 *
 *  - **Blocked** covers an app that isn't kept. TYPE_ACCESSIBILITY_OVERLAY when hosted by the
 *    accessibility service, so an app that hides overlays can't make it invisible.
 *  - **Home** covers the launcher — and with it the app drawer and recents — with the kept apps.
 *    TYPE_APPLICATION_OVERLAY, deliberately: it sits *below* the status bar, the notification
 *    shade and the lock screen, so notifications, Quick Settings and unlocking all still work.
 *    An accessibility overlay would sit on top of all three. It needs the "draw over other apps"
 *    permission; without it the home screen isn't drawn and apps are still blocked one by one.
 *
 * Both windows are focusable, which is what lets them swallow the back key.
 */
class BlockOverlay(
    private val context: Context,
    private val onDismiss: () -> Unit,
    private val onOpen: (String) -> Unit = { HomeLinks.open(context, it) },
) {

    private sealed interface Mode {
        data class Blocked(val label: String) : Mode
        data object Home : Mode
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var host: Host? = null
    private var hostType: Int = 0
    private val mode = mutableStateOf<Mode>(Mode.Home)

    val isShowing: Boolean get() = host != null

    /** Cover a blocked app. */
    fun show(appLabel: String) = present(Mode.Blocked(appLabel))

    /** Cover the launcher with the snibbed home screen. */
    fun showHome() = present(Mode.Home)

    fun hide() {
        val current = host ?: return
        host = null
        runCatching { windowManager.removeView(current.view) }
        current.destroy()
    }

    private fun present(next: Mode) {
        val type = windowTypeFor(next)
        if (type == null) {
            hide()
            return
        }
        mode.value = next
        if (host != null && hostType == type) return
        // Switching between block and home changes the window type, and a window's type is fixed
        // once added — so take the old one down and put a new one up.
        hide()
        add(type)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add(type: Int) {
        val newHost = Host(context)
        newHost.view.setContent {
            when (val current = mode.value) {
                is Mode.Blocked -> BlockScreen(
                    appLabel = current.label,
                    onDismiss = { onDismiss() },
                )
                Mode.Home -> SnibbedHomeOverlay(onOpen = onOpen)
            }
        }
        newHost.view.isFocusableInTouchMode = true
        newHost.view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // On the block screen, back is the one dismiss. On the home screen there is
                // nowhere further back to go.
                if (event.action == KeyEvent.ACTION_UP && mode.value is Mode.Blocked) onDismiss()
                true
            } else {
                // Volume and power keep working. Nothing about a block should feel like a hijack.
                false
            }
        }

        try {
            windowManager.addView(newHost.view, layoutParams(type))
            newHost.resume()
            newHost.view.requestFocus()
            host = newHost
            hostType = type
        } catch (e: Exception) {
            newHost.destroy()
            // Overlay permission revoked mid-session, or an OEM refusing the window. For a block,
            // fall back to the blunt instrument rather than failing open. For the home screen,
            // there's nothing to fall back to — the apps in the drawer are still blocked one by one.
            if (mode.value is Mode.Blocked) onDismiss()
        }
    }

    private fun layoutParams(type: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        type,
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    )

    /**
     * When the block screen is hosted by the accessibility service we use
     * TYPE_ACCESSIBILITY_OVERLAY. It needs no overlay permission and, crucially, it is not a
     * "non-system overlay", so an app that calls setHideOverlayWindows(true) — which banking and
     * payment apps do, and which hides every TYPE_APPLICATION_OVERLAY — cannot make the block
     * screen silently invisible.
     *
     * The session-service fallback (accessibility off) has no such host, so it uses
     * TYPE_APPLICATION_OVERLAY and the SYSTEM_ALERT_WINDOW permission.
     *
     * The home screen always uses TYPE_APPLICATION_OVERLAY (see the class comment), or isn't
     * drawn at all: null means "don't show".
     */
    private fun windowTypeFor(mode: Mode): Int? = when (mode) {
        Mode.Home ->
            if (Settings.canDrawOverlays(context)) applicationOverlay() else null
        is Mode.Blocked ->
            if (context is AccessibilityService) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                applicationOverlay()
            }
    }

    private fun applicationOverlay(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /**
     * Compose needs a lifecycle, a saved-state registry and a view-model store to run inside a
     * window that is not an activity. A fresh host per showing: a destroyed registry cannot be
     * restarted.
     */
    private class Host(context: Context) :
        LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        val view = ComposeView(context)

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }

        fun resume() { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}
