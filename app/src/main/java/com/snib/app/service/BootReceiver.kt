package com.snib.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snib.app.data.SessionRepository

/**
 * Reboot into a still-snibbed state.
 *
 * There is a short window between boot and this receiver firing where a blocked app will open.
 * It cannot be closed from userspace. Keep it small and be honest about it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                SessionRepository.init(context)
                if (SessionRepository.isSnibbed) SessionService.sync(context)
            }
        }
    }
}
