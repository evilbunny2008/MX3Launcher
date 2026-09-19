package com.odiousapps.mx3launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds every launchable app, one entry per package. Prefers a dedicated
 * CATEGORY_LEANBACK_LAUNCHER entry (TV-specific icon); falls back to
 * CATEGORY_LAUNCHER so sideloaded apps without TV packaging still show up.
 *
 * Requires the <queries> declarations in AndroidManifest_snippet.xml —
 * without them, API 30+ package-visibility filtering makes
 * queryIntentActivities() silently return nothing for other packages.
 */
object AppRepository {

    suspend fun loadInstalledApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val ownPackage = context.packageName

        val leanbackApps = queryLaunchable(pm, Intent.CATEGORY_LEANBACK_LAUNCHER)
        val regularApps = queryLaunchable(pm, Intent.CATEGORY_LAUNCHER)

        // Prefer the leanback entry per package; fall back to regular.
        val byPackage = LinkedHashMap<String, AppEntry>()
        for (entry in leanbackApps) byPackage[entry.packageName] = entry
        for (entry in regularApps) {
            if (entry.packageName !in byPackage) byPackage[entry.packageName] = entry
        }

        byPackage.values
            .filter { it.packageName != ownPackage } // don't list ourselves
            .sortedBy { it.label.lowercase() }
    }

    private fun queryLaunchable(pm: PackageManager, category: String): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(intent, 0).mapNotNull { resolveInfo ->
            try {
                AppEntry(
                    packageName = resolveInfo.activityInfo.packageName,
                    activityClassName = resolveInfo.activityInfo.name,
                    label = resolveInfo.loadLabel(pm).toString(),
                    icon = resolveInfo.loadIcon(pm),
                )
            } catch (_: Exception) {
                null // a broken/uninstalling package shouldn't crash the whole grid
            }
        }
    }
}
