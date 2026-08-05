package com.snib.app.block

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
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

/**
 * The block screen, as a real window rather than an activity.
 *
 * An overlay is preferred to GLOBAL_ACTION_HOME because being silently bounced home is confusing,
 * and the block screen is where the product's voice lives. It is preferred to an activity because
 * an activity enters the recents stack and can be swiped away mid-session.
 *
 * The window is focusable, which is what lets it swallow the back key.
 */
class BlockOverlay(
    private val context: Context,
    private val onDismiss: () -> Unit,
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var host: Host? = null
    private val blockedLabel = mutableStateOf("")

    val isShowing: Boolean get() = host != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(appLabel: String) {
        blockedLabel.value = appLabel
        if (host != null) return

        val newHost = Host(context)
        newHost.view.setContent {
            BlockScreen(
                appLabel = blockedLabel.value,
                onDismiss = { onDismiss() },
            )
        }
        newHost.view.isFocusableInTouchMode = true
        newHost.view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onDismiss(); true
            } else {
                // Volume and power keep working. Nothing about a block should feel like a hijack.
                false
            }
        }

        try {
            windowManager.addView(newHost.view, layoutParams())
            newHost.resume()
            newHost.view.requestFocus()
            host = newHost
        } catch (e: Exception) {
            // Overlay permission revoked mid-session, or an OEM refusing the window.
            // Fall back to the blunt instrument rather than failing open.
            onDismiss()
        }
    }

    fun hide() {
        val current = host ?: return
        host = null
        runCatching { windowManager.removeView(current.view) }
        current.destroy()
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    )

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
