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

/**
 * Applies the user's saved order to the raw installed-app list. Apps
 * newly discovered since the order was last saved (freshly installed, or
 * this is first launch and appOrder is empty) are appended at the end in
 * their default alphabetical order. Apps that were saved in the order but
 * are no longer installed are simply dropped -- no error, no special
 * handling needed.
 */
fun orderApps(installed: List<AppEntry>, appOrder: List<String>): List<AppEntry> {
    val byPackage = installed.associateBy { it.packageName }
    val ordered = appOrder.mapNotNull { byPackage[it] }
    val orderedPackages = ordered.map { it.packageName }.toSet()
    val remaining = installed.filter { it.packageName !in orderedPackages }
    return ordered + remaining
}

/** Same ordering, minus anything the user has hidden -- this is what the
 *  home grid actually shows. The settings screen uses [orderApps] alone
 *  (unfiltered) so hidden apps can still be found and re-shown. */
fun visibleOrderedApps(installed: List<AppEntry>, settings: LauncherSettings): List<AppEntry> =
    orderApps(installed, settings.appOrder).filter { it.packageName !in settings.hiddenPackages }
