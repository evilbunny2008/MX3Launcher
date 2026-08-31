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
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.odiousapps.mx3launcher.data.AppEntry
import com.odiousapps.mx3launcher.data.AppRepository
import com.odiousapps.mx3launcher.data.LauncherPreferences
import com.odiousapps.mx3launcher.data.LauncherSettings
import com.odiousapps.mx3launcher.data.ThemeMode
import com.odiousapps.mx3launcher.data.orderApps
import com.odiousapps.mx3launcher.data.visibleOrderedApps
import com.odiousapps.mx3launcher.ui.AppDisplaySettingsScreen
import com.odiousapps.mx3launcher.ui.AppGridScreen
import com.odiousapps.mx3launcher.ui.SettingsScreen
import com.odiousapps.mx3launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch

private sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    object AppDisplaySettings : Screen()
}

class MainActivity : ComponentActivity() {

    companion object {
        // Set by Button Mapper (or anything else) to request opening
        // straight to the settings screen instead of the home grid --
        // see ButtonMapperService.kt's launchLauncherSettings().
        const val EXTRA_OPEN_SETTINGS = "com.odiousapps.mx3launcher.OPEN_SETTINGS"
    }

    // Held here (not as remember{} inside the composable) specifically so
    // onKeyDown() below -- which lives outside Compose entirely -- can
    // read and write it directly. Still fully reactive from Compose's
    // side: a MutableState triggers recomposition on write regardless of
    // where it was created, as long as it's read within a @Composable.
    private val screenState: MutableState<Screen> = mutableStateOf(Screen.Home)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyIntent(intent)
        requestNotificationPermissionIfNeeded()
        startWakeGuardService()
        setContent {
            LauncherApp(screenState)
        }
    }

    /**
     * Starts immediately on launch too, not just on the next boot via
     * BootReceiver.kt -- otherwise the wake-guard feature wouldn't take
     * effect until after a reboot, even though the app was just
     * installed and opened.
     */
    private fun startWakeGuardService() {
        startForegroundService(Intent(this, ScreenWakeGuardService::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        // Only needed on API 33+ -- ScreenWakeGuardService's notifications
        // (both the persistent "active" one and the wake-trigger
        // fallback) won't show without this on newer Android.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * android:launchMode="singleTask" means a NEW launch intent arriving
     * while this Activity is already running (e.g. Button Mapper
     * launching us a second time) does NOT go through onCreate() again --
     * it comes here instead, on the existing instance. Without this
     * override, the settings-jump would only ever work on a cold start
     * of the launcher, not when it's already alive in the background.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            screenState.value = Screen.Settings
        }
    }
}

@Composable
private fun LauncherApp(screenState: MutableState<Screen>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by screenState
    var installedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        installedApps = AppRepository.loadInstalledApps(context)
    }

    // Without this, installedApps only ever loads once on cold start --
    // uninstalling (or installing) an app while the launcher is already
    // running wouldn't be reflected until the next restart, which is
    // also why stale package references never got pruned below (this
    // effect running is a prerequisite for that).
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                scope.launch {
                    installedApps = AppRepository.loadInstalledApps(context)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    val settings by LauncherPreferences.observe(context)
        .collectAsState(initial = LauncherSettings(ThemeMode.SYSTEM, "slate", 6, emptySet(), emptyList()))

    // Home is the root of this app -- Back should do nothing there, same
    // as every other launcher. Without this, Android's default back
    // behaviour finishes the Activity entirely once on the Home screen
    // (nothing else intercepts it, unlike Settings/AppDisplaySettings,
    // which each have their own BackHandler navigating back to Home).
    // Finishing here is exactly what let the TV fall through to
    // whatever the system's other Home app is (Google's built-in
    // launcher) instead of staying on MX3 Launcher. Composed
    // unconditionally with `enabled` tied to the current screen, rather
    // than only composed while on Home, so toggling it on/off as the
    // screen changes doesn't fight with Settings/AppDisplaySettings's
    // own BackHandlers when THEY'RE the active screen.
    androidx.activity.compose.BackHandler(enabled = screen == Screen.Home) {}

    // Stored appOrder/hiddenPackages only ever grow -- nothing prunes an
    // entry when its package gets uninstalled (orderApps() already
    // filters stale entries out at DISPLAY time, which is why the grid
    // itself looks fine regardless, but the underlying stored data, and
    // therefore any backup taken from it, kept the old reference
    // indefinitely). Once installedApps is known, drop anything from
    // storage that's no longer actually installed. Guarded by an
    // inequality check so this only writes when there's an actual
    // change -- otherwise the settings Flow re-emitting after a write
    // would just trigger this effect again in a tight loop.
    LaunchedEffect(installedApps, settings) {
        if (installedApps.isEmpty()) return@LaunchedEffect
        val installedPackages = installedApps.map { it.packageName }.toSet()
        val prunedOrder = settings.appOrder.filter { it in installedPackages }
        val prunedHidden = settings.hiddenPackages.filter { it in installedPackages }.toSet()
        if (prunedOrder != settings.appOrder || prunedHidden != settings.hiddenPackages) {
            LauncherPreferences.restoreAll(
                context,
                settings.copy(appOrder = prunedOrder, hiddenPackages = prunedHidden),
            )
        }
    }

    LauncherTheme(themeMode = settings.themeMode, gradientId = settings.gradientId) {
        when (screen) {
            Screen.Home -> AppGridScreen(
                apps = visibleOrderedApps(installedApps, settings),
                columns = settings.columns,
                onOpenAppSettings = { screen = Screen.Settings },
                soundbarWakeEnabled = settings.soundbarWakeEnabled,
                soundbarWakeUrl = settings.soundbarWakeUrl,
                soundbarWakeSecret = settings.soundbarWakeSecret,
            )

            Screen.Settings -> SettingsScreen(
                settings = settings,
                onThemeModeChange = { mode -> scope.launch { LauncherPreferences.setThemeMode(context, mode) } },
                onGradientChange = { id -> scope.launch { LauncherPreferences.setGradient(context, id) } },
                onColumnsChange = { columns -> scope.launch { LauncherPreferences.setColumns(context, columns) } },
                onOpenAppDisplaySettings = { screen = Screen.AppDisplaySettings },
                onRestore = { restored -> scope.launch { LauncherPreferences.restoreAll(context, restored) } },
                onSoundbarWakeEnabledChange = { enabled ->
                    scope.launch { LauncherPreferences.setSoundbarWakeEnabled(context, enabled) }
                },
                onSoundbarWakeUrlChange = { url ->
                    scope.launch { LauncherPreferences.setSoundbarWakeUrl(context, url) }
                },
                onSoundbarWakeSecretChange = { secret ->
                    scope.launch { LauncherPreferences.setSoundbarWakeSecret(context, secret) }
                },
                onBack = { screen = Screen.Home },
            )

            Screen.AppDisplaySettings -> AppDisplaySettingsScreen(
                allApps = orderApps(installedApps, settings.appOrder),
                hiddenPackages = settings.hiddenPackages,
                onToggleVisibility = { packageName ->
                    val newHidden = if (packageName in settings.hiddenPackages) {
                        settings.hiddenPackages - packageName
                    } else {
                        settings.hiddenPackages + packageName
                    }
                    scope.launch { LauncherPreferences.setHiddenPackages(context, newHidden) }
                },
                onMove = { packageName, direction ->
                    val current = orderApps(installedApps, settings.appOrder).map { it.packageName }.toMutableList()
                    val index = current.indexOf(packageName)
                    val newIndex = index + direction
                    if (index >= 0 && newIndex in current.indices) {
                        current[index] = current[newIndex].also { current[newIndex] = current[index] }
                        scope.launch { LauncherPreferences.setAppOrder(context, current) }
                    }
                },
                onBack = { screen = Screen.Settings },
            )
        }
    }
}
