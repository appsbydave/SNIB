package com.snib.app.service

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Optional uninstall friction, and nothing else. Snib asks for no policy it does not use, and it
 * is never a Device Owner — that path was removed from the design deliberately.
 */
class SnibDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun component(context: Context) =
            ComponentName(context, SnibDeviceAdminReceiver::class.java)
    }

    override fun onDisableRequested(context: Context, intent: android.content.Intent): CharSequence =
        "Snib will be easier to uninstall after this."
}
