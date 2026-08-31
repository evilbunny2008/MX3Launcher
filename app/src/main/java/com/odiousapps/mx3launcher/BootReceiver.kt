/*
 * This file is part of MX3 Launcher.
 * Copyright (C) 2026 the MX3 Launcher contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
