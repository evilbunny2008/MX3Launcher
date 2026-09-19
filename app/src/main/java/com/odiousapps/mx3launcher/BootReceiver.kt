package com.odiousapps.mx3launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BOOT_COMPLETED is one of the few implicit broadcasts still delivered to a
 * static manifest receiver (unlike SCREEN_ON, registered dynamically by
 * ScreenWakeGuardService.kt). Just starts that foreground service early.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, ScreenWakeGuardService::class.java))
        }
    }
}
