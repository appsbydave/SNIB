package com.snib.app.apps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val protected: Boolean,
    val isBrowser: Boolean,
    val minutesLastMonth: Long,
)

/**
 * The blocklist picker's data.
 *
 * Sorted by most-used on first load. The apps you want blocked are the ones you use most, and
 * making someone hunt alphabetically for Instagram is a small insult.
 */
object InstalledApps {

    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        val usage = usageMinutes(context)
        val protectedPackages = protectedPackages(context)
        val browsers = browserPackages(context)

        return launchable
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                    protected = info.packageName in protectedPackages,
                    isBrowser = info.packageName in browsers,
                    minutesLastMonth = usage[info.packageName] ?: 0L,
                )
            }
            .sortedWith(
                compareByDescending<InstalledApp> { it.minutesLastMonth }
                    .thenBy { it.label.lowercase() }
            )
    }

    /**
     * Never blockable, and shown greyed in an "Always allowed" list rather than hidden — so
     * people can see they are safe rather than assume they are missing.
     */
    fun protectedPackages(context: Context): Set<String> = buildSet {
        add(context.packageName)
        add("com.android.settings")
        resolve(context, Intent(Settings.ACTION_SETTINGS))?.let(::add)
        resolve(context, Intent(Intent.ACTION_DIAL))?.let(::add)
        Telephony.Sms.getDefaultSmsPackage(context)?.let(::add)
        runCatching {
            (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager)
                .defaultDialerPackage
        }.getOrNull()?.let(::add)
        // The home screen's package also hosts recents on a Pixel. Blocking it would snib the
        // launcher and recents themselves, which is a trap, not a block.
        resolve(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let(::add)
    }

    /**
     * Apps that can resolve a web URL. Blocking Instagram and then leaving a browser open is a hole
     * — you can reach everything you snibbed, on the web — so the picker warns when one is chosen.
     */
    fun browserPackages(context: Context): Set<String> = buildSet {
        val browse = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        context.packageManager.queryIntentActivities(browse, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .forEach(::add)
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun resolve(context: Context, intent: Intent): String? =
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName

    private fun usageMinutes(context: Context): Map<String, Long> = runCatching {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 30L * 24 * 60 * 60 * 1000
        manager.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, start, end)
            .orEmpty()
            .groupBy { it.packageName }
            .mapValues { (_, stats) -> stats.sumOf { it.totalTimeInForeground } / 60_000L }
    }.getOrDefault(emptyMap())
}
