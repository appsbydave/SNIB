package com.snib.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snib.app.MainActivity
import com.snib.app.R
import com.snib.app.SnibApp
import com.snib.app.block.BlockOverlay
import com.snib.app.data.SessionRepository
import com.snib.app.data.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the session alive and visible, and — following Digital Detox — carries a second,
 * independent enforcer that does not depend on the accessibility service.
 *
 * Digital Detox's core blocking runs on a foreground service, Usage Access and draw-over-apps;
 * accessibility is only cheat-protection on top. Snib keeps the accessibility service as its
 * primary enforcer (it sees split-screen and freeform, which Usage Access cannot), but this
 * service now:
 *
 *   - watches which app is in front via Usage Access while snibbed and the screen is on;
 *   - when the accessibility service is connected, feeds that as a second source so a missed
 *     event cannot leave a blocked app open;
 *   - when the accessibility service is off, draws the block screen itself — slower, and blind to
 *     split-screen, but it still blocks something rather than failing open;
 *   - raises a loud "snibbed, but not blocking" notification whenever enforcement is degraded.
 */
class SessionService : Service() {

    companion object {
        const val ACTION_SYNC = "com.snib.app.SYNC"
        private const val NOTIFICATION_ID = 1
        private const val POLL_MS = 900L
        private const val LOOKBACK_MS = 10_000L

        fun sync(context: Context) {
            val intent = Intent(context, SessionService::class.java).setAction(ACTION_SYNC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Latest package whose activity came to the front in the trailing window, or null.
         * ACTIVITY_RESUMED is reported only for activities, so this never confuses the keyboard or
         * the shade for the foreground app.
         */
        fun latestResumed(context: Context, since: Long): String? {
            val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
            val events = usm.queryEvents(since, System.currentTimeMillis())
            val event = UsageEvents.Event()
            var pkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) pkg = event.packageName
            }
            return pkg
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var breakWatcher: Job? = null
    private var poller: Job? = null

    private lateinit var powerManager: PowerManager
    private var fallbackOverlay: BlockOverlay? = null
    private val labels = mutableMapOf<String, String>()

    /**
     * Last package Usage Access reported in front. ACTIVITY_RESUMED only fires on a transition, so
     * a blocked app sitting still produces no fresh event — we must remember it, or the fallback
     * would drop the block after the lookback window slides past the launch.
     */
    private var lastForeground: String? = null

    /** So the notification is rebuilt the moment enforcement becomes degraded or recovers. */
    private var lastDegraded: Boolean? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SessionRepository.ensureLoaded(this)

        // Honour the foreground-start contract first: a service launched with
        // startForegroundService() must call startForeground() within a few seconds or the system
        // kills the process, even on the path where we are only being told the session just ended.
        lastDegraded = isDegraded()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!SessionRepository.isSnibbed) {
            stopEnforcing()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        watchBreak()
        startPolling()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun watchBreak() {
        breakWatcher?.cancel()
        val remaining = SessionRepository.breakRemainingMs()
        if (remaining <= 0L) return
        breakWatcher = scope.launch {
            delay(remaining)
            // Silent re-snib at expiry. No thirty-second warning; the break should not be
            // something you learn to ride to the last second. Re-decide on both enforcers.
            if (SessionRepository.isSnibbed) {
                BlockingService.recheck()
                renotify()
            }
        }
    }

    /**
     * The Usage Access loop. Cheap, and only while snibbed and the screen is on.
     */
    private fun startPolling() {
        poller?.cancel()
        poller = scope.launch {
            while (isActive && SessionRepository.isSnibbed) {
                tick()
                delay(POLL_MS)
            }
            fallbackOverlay?.hide()
        }
    }

    private fun tick() {
        // Rebuild the notification if enforcement just degraded or recovered.
        val degraded = isDegraded()
        if (degraded != lastDegraded) {
            lastDegraded = degraded
            renotify()
        }

        if (!powerManager.isInteractive) {
            fallbackOverlay?.hide()
            return
        }

        // Only a non-null reading updates what's in front; a quiet tick keeps the last known.
        latestResumed(this, System.currentTimeMillis() - LOOKBACK_MS)?.let { lastForeground = it }
        val front = lastForeground

        if (BlockingService.isConnected) {
            // Accessibility is primary; the overlay belongs to it. Feed it a second signal and
            // make sure our fallback window is down.
            fallbackOverlay?.hide()
            if (front != null) BlockingService.offerForeground(front)
            return
        }

        // Accessibility is off. Draw the block screen ourselves.
        if (!SessionRepository.isSnibbed || SessionRepository.onBreak()) {
            fallbackOverlay?.hide()
            return
        }
        if (front == null) {
            fallbackOverlay?.hide()
            return
        }
        when (SessionRepository.verdict(front)) {
            Verdict.BLOCK -> ensureFallbackOverlay().show(labelOf(front))
            Verdict.HOME -> ensureFallbackOverlay().showHome()
            Verdict.ALLOW -> fallbackOverlay?.hide()
        }
    }

    private fun ensureFallbackOverlay(): BlockOverlay =
        fallbackOverlay ?: BlockOverlay(this, onDismiss = {
            // No GLOBAL_ACTION_HOME without the accessibility service, so go home by intent.
            fallbackOverlay?.hide()
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }).also { fallbackOverlay = it }

    private fun labelOf(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                .toString()
        }.getOrDefault("That app")
    }

    /** Snibbed but the accessibility service is not bound: enforcement is weaker than the user thinks. */
    private fun isDegraded(): Boolean =
        SessionRepository.isSnibbed && !BlockingService.isConnected

    @SuppressLint("MissingPermission")
    private fun renotify() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val onBreak = SessionRepository.onBreak()
        val degraded = isDegraded()

        if (degraded) {
            val fix = PendingIntent.getActivity(
                this, 1,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(this, SnibApp.CHANNEL_BREAK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Snibbed, but not blocking")
                .setContentText("Snib is off in Accessibility. Tap to turn it back on.")
                .setContentIntent(fix)
                .setOngoing(true)
                .setColorized(true)
                .setColor(getColor(R.color.rust))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(
            this,
            if (onBreak) SnibApp.CHANNEL_BREAK else SnibApp.CHANNEL_SESSION,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setSilent(!onBreak)
            .setPriority(if (onBreak) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        return if (onBreak) {
            val endsAt = System.currentTimeMillis() + SessionRepository.breakRemainingMs()
            builder
                .setContentTitle("Emergency break")
                .setContentText("Snib comes back on in")
                .setWhen(endsAt)
                .setChronometerCountDown(true)
                .setColorized(true)
                .setColor(getColor(R.color.rust))
                .build()
        } else {
            builder
                .setContentTitle("Snibbed")
                .setContentText("Tap your token to open up.")
                .setWhen(SessionRepository.snibbedAt ?: System.currentTimeMillis())
                .setColor(getColor(R.color.brass))
                .build()
        }
    }

    private fun stopEnforcing() {
        poller?.cancel()
        breakWatcher?.cancel()
        fallbackOverlay?.hide()
        fallbackOverlay = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopEnforcing()
        scope.cancel()
        super.onDestroy()
    }
}
