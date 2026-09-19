package com.odiousapps.mx3launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.odiousapps.mx3launcher.data.AppEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppGridScreen(
    apps: List<AppEntry>,
    columns: Int,
    onOpenAppSettings: () -> Unit,
    soundbarWakeEnabled: Boolean,
    soundbarWakeUrl: String,
    soundbarWakeSecret: String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Error message for the blocking dialog below; null means no dialog.
    var pendingWakeFailure by remember { mutableStateOf<String?>(null) }

    // Keyed on `apps` so requesters are re-created (not stale) whenever the
    // list itself changes -- install/uninstall, reorder, show/hide.
    val focusRequesters = remember(apps) { List(apps.size) { FocusRequester() } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(onOpenAppSettings = onOpenAppSettings)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppTile(
                    app = app,
                    onClick = {
                        // Waits for the wake result before launching; a failure
                        // shows a blocking dialog and does not launch the app.
                        scope.launch {
                            if (soundbarWakeEnabled && soundbarWakeUrl.isNotBlank()) {
                                val error = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    checkSoundbarWake(soundbarWakeUrl, soundbarWakeSecret)
                                }
                                if (error != null) {
                                    pendingWakeFailure = error
                                    return@launch
                                }
                            }
                            launchAppIntent(context, app)
                        }
                    },
                    index = index,
                    totalCount = apps.size,
                    columns = columns,
                    focusRequesters = focusRequesters,
                )
            }
        }
    }

    pendingWakeFailure?.let { message ->
        // Dismissing only closes the dialog; it does not launch the app --
        // the person must press the tile again to retry.
        val dismiss = { pendingWakeFailure = null }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = dismiss) {
                    androidx.compose.material3.Text(text = "OK")
                }
            },
            title = { androidx.compose.material3.Text(text = "Soundbar wake failed") },
            text = { androidx.compose.material3.Text(text = message) },
        )
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    onClick: () -> Unit,
    index: Int,
    totalCount: Int,
    columns: Int,
    focusRequesters: List<FocusRequester>,
) {
    val iconSizeDp = 36
    val context = androidx.compose.ui.platform.LocalContext.current

    val column = index % columns
    val rowStart = index - column
    // A partial final row may not end at column (columns - 1).
    val rowEnd = minOf(rowStart + columns - 1, totalCount - 1)
    val isLastInRow = index == rowEnd

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequesters[index])
            // Only fires while this tile holds D-pad focus, so it naturally
            // scopes to the focused tile with no extra tracking state needed.
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                val isMenuPress = keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU
                if (isMenuPress) {
                    openAppInfo(context, app.packageName)
                    return@onKeyEvent true
                }

                // Compose doesn't wrap D-pad focus at grid edges on its own;
                // redirect Left/Right at row ends to the opposite edge of the
                // same row instead.
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                val isLeftAtStart = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && column == 0
                val isRightAtEnd = keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && isLastInRow
                if (isLeftAtStart) {
                    focusRequesters[rowEnd].requestFocus()
                    return@onKeyEvent true
                }
                if (isRightAtEnd) {
                    focusRequesters[rowStart].requestFocus()
                    return@onKeyEvent true
                }

                false
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Preserves the drawable's natural aspect ratio; forcing a fixed
            // square size squishes non-square icons. Falls back to a fixed
            // size only when intrinsic size is invalid (some adaptive icons
            // report -1).
            val bitmap = remember(app.packageName) {
                val hasValidIntrinsicSize = app.icon.intrinsicWidth > 0 && app.icon.intrinsicHeight > 0
                if (hasValidIntrinsicSize) {
                    app.icon.toBitmap().asImageBitmap()
                } else {
                    app.icon.toBitmap(108, 108).asImageBitmap()
                }
            }

            // Full-width backdrop behind the icon; one subtle tint works in
            // both themes without needing per-theme colours.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // ContentScale.Fit preserves aspect ratio and centers by default.
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(iconSizeDp.dp),
                )
            }

            Text(text = app.label, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun launchAppIntent(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = ComponentName(app.packageName, app.activityClassName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // App may have been uninstalled since the grid loaded -- fail quietly
        // rather than crash the launcher and take the whole home screen down.
    }
}

private const val TAG = "SoundbarWake"

// Hardware limitation, not a bug: the soundbar auto-enters standby with no
// way to disable that. An AudioManager volume nudge didn't wake it (that
// only adjusts a volume setting, no real audio session). This instead calls
// a server-side script (URL/secret from Settings) that checks a
// power-monitoring smart socket and, only if the soundbar is actually in
// standby, sends a Zigbee IR power-toggle -- toggle, not a dedicated "on"
// command, so firing it blindly would turn an already-on soundbar off.
//
// Blocking, not fire-and-forget: the caller awaits this (via
// withContext(Dispatchers.IO)) before deciding whether to show a dialog or
// launch. Returns null on success, or a human-readable failure otherwise.
private fun checkSoundbarWake(url: String, secret: String): String? {
    val fullUrl = if (secret.isBlank()) {
        url
    } else {
        val separator = if (url.contains("?")) "&" else "?"
        val encodedSecret = java.net.URLEncoder.encode(secret, "UTF-8")
        "$url${separator}key=$encodedSecret"
    }

    return try {
        val connection = java.net.URL(fullUrl).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.requestMethod = "GET"

        // Must check the response code before reading a stream --
        // .inputStream throws for non-2xx (error bodies come from .errorStream).
        val responseCode = connection.responseCode
        val result = if (responseCode in 200..299) {
            connection.inputStream.use { it.readBytes() } // drain, result unneeded
            null
        } else {
            // wake_soundbar.php returns a JSON error body, more useful than
            // the bare status code; truncated in case something else is at
            // this URL and returns something huge/malformed.
            val errorBody = connection.errorStream
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?.trim()
                ?.take(500)
                .orEmpty()
            // Logs the plain url, not fullUrl, to avoid leaking the secret
            // (appended as ?key=...) into logcat.
            val logSuffix = if (errorBody.isNotEmpty()) ": $errorBody" else ""
            android.util.Log.w(TAG, "Wake failed for $url -- server returned $responseCode$logSuffix")
            if (errorBody.isNotEmpty()) "Server returned $responseCode: $errorBody" else "Server returned $responseCode"
        }
        connection.disconnect()
        result
    } catch (e: Exception) {
        android.util.Log.w(TAG, "Wake failed for $url", e)
        e.message ?: e.javaClass.simpleName
    }
}

/**
 * Opens Android's App Info page for the package -- the usual "long-press an
 * icon" destination, reached here via Menu on the focused tile since long-press
 * has no D-pad equivalent.
 */
private fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Same reasoning as launchAppIntent() -- fail quietly.
    }
}
