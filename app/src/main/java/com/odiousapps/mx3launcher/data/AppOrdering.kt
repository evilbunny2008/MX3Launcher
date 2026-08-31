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
