package com.odiousapps.mx3launcher.data

import android.graphics.drawable.Drawable

/**
 * One launchable app, as shown in the grid or the app-display settings list.
 * `icon` is loaded lazily by AppRepository, not held as a Bitmap, to avoid
 * decoding cost for hidden apps.
 */
data class AppEntry(
    val packageName: String,
    val activityClassName: String,
    val label: String,
    val icon: Drawable,
)
