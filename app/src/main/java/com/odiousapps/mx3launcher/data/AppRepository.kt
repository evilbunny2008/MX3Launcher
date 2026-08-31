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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds every launchable app, one entry per package. Where an app has a
 * dedicated CATEGORY_LEANBACK_LAUNCHER entry (a TV-specific home-screen
 * icon, distinct from its regular phone/tablet launcher icon) that's used;
 * otherwise falls back to its plain CATEGORY_LAUNCHER entry, so sideloaded
 * apps without TV-specific packaging still show up.
 *
 * Querying other apps' launchable activities like this needs the
 * <queries> declarations in AndroidManifest_snippet.xml -- without them,
 * package-visibility filtering (API 30+) makes queryIntentActivities()
 * silently return nothing for apps outside your own package, the same
 * failure mode covered in Button Mapper's README.
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
