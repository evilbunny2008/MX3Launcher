package com.odiousapps.mx3launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BOOT_COMPLETED is one of the few implicit broadcasts Android still
 * delivers to manifest-declared (static) receivers -- unlike SCREEN_ON,
 * which ScreenWakeGuardService.kt registers dynamically instead. This
 * receiver's only job is starting that foreground service so it's
 * already running and able to catch screen-on events before any standby
 * cycle happens.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, ScreenWakeGuardService::class.java))
        }
    }
}
