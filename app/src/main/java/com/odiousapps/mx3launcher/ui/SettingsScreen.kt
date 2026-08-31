package com.odiousapps.mx3launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.odiousapps.mx3launcher.data.GRADIENT_PRESETS
import com.odiousapps.mx3launcher.data.LauncherBackup
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
    onRestore: (LauncherSettings) -> Unit,
    onSoundbarWakeEnabledChange: (Boolean) -> Unit,
    onSoundbarWakeUrlChange: (String) -> Unit,
    onSoundbarWakeSecretChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var backupStatus by remember { mutableStateOf<String?>(null) }

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

        // Calls a small server-side script on app launch to wake a
        // soundbar that auto-enters standby with no way to disable that
        // on the hardware itself. URL and secret are configured here
        // rather than hardcoded, so they can be changed without a
        // rebuild -- see AppGridScreen.kt's wakeSoundbarIfNeeded() for
        // how these get used.
        SettingsSection(title = "Soundbar wake") {
            Button(onClick = { onSoundbarWakeEnabledChange(!settings.soundbarWakeEnabled) }) {
                Text(text = if (settings.soundbarWakeEnabled) "Enabled ✓" else "Disabled")
            }

            SoundbarPairingSection(
                settings = settings,
                onSoundbarWakeUrlChange = onSoundbarWakeUrlChange,
                onSoundbarWakeSecretChange = onSoundbarWakeSecretChange,
            )
        }

        // No Storage Access Framework / system picker here -- that was
        // tried first and failed with "you don't have an app that can do
        // this" on this device, since it doesn't ship anything that
        // handles SAF's picker intents. Reads/writes the public
        // Downloads collection via MediaStore directly instead, which
        // needs no picker or provider app to exist at all. See
        // LauncherBackup.kt. Each backup gets its own timestamped
        // filename rather than overwriting a single fixed name, so
        // restore lists what's available instead of assuming there's
        // exactly one.
        SettingsSection(title = "Backup & restore") {
            var availableBackups by remember { mutableStateOf(LauncherBackup.listBackups(context)) }
            var showRestoreList by remember { mutableStateOf(false) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    backupStatus = if (LauncherBackup.writeBackup(context, settings)) {
                        availableBackups = LauncherBackup.listBackups(context)
                        "Backup saved to ${LauncherBackup.BACKUP_LOCATION_DESCRIPTION}"
                    } else {
                        "Backup failed"
                    }
                }) {
                    Text(text = "Back up settings")
                }
                Button(onClick = {
                    availableBackups = LauncherBackup.listBackups(context)
                    showRestoreList = true
                }) {
                    Text(text = "Restore settings")
                }
                Button(onClick = {
                    // Reuses the same restoreAll() path as picking a
                    // backup -- resetting is really just "restore" to a
                    // fixed default LauncherSettings, same default values
                    // used for collectAsState's initial state up in
                    // MainActivity.kt.
                    onRestore(LauncherSettings(ThemeMode.SYSTEM, GRADIENT_PRESETS.first().id, 6, emptySet(), emptyList()))
                    showRestoreList = false
                    backupStatus = "Reset to defaults"
                }) {
                    Text(text = "Reset to defaults")
                }
            }

            if (showRestoreList) {
                if (availableBackups.isEmpty()) {
                    Text(text = "No backups found in ${LauncherBackup.BACKUP_LOCATION_DESCRIPTION}")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableBackups.forEach { backup ->
                            Button(onClick = {
                                val restored = LauncherBackup.readBackup(context, backup.uri)
                                if (restored != null) {
                                    onRestore(restored)
                                    backupStatus = "Restored from ${backup.displayLabel}"
                                } else {
                                    backupStatus = "Restore failed -- couldn't read ${backup.displayName}"
                                }
                                showRestoreList = false
                            }) {
                                Text(text = backup.displayLabel)
                            }
                        }
                    }
                }
            }

            backupStatus?.let { status ->
                Text(text = status)
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
}

/**
 * Device-code-style pairing UI -- replaces raw text-entry fields
 * entirely. The TV only ever displays a short code and polls in the
 * background; the real URL+secret get typed nowhere on the TV, which
 * also sidesteps the D-pad navigation trap plain TextFields hit here
 * (focused text-edit mode captures Down for cursor movement instead of
 * surfacing it for moving focus to whatever's below the field).
 */
@Composable
private fun SoundbarPairingSection(
    settings: LauncherSettings,
    onSoundbarWakeUrlChange: (String) -> Unit,
    onSoundbarWakeSecretChange: (String) -> Unit,
) {
    val isPaired = settings.soundbarWakeUrl.isNotBlank() && settings.soundbarWakeSecret.isNotBlank()
    var pairingState by remember { mutableStateOf<PairingUiState>(PairingUiState.Idle) }
    val pairingScope = androidx.compose.runtime.rememberCoroutineScope()

    if (isPaired) {
        Text(text = "Paired ✓")
        Button(onClick = {
            onSoundbarWakeUrlChange("")
            onSoundbarWakeSecretChange("")
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
                            "Couldn't start pairing -- check the server is reachable"
                        )
                        return@launch
                    }
                    val approveUrl = "https://mx3launcher.odiousapps.com/pair_approve.php?code=${session.code}"
                    val qrBitmap = withContext(Dispatchers.Default) { generateQrCodeBitmap(approveUrl) }
                    pairingState = PairingUiState.ShowingCode(session.code, session.token, qrBitmap)

                    val deadlineMs = System.currentTimeMillis() + session.expiresInSeconds * 1000L
                    while (System.currentTimeMillis() < deadlineMs) {
                        delay(3000.milliseconds)
                        when (val result = withContext(Dispatchers.IO) {
                            SoundbarPairing.pollPairing(session.token)
                        }) {
                            is SoundbarPairing.PollResult.Approved -> {
                                onSoundbarWakeUrlChange(result.url)
                                onSoundbarWakeSecretChange(result.secret)
                                pairingState = PairingUiState.Idle
                                return@launch
                            }
                            is SoundbarPairing.PollResult.Expired -> {
                                pairingState = PairingUiState.Failed("Code expired -- try again")
                                return@launch
                            }
                            is SoundbarPairing.PollResult.Error -> {
                                // Transient network hiccup -- keep polling rather
                                // than giving up on the first blip.
                            }
                            SoundbarPairing.PollResult.Pending -> {
                                // Keep waiting.
                            }
                        }
                    }
                    pairingState = PairingUiState.Failed("Code expired -- try again")
                }
            }) {
                Text(text = "Pair")
            }
        }
        is PairingUiState.Starting -> {
            Text(text = "Starting...")
        }
        is PairingUiState.ShowingCode -> {
            if (state.qrBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = state.qrBitmap,
                    contentDescription = "QR code to open the pairing page",
                    modifier = Modifier.size(200.dp),
                )
                Text(text = "Scan with your phone's camera, or go to:")
            } else {
                // QR generation failed -- fall back to the plain text
                // path entirely rather than showing a broken state.
                Text(text = "On your phone, go to:")
            }
            Text(text = "mx3launcher.odiousapps.com/pair_approve.php")
            Text(text = "and enter this code:")
            Text(text = state.code)
        }
    }
}
