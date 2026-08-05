package com.snib.app.apps

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.snib.app.service.BlockingService
import com.snib.app.service.SnibDeviceAdminReceiver

/**
 * One at a time, with a plain reason each. Never four requests at once.
 */
object Permissions {

    fun accessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, BlockingService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) } ||
            BlockingService.isConnected
    }

    fun overlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun notificationsGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun batteryExempt(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun deviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(SnibDeviceAdminReceiver.component(context))
    }

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlayIntent(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    @Suppress("BatteryLife")
    fun batteryIntent(context: Context) = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )

    fun deviceAdminIntent(context: Context) =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                SnibDeviceAdminReceiver.component(context),
            )
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Makes Snib take an extra deliberate step to uninstall. " +
                    "Snib cannot lock, wipe or manage your phone.",
            )
}
