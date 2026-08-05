package com.snib.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.snib.app.data.SessionRepository

class SnibApp : Application() {

    companion object {
        const val CHANNEL_SESSION = "session"
        const val CHANNEL_BREAK = "break"
    }

    override fun onCreate() {
        super.onCreate()
        SessionRepository.init(this)
        AppState.load(this)
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION,
                getString(R.string.channel_session_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.channel_session_description)
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BREAK,
                getString(R.string.channel_break_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_break_description)
                setShowBadge(false)
            }
        )
    }
}
