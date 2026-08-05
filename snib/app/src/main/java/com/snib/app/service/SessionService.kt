package com.snib.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snib.app.MainActivity
import com.snib.app.R
import com.snib.app.SnibApp
import com.snib.app.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the session alive and visible. Low-priority ongoing notification, no dismiss.
 *
 * The elapsed time is drawn by the system chronometer rather than a ticking coroutine — a snib
 * that lasts nine hours should not cost nine hours of wakeups.
 */
class SessionService : Service() {

    companion object {
        const val ACTION_SYNC = "com.snib.app.SYNC"
        private const val NOTIFICATION_ID = 1

        fun sync(context: Context) {
            val intent = Intent(context, SessionService::class.java).setAction(ACTION_SYNC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob())
    private var breakWatcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SessionRepository.ensureLoaded(this)

        if (!SessionRepository.isSnibbed) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        watchBreak()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun watchBreak() {
        breakWatcher?.cancel()
        val endsAt = SessionRepository.breakEndsAt() ?: return
        breakWatcher = scope.launch {
            delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0))
            // Silent re-snib at expiry. No thirty-second warning; the break should not be
            // something you learn to ride to the last second.
            if (SessionRepository.isSnibbed) {
                NotificationManagerCompat.from(this@SessionService)
                    .notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    private fun buildNotification(): Notification {
        val onBreak = SessionRepository.onBreak()
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
            val endsAt = SessionRepository.breakEndsAt() ?: System.currentTimeMillis()
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

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
