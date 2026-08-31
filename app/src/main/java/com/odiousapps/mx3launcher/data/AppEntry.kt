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

package com.odiousapps.mx3launcher.data

import android.graphics.drawable.Drawable

/**
 * One launchable app, as shown in the grid or the app-display settings
 * list. `icon` is loaded lazily/cached by AppRepository, not held as a
 * Bitmap here, to avoid decoding cost for apps that are hidden.
 */
data class AppEntry(
    val packageName: String,
    val activityClassName: String,
    val label: String,
    val icon: Drawable,
)
