package com.odiousapps.mx3launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.odiousapps.mx3launcher.data.GRADIENT_PRESETS
import com.odiousapps.mx3launcher.data.LauncherConfigSync
import com.odiousapps.mx3launcher.data.LauncherSettings
import com.odiousapps.mx3launcher.data.SoundbarPairing
import com.odiousapps.mx3launcher.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private val COLUMN_OPTIONS = listOf(5, 6, 7)

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onGradientChange: (String) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onOpenAppDisplaySettings: () -> Unit,
    onRestore: suspend (LauncherSettings) -> Unit,
    configSyncDeviceToken: String,
    onConfigSyncDeviceTokenChange: (String) -> Unit,
    onSoundbarWakeEnabledChange: (Boolean) -> Unit,
    onSoundbarWakePairingChange: (url: String, secret: String, irCodesToSend: String, checkCurrent: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Text(text = "Launcher settings")

        SettingsSection(title = "Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Button(
                        onClick = { onThemeModeChange(mode) },
                    ) {
                        Text(text = mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        SettingsSection(title = "Background gradient") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GRADIENT_PRESETS.forEach { preset ->
                    GradientSwatch(
                        preset = preset,
                        selected = preset.id == settings.gradientId,
                        onClick = { onGradientChange(preset.id) },
                    )
                }
            }
        }

        SettingsSection(title = "App columns per row") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                COLUMN_OPTIONS.forEach { option ->
                    Button(
                        onClick = { onColumnsChange(option) },
                    ) {
                        Text(text = "$option${if (option == settings.columns) " ✓" else ""}")
                    }
                }
            }
        }

        SettingsSection(title = "App display") {
            Button(onClick = onOpenAppDisplaySettings) {
                Text(text = "Show/hide apps and set their order")
            }
        }

        // See checkSoundbarWake() in AppGridScreen.kt.
        SettingsSection(title = "Soundbar wake") {
            Button(onClick = { onSoundbarWakeEnabledChange(!settings.soundbarWakeEnabled) }) {
                Text(text = if (settings.soundbarWakeEnabled) "Enabled ✓" else "Disabled")
            }

            SoundbarPairingSection(
                settings = settings,
                onSoundbarWakePairingChange = onSoundbarWakePairingChange,
            )
        }

        // See LauncherConfigSync.kt: pair once over the same
        // sync.odiousapps.com credential relay SoundbarPairingSection
        // uses below, then back up/restore directly - no code/QR per
        // operation, unlike soundbar pairing.
        SettingsSection(title = "Backup & restore") {
            ConfigSyncSection(
                settings = settings,
                deviceToken = configSyncDeviceToken,
                onDeviceTokenChange = onConfigSyncDeviceTokenChange,
                onRestore = onRestore,
            )

            Button(onClick = {
                scope.launch {
                    // Reset is just a restore to the same defaults used for
                    // collectAsState's initial state in MainActivity.kt.
                    onRestore(LauncherSettings(ThemeMode.SYSTEM, GRADIENT_PRESETS.first().id, 6, emptySet(), emptyList()))
                }
            }) {
                Text(text = "Reset to defaults")
            }
        }

        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title)
        content()
    }
}

@Composable
private fun GradientSwatch(
    preset: com.odiousapps.mx3launcher.data.GradientPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(preset.start, preset.end)
                    )
                )
        ) {
            if (selected) {
                Text(text = "✓", modifier = Modifier.padding(4.dp))
            }
        }
    }
}

private sealed class PairingUiState {
    object Idle : PairingUiState()
    object Starting : PairingUiState()
    data class ShowingCode(
        val code: String,
        val token: String,
        val qrBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    ) : PairingUiState()
    data class Failed(val message: String) : PairingUiState()
    // Only used by BackupSyncSection/RestoreSyncSection below — a transient
    // success message shown alongside the next "start again" button, unlike
    // SoundbarPairingSection, whose success instead flips settings into a
    // persistent "Paired ✓" state.
    data class Done(val message: String) : PairingUiState()
}

/** The QR/code display shared by every PairingUiState.ShowingCode, below. */
@Composable
private fun PairingCodeDisplay(state: PairingUiState.ShowingCode) {
    // Newly-appearing content nested this deep inside SettingsScreen's outer
    // verticalScroll Column doesn't otherwise pull the scroll position along
    // with it — nothing here is TV-focusable to trigger the usual
    // focus-follows-scroll behaviour, so without this the QR/code can end up
    // partially or fully below the bottom of the screen the moment it appears.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { bringIntoViewRequester.bringIntoView() }

    Column(
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.qrBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = state.qrBitmap,
                contentDescription = "QR code to open the pairing page",
                modifier = Modifier.size(200.dp),
            )
            Text(text = "Scan with your phone's camera, or go to:")
        } else {
            // QR generation failed — fall back to plain text.
            Text(text = "On your phone, go to:")
        }
        Text(text = "sync.odiousapps.com/credential_view.php")
        Text(text = "and enter this code:")
        // Large, bold, and letter-spaced — this needs to be read at normal
        // TV sitting distance, not proofread up close like body text.
        Text(
            text = state.code,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp,
        )
    }
}

/**
 * Device-code-style pairing UI: the TV only ever shows a short code and
 * polls in the background, with the real URL+secret typed on a phone
 * instead. This also avoids the D-pad trap of on-screen TextFields, where
 * focused text-edit mode captures Down for cursor movement rather than
 * moving focus to whatever's below.
 */
@Composable
private fun SoundbarPairingSection(
    settings: LauncherSettings,
    onSoundbarWakePairingChange: (url: String, secret: String, irCodesToSend: String, checkCurrent: Boolean) -> Unit,
) {
    val isPaired = settings.soundbarWakeUrl.isNotBlank() && settings.soundbarWakeSecret.isNotBlank()
    var pairingState by remember { mutableStateOf<PairingUiState>(PairingUiState.Idle) }
    val pairingScope = androidx.compose.runtime.rememberCoroutineScope()

    if (isPaired) {
        Text(text = "Paired ✓")
        Button(onClick = {
            onSoundbarWakePairingChange("", "", com.odiousapps.mx3launcher.data.DEFAULT_SOUNDBAR_IR_CODES, true)
            pairingState = PairingUiState.Idle
        }) {
            Text(text = "Forget pairing")
        }
        return
    }

    when (val state = pairingState) {
        is PairingUiState.Idle, is PairingUiState.Failed -> {
            if (state is PairingUiState.Failed) {
                Text(text = state.message)
            }
            Button(onClick = {
                pairingState = PairingUiState.Starting
                pairingScope.launch {
                    val session = withContext(Dispatchers.IO) { SoundbarPairing.startPairing() }
                    if (session == null) {
                        pairingState = PairingUiState.Failed(
                            "Couldn't start pairing — check the server is reachable"
                        )
                        return@launch
                    }
                    val approveUrl = "https://sync.odiousapps.com/credential_view.php?code=${session.code}"
                    val qrBitmap = withContext(Dispatchers.Default) { generateQrCodeBitmap(approveUrl) }
                    pairingState = PairingUiState.ShowingCode(session.code, session.token, qrBitmap)

                    val deadlineMs = System.currentTimeMillis() + session.expiresInSeconds * 1000L
                    while (System.currentTimeMillis() < deadlineMs) {
                        delay(3000.milliseconds)
                        when (val result = withContext(Dispatchers.IO) {
                            SoundbarPairing.pollPairing(session.token)
                        }) {
                            is SoundbarPairing.PollResult.Approved -> {
                                onSoundbarWakePairingChange(
                                    result.url,
                                    result.secret,
                                    result.irCodesToSend,
                                    result.checkCurrent,
                                )
                                pairingState = PairingUiState.Idle
                                return@launch
                            }
                            is SoundbarPairing.PollResult.Expired -> {
                                pairingState = PairingUiState.Failed("Code expired — try again")
                                return@launch
                            }
                            is SoundbarPairing.PollResult.Error -> {
                                // Keep polling; don't give up on a transient blip.
                            }
                            SoundbarPairing.PollResult.Pending -> {
                                // Keep waiting.
                            }
                        }
                    }
                    pairingState = PairingUiState.Failed("Code expired — try again")
                }
            }) {
                Text(text = "Pair")
            }
        }
        is PairingUiState.Starting -> {
            Text(text = "Starting...")
        }
        is PairingUiState.ShowingCode -> PairingCodeDisplay(state)
        is PairingUiState.Done -> {
            // SoundbarPairingSection never produces this state — success
            // flips settings into the "Paired ✓" branch above instead.
        }
    }
}

private sealed class RestoreListUiState {
    object Idle : RestoreListUiState()
    object Loading : RestoreListUiState()
    data class Loaded(val backups: List<LauncherConfigSync.RemoteBackup>) : RestoreListUiState()
    data class Failed(val message: String) : RestoreListUiState()
}

/**
 * Pair once (same device-code UI as SoundbarPairingSection above), then
 * back up/restore directly against device_backup.php and friends — no
 * further code/QR per operation, unlike soundbar pairing or the old
 * per-backup pairing this replaced. See LauncherConfigSync.kt.
 */
@Composable
private fun ConfigSyncSection(
    settings: LauncherSettings,
    deviceToken: String,
    onDeviceTokenChange: (String) -> Unit,
    onRestore: suspend (LauncherSettings) -> Unit,
) {
    if (deviceToken.isBlank()) {
        DevicePairingSection(onPaired = onDeviceTokenChange)
        return
    }

    val scope = rememberCoroutineScope()
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var restoreState by remember { mutableStateOf<RestoreListUiState>(RestoreListUiState.Idle) }

    Text(text = "Paired ✓")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            onDeviceTokenChange("")
            restoreState = RestoreListUiState.Idle
            syncStatus = null
        }) {
            Text(text = "Forget pairing")
        }
        Button(onClick = {
            scope.launch {
                syncStatus = "Backing up..."
                val ok = withContext(Dispatchers.IO) { LauncherConfigSync.uploadBackup(deviceToken, settings) }
                syncStatus = if (ok) "Backed up" else "Backup failed — check the server is reachable"
            }
        }) {
            Text(text = "Back up settings")
        }
        Button(onClick = {
            scope.launch {
                restoreState = RestoreListUiState.Loading
                val backups = withContext(Dispatchers.IO) { LauncherConfigSync.listBackups(deviceToken) }
                restoreState = if (backups == null) {
                    RestoreListUiState.Failed("Couldn't load backups — check the server is reachable")
                } else {
                    RestoreListUiState.Loaded(backups)
                }
            }
        }) {
            Text(text = "Restore settings")
        }
    }
    Text(text = "Manage or revoke this pairing any time at sync.odiousapps.com/paired_devices.php")
    syncStatus?.let { Text(text = it) }

    when (val s = restoreState) {
        RestoreListUiState.Idle -> {}
        RestoreListUiState.Loading -> Text(text = "Loading...")
        is RestoreListUiState.Failed -> Text(text = s.message)
        is RestoreListUiState.Loaded -> {
            if (s.backups.isEmpty()) {
                Text(text = "No backups yet")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.backups.forEach { backup ->
                        Button(onClick = {
                            scope.launch {
                                val fetched = withContext(Dispatchers.IO) {
                                    LauncherConfigSync.fetchBackup(deviceToken, backup.id)
                                }
                                if (fetched != null) {
                                    // Awaited, not fired-and-forgotten, so this
                                    // status message reflects the settings
                                    // write actually completing.
                                    onRestore(fetched)
                                    restoreState = RestoreListUiState.Idle
                                    syncStatus = "Restored from ${backup.label}"
                                } else {
                                    restoreState = RestoreListUiState.Failed("Couldn't read that backup")
                                }
                            }
                        }) {
                            Text(text = backup.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicePairingSection(onPaired: (String) -> Unit) {
    var pairingState by remember { mutableStateOf<PairingUiState>(PairingUiState.Idle) }
    val scope = rememberCoroutineScope()

    when (val state = pairingState) {
        is PairingUiState.Idle, is PairingUiState.Failed -> {
            if (state is PairingUiState.Failed) {
                Text(text = state.message)
            }
            Text(text = "Pair once to back up/restore settings without a code each time.")
            Button(onClick = {
                pairingState = PairingUiState.Starting
                scope.launch {
                    val session = withContext(Dispatchers.IO) { LauncherConfigSync.pairDevice() }
                    if (session == null) {
                        pairingState = PairingUiState.Failed(
                            "Couldn't start pairing — check the server is reachable"
                        )
                        return@launch
                    }
                    val qrBitmap = withContext(Dispatchers.Default) { generateQrCodeBitmap(session.approveUrl) }
                    pairingState = PairingUiState.ShowingCode(session.code, session.token, qrBitmap)

                    val deadlineMs = System.currentTimeMillis() + session.expiresInSeconds * 1000L
                    while (System.currentTimeMillis() < deadlineMs) {
                        delay(3000.milliseconds)
                        when (val result = withContext(Dispatchers.IO) {
                            LauncherConfigSync.pollPairing(session.token)
                        }) {
                            is LauncherConfigSync.PairingPollResult.Paired -> {
                                onPaired(result.deviceToken)
                                pairingState = PairingUiState.Idle
                                return@launch
                            }
                            is LauncherConfigSync.PairingPollResult.Expired -> {
                                pairingState = PairingUiState.Failed("Code expired — try again")
                                return@launch
                            }
                            is LauncherConfigSync.PairingPollResult.Error -> {
                                // Keep polling; don't give up on a transient blip.
                            }
                            LauncherConfigSync.PairingPollResult.Pending -> {
                                // Keep waiting.
                            }
                        }
                    }
                    pairingState = PairingUiState.Failed("Code expired — try again")
                }
            }) {
                Text(text = "Pair")
            }
        }
        is PairingUiState.Starting -> {
            Text(text = "Starting...")
        }
        is PairingUiState.ShowingCode -> PairingCodeDisplay(state)
        is PairingUiState.Done -> {
            // DevicePairingSection never produces this state — success
            // flips deviceToken non-blank via onPaired instead.
        }
    }
}
