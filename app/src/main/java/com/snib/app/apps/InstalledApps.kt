package com.snib.app.apps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.snib.app.data.AppCatalog

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val protected: Boolean,
    val isBrowser: Boolean,
    val minutesLastMonth: Long,
)

/**
 * The phone's apps, as Snib needs to see them: the picker's list, and the catalog the blocking
 * decision runs on.
 */
object InstalledApps {

    /**
     * The picker's data. Launchers are left out — they are never kept or blocked, they get the
     * snibbed home screen drawn over them. Sorted by most-used on first load, then alphabetical.
     */
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        val usage = usageMinutes(context)
        val floor = protectedPackages(context)
        val home = homePackages(context, floor)
        val browsers = browserPackages(context)

        return launchable
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName && it.packageName !in home }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                    protected = info.packageName in floor,
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
     * Everything the blocking decision needs, in one read. If any of it fails, return the empty
     * catalog — BlockDecision fails open on an empty app list rather than guess.
     */
    fun catalog(context: Context): AppCatalog = runCatching {
        val floor = protectedPackages(context)
        AppCatalog(
            launchable = launchablePackages(context),
            floor = floor,
            home = homePackages(context, floor),
        )
    }.getOrDefault(AppCatalog.EMPTY)

    /** Every package with a launcher icon. While snibbed, these are blocked unless kept. */
    fun launchablePackages(context: Context): Set<String> =
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

    /**
     * Never blockable, and shown greyed in an "Always allowed" list rather than hidden — so
     * people can see they are safe rather than assume they are missing.
     *
     * With an allowlist this floor matters more than it did with a blocklist: anything not in it,
     * and not kept, is blocked. So it covers everything a phone must always be able to do — take
     * and make calls, reach emergency services, dismiss an alarm, type, and open Settings.
     */
    fun protectedPackages(context: Context): Set<String> = buildSet {
        add(context.packageName)

        // Settings. SelfDefence guards the handful of screens that switch Snib off.
        add("com.android.settings")
        settingsPackage(context)?.let(::add)

        // Calls: the dialler and in-call screen, telephony and the emergency dialler.
        dialerPackage(context)?.let(::add)
        resolve(context, Intent(Intent.ACTION_DIAL))?.let(::add)
        add("com.android.phone")
        add("com.android.server.telecom")

        // Emergency: Pixel's Safety app runs Emergency SOS and crash detection; it must never be
        // covered by a block screen.
        add("com.android.emergency")
        add("com.google.android.apps.safetyhub")
        resolve(context, Intent(ACTION_EMERGENCY_ASSISTANCE))?.let(::add)

        // Messages.
        Telephony.Sms.getDefaultSmsPackage(context)?.let(::add)

        // The clock. An alarm going off must always be dismissable.
        clockPackage(context)?.let(::add)
        add("com.google.android.deskclock")
        add("com.android.deskclock")

        // Keyboards, in case one has a launcher icon of its own.
        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.forEach { add(it.packageName) }
        }

        // System plumbing. None of these has a launcher icon, so none would be blocked anyway;
        // listed so the intent is written down.
        add("com.android.systemui")
        add("com.android.permissioncontroller")
        add("com.google.android.permissioncontroller")
    }

    /**
     * Every installed launcher. On a Pixel, Pixel Launcher also hosts recents and the app drawer.
     * While snibbed these get the snibbed home screen drawn over them, which is what stops
     * "swipe up, open anything". Settings' fallback home (shown for a moment at boot) is in the
     * floor, so it is left out here.
     */
    fun homePackages(context: Context, floor: Set<String> = protectedPackages(context)): Set<String> =
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != "android" && it !in floor }
            .toSet()

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun dialerPackage(context: Context): String? = runCatching {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull() ?: resolve(context, Intent(Intent.ACTION_DIAL))

    fun clockPackage(context: Context): String? =
        resolve(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun settingsPackage(context: Context): String? =
        resolve(context, Intent(Settings.ACTION_SETTINGS))

    /**
     * Apps that can resolve a web URL. Keeping a browser is a hole — it reaches everything you
     * snibbed, on the web — so the picker says so when one is kept.
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

    /** TelephonyManager.ACTION_EMERGENCY_ASSISTANCE, spelled out so it compiles on every API level. */
    private const val ACTION_EMERGENCY_ASSISTANCE = "android.telephony.action.EMERGENCY_ASSISTANCE"

    /**
     * The package that would handle [intent], or null. When several apps could and none is the
     * default, Android answers with its own chooser, which lives in the "android" package — that
     * tells us nothing, so it is dropped.
     */
    private fun resolve(context: Context, intent: Intent): String? =
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
            ?.takeIf { it != "android" }

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
