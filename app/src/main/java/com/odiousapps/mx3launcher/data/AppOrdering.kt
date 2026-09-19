package com.odiousapps.mx3launcher.data

/**
 * Applies the user's saved order to the raw installed-app list. Apps not yet
 * in the saved order are appended alphabetically; saved entries no longer
 * installed are dropped.
 */
fun orderApps(installed: List<AppEntry>, appOrder: List<String>): List<AppEntry> {
    val byPackage = installed.associateBy { it.packageName }
    val ordered = appOrder.mapNotNull { byPackage[it] }
    val orderedPackages = ordered.map { it.packageName }.toSet()
    val remaining = installed.filter { it.packageName !in orderedPackages }
    return ordered + remaining
}

/** Same ordering minus hidden apps -- what the home grid shows. The settings
 *  screen uses [orderApps] unfiltered so hidden apps can still be re-shown. */
fun visibleOrderedApps(installed: List<AppEntry>, settings: LauncherSettings): List<AppEntry> =
    orderApps(installed, settings.appOrder).filter { it.packageName !in settings.hiddenPackages }
