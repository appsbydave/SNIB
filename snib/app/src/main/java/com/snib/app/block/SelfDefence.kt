package com.snib.app.block

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches for navigation toward the off switch and bounces the user back.
 *
 * Settings itself is never blocked — you must always be able to reach your own phone's settings.
 * What gets bounced is the specific handful of screens that switch Snib off: the accessibility
 * detail page, Snib's app-info page, and the device-admin page.
 *
 * Honest ceiling, stated in the brief and in the marketing: a determined technical user gets past
 * this in about ninety seconds, or instantly over ADB. The job is defeating the reflex.
 */
object SelfDefence {

    private const val COOLDOWN_MS = 600L

    private val settingsPackages = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.oppo.settings",
        "com.oneplus.settings",
    )

    private val offSwitchScreens = listOf(
        "AccessibilitySettings",
        "AccessibilityDetailsSettings",
        "ToggleAccessibilityServicePreferenceFragment",
        "InstalledAppDetails",
        "AppInfoDashboardFragment",
        "DeviceAdminSettings",
        "DeviceAdminAdd",
    )

    private var lastBounceAt = 0L

    /** Returns true if the event was handled and no further blocking decision should be made. */
    fun intercept(
        service: AccessibilityService,
        event: AccessibilityEvent,
        packageName: String,
    ): Boolean {
        if (packageName !in settingsPackages) return false

        val className = event.className?.toString().orEmpty()
        val onOffSwitchScreen = offSwitchScreens.any { className.contains(it, ignoreCase = true) }
        if (!onOffSwitchScreen) return false

        // An accessibility list or an app-info page is only interesting if it is about us.
        if (!mentionsSnib(service.rootInActiveWindow)) return false

        val now = System.currentTimeMillis()
        if (now - lastBounceAt < COOLDOWN_MS) return true
        lastBounceAt = now
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        return true
    }

    private fun mentionsSnib(root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        return try {
            root.findAccessibilityNodeInfosByText("Snib").isNotEmpty()
        } catch (e: Exception) {
            false
        } finally {
            runCatching { root.recycle() }
        }
    }
}
