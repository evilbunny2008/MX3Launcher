package com.odiousapps.mx3launcher

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import androidx.core.content.ContextCompat
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
        // Requests opening straight to Settings instead of the home grid; set by
        // Button Mapper (see ButtonMapperService.kt's launchLauncherSettings()).
        const val EXTRA_OPEN_SETTINGS = "com.odiousapps.mx3launcher.OPEN_SETTINGS"

        // Read by ScreenWakeGuardService (same process) to tell whether its
        // direct startActivity() actually worked - see bringLauncherToFront().
        @Volatile private var isResumed = false
        @Volatile private var lastResumedAt = 0L

        fun hasResumedSince(elapsedRealtime: Long): Boolean =
            isResumed || lastResumedAt >= elapsedRealtime
    }

    // Held here rather than remember{} so onKeyDown() below, which is outside
    // Compose, can read/write it directly; still recomposes on write.
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

    override fun onResume() {
        super.onResume()
        isResumed = true
        lastResumedAt = SystemClock.elapsedRealtime()
        // We're in front, so any pending full-screen-intent fallback from
        // ScreenWakeGuardService is redundant - don't leave it in the shade.
        getSystemService(NotificationManager::class.java)
            ?.cancel(ScreenWakeGuardService.WAKE_NOTIFICATION_ID)
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    /**
     * Also started on launch, not just on boot via BootReceiver.kt, so the
     * wake-guard is active immediately after install rather than only after a reboot.
     */
    private fun startWakeGuardService() {
        startForegroundService(Intent(this, ScreenWakeGuardService::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        // API 33+ only: required for ScreenWakeGuardService's notifications to show.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * android:launchMode="singleTask" routes a new launch intent (e.g. Button
     * Mapper relaunching us) here instead of onCreate() when already running.
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

    // Keeps installedApps in sync with install/uninstall while running, rather
    // than only reloading on cold start; also a prerequisite for pruning below.
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
        // ContextCompat handles the API 33+ export flag and plain registration
        // below that alike, so the receiver is always registered - matching the
        // unconditional unregister below, which would otherwise throw.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val settings by LauncherPreferences.observe(context)
        .collectAsState(initial = LauncherSettings(ThemeMode.SYSTEM, "slate", 6, emptySet(), emptyList()))
    val configSyncDeviceToken by LauncherPreferences.observeConfigSyncDeviceToken(context)
        .collectAsState(initial = "")

    // Suppresses Back on Home, which otherwise finishes the Activity and lets the
    // TV fall through to the system's other Home app. Composed unconditionally
    // (enabled tied to `screen`) so it doesn't fight with the other screens' own
    // BackHandlers when they're active.
    androidx.activity.compose.BackHandler(enabled = screen == Screen.Home) {}

    // appOrder/hiddenPackages only ever grow in storage (orderApps() filters stale
    // entries at display time, so the grid looks fine regardless, but backups would
    // keep old references forever). Prune once installedApps is known; the
    // inequality check avoids a write-triggers-reload-triggers-write loop.
    //
    // Writes via the targeted setAppOrder/setHiddenPackages below, NOT
    // restoreAll(settings.copy(...)) - this effect re-runs on every settings
    // change (it's keyed on `settings`, not just installedApps), so an
    // in-flight instance can easily still be holding a stale `settings`
    // snapshot from before some unrelated field (e.g. soundbar pairing's
    // URL/secret, or a gradient/theme change) was written elsewhere. A full
    // restoreAll() from that stale snapshot landing afterwards would silently
    // revert that unrelated write back to blank - this actually happened
    // with soundbar pairing appearing to succeed then revert. Per-key
    // setters can't clobber fields they don't touch, race or not.
    LaunchedEffect(installedApps, settings) {
        if (installedApps.isEmpty()) return@LaunchedEffect
        val installedPackages = installedApps.map { it.packageName }.toSet()
        val prunedOrder = settings.appOrder.filter { it in installedPackages }
        val prunedHidden = settings.hiddenPackages.filter { it in installedPackages }.toSet()
        if (prunedOrder != settings.appOrder) {
            LauncherPreferences.setAppOrder(context, prunedOrder)
        }
        if (prunedHidden != settings.hiddenPackages) {
            LauncherPreferences.setHiddenPackages(context, prunedHidden)
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
                soundbarIrCodesToSend = settings.soundbarIrCodesToSend,
                soundbarCheckCurrent = settings.soundbarCheckCurrent,
            )

            Screen.Settings -> SettingsScreen(
                settings = settings,
                onThemeModeChange = { mode -> scope.launch { LauncherPreferences.setThemeMode(context, mode) } },
                onGradientChange = { id -> scope.launch { LauncherPreferences.setGradient(context, id) } },
                onColumnsChange = { columns -> scope.launch { LauncherPreferences.setColumns(context, columns) } },
                onOpenAppDisplaySettings = { screen = Screen.AppDisplaySettings },
                // Suspends until the DataStore write actually completes, so
                // SettingsScreen's "Restored from ..." status is trustworthy
                // rather than reported the instant the write is scheduled.
                onRestore = { restored -> LauncherPreferences.restoreAll(context, restored) },
                configSyncDeviceToken = configSyncDeviceToken,
                onConfigSyncDeviceTokenChange = { token ->
                    scope.launch { LauncherPreferences.setConfigSyncDeviceToken(context, token) }
                },
                onSoundbarWakeEnabledChange = { enabled ->
                    scope.launch { LauncherPreferences.setSoundbarWakeEnabled(context, enabled) }
                },
                onSoundbarWakePairingChange = { url, secret, irCodesToSend, checkCurrent ->
                    scope.launch {
                        LauncherPreferences.setSoundbarWakePairing(context, url, secret, irCodesToSend, checkCurrent)
                    }
                },
                onBack = { screen = Screen.Home },
            )

            Screen.AppDisplaySettings -> AppDisplaySettingsScreen(
                allApps = orderApps(installedApps, settings.appOrder),
                hiddenPackages = settings.hiddenPackages,
                // Both compute from the stored value inside the DataStore
                // transaction, not from the `settings` snapshot: rapid remote
                // presses otherwise all start from the same stale snapshot
                // before the first write lands, and all but one get lost.
                onToggleVisibility = { packageName ->
                    scope.launch {
                        LauncherPreferences.updateHiddenPackages(context) { hidden ->
                            if (packageName in hidden) hidden - packageName else hidden + packageName
                        }
                    }
                },
                onMove = { packageName, direction ->
                    val installed = installedApps
                    scope.launch {
                        LauncherPreferences.updateAppOrder(context) { storedOrder ->
                            val current = orderApps(installed, storedOrder).map { it.packageName }.toMutableList()
                            val index = current.indexOf(packageName)
                            val newIndex = index + direction
                            if (index >= 0 && newIndex in current.indices) {
                                current[index] = current[newIndex].also { current[newIndex] = current[index] }
                            }
                            current
                        }
                    }
                },
                onBack = { screen = Screen.Settings },
            )
        }
    }
}
