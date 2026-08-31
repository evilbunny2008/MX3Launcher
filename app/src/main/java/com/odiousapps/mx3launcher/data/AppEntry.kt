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
