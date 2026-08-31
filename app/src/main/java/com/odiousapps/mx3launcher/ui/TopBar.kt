package com.odiousapps.mx3launcher.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import androidx.compose.material3.Icon
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val WIFI_POLL_INTERVAL_MS = 5_000L

@Composable
fun TopBar(onOpenAppSettings: () -> Unit) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LiveClock()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiStatusButton(context)
            IconButton(onClick = { openOsSettings(context) }) {
                Icon(Icons.Filled.SettingsApplications, contentDescription = "System settings")
            }
            IconButton(onClick = onOpenAppSettings) {
                Icon(Icons.Filled.Tune, contentDescription = "Launcher settings")
            }
        }
    }
}

@Composable
private fun LiveClock() {
    var time by remember { mutableStateOf(currentTimeString()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTimeString()
            delay(1_000L.milliseconds)
        }
    }
    Text(text = time)
}

private fun currentTimeString(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

@Composable
private fun WifiStatusButton(context: Context) {
    var connected by remember { mutableStateOf(isWifiConnected(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            connected = isWifiConnected(context)
            delay(WIFI_POLL_INTERVAL_MS.milliseconds)
        }
    }
    IconButton(onClick = { openWifiSettings(context) }) {
        Icon(
            if (connected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
            contentDescription = if (connected) "Wi-Fi connected, open network settings" else "Wi-Fi not connected, open network settings",
        )
    }
}

/**
 * Checks the ACTIVE network specifically -- a device can have Wi-Fi
 * radio-on but actually be routing over Ethernet, in which case showing
 * a "connected" Wi-Fi icon would be misleading. This checks whether
 * Wi-Fi specifically is the transport actually in use right now.
 */
private fun isWifiConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

private fun openWifiSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openOsSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
