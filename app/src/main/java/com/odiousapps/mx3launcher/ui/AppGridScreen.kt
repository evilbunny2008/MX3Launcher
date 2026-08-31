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

    // Holds the error message while a blocking dialog shows it -- null
    // means no dialog is showing. Just a String rather than also
    // carrying the AppEntry that triggered it, since a failure now
    // blocks launching entirely rather than launching that app once
    // dismissed (see the dialog below).
    var pendingWakeFailure by remember { mutableStateOf<String?>(null) }

    // One FocusRequester per app, re-created whenever the app list itself
    // changes (install/uninstall, reorder, show/hide) -- keyed on `apps`
    // rather than just remember{} so stale requesters from a previous
    // list shape never get referenced after a change.
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
                        // Waits for the wake attempt's result BEFORE
                        // launching -- this is why it's a coroutine
                        // rather than the earlier fire-and-forget
                        // background Thread. A failure shows a blocking
                        // dialog and does NOT launch the app at all --
                        // dismissing the dialog only closes it; actually
                        // launching requires pressing the tile again.
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
        // A wake failure is a hard block, not just an FYI -- dismissing
        // this (OK, or Back/outside-tap) only closes the dialog, it does
        // NOT launch the app. The person needs to actually retry (fix
        // whatever's wrong, or accept it and press the tile again) to
        // launch, rather than the failure being informational and
        // launching happening regardless either way.
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
    // Handles a partial final row (total items not evenly divisible by
    // columns) -- the last real item in this row isn't always at
    // column (columns - 1).
    val rowEnd = minOf(rowStart + columns - 1, totalCount - 1)
    val isLastInRow = index == rowEnd

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequesters[index])
            // Compose's key-event modifiers only fire while this specific
            // composable (or a focused descendant) actually holds D-pad
            // focus -- so this naturally scopes to "whichever tile is
            // currently focused" with no extra focus-tracking state
            // needed. This is a plain Activity, so it already receives
            // hardware key events natively; no accessibility service or
            // elevated privileges needed for this, unlike Button Mapper's
            // system-wide interception.
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                val isMenuPress = keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU
                if (isMenuPress) {
                    openAppInfo(context, app.packageName)
                    return@onKeyEvent true
                }

                // Compose's default D-pad navigation doesn't wrap grid
                // edges on its own -- pressing Left at column 0 (or
                // Right at the last column) would otherwise just do
                // nothing, or move focus somewhere outside the grid
                // entirely. Explicitly redirect to the opposite edge of
                // the SAME row instead, consuming the event so the
                // default (non-wrapping) behaviour doesn't also fire.
                // Uses the same raw nativeKeyEvent.keyCode approach as
                // the Menu check above, rather than Compose's own Key.*
                // constants -- kept to one single, already-verified way
                // of reading key identity in this file.
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
            // Preserves the drawable's own natural aspect ratio rather
            // than forcing a fixed square canvas. Forcing
            // toBitmap(width, height) to an exact size non-uniformly
            // squishes any drawable that isn't already perfectly square
            // -- that's what caused icons to look off-centre after the
            // previous fix. Falls back to a fixed size only if the
            // drawable reports an invalid intrinsic size (some adaptive
            // icons can report -1), which toBitmap() itself would
            // otherwise mishandle.
            val bitmap = remember(app.packageName) {
                val hasValidIntrinsicSize = app.icon.intrinsicWidth > 0 && app.icon.intrinsicHeight > 0
                if (hasValidIntrinsicSize) {
                    app.icon.toBitmap().asImageBitmap()
                } else {
                    app.icon.toBitmap(108, 108).asImageBitmap()
                }
            }

            // Full-width backdrop behind the icon, per request -- a
            // subtle tint that reads reasonably in both light and dark
            // theme without needing separate per-theme colours, rather
            // than the icon sitting in empty space at its own small
            // fixed size.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // ContentScale.Fit preserves aspect ratio (no stretching)
                // AND centers by default (its default alignment is
                // Alignment.Center) -- this single setting is what
                // guarantees both "not distorted" and "properly centered"
                // regardless of any given icon's own native proportions.
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
        // App may have been uninstalled between the grid loading and the
        // tap landing -- fail quietly rather than crashing the launcher
        // itself, since a crashing launcher takes the whole home screen
        // down with it.
    }
}

private const val TAG = "SoundbarWake"

// Standing hardware limitation, not a bug: the soundbar auto-enters
// standby after inactivity and has no setting to disable that.
// Superseded the earlier AudioManager volume-nudge approach -- that
// turned out not to actually wake it, since it only ever adjusted a
// volume SETTING without opening a real audio session, and separately
// even the soundbar's own remote couldn't wake it via volume at all.
// This instead calls a small server-side script over HTTPS (URL and
// shared-secret key configured in Settings, not hardcoded here), which
// checks a power-monitoring smart socket to see whether the soundbar
// is actually in standby, and only then sends a Zigbee IR toggle
// command to wake it -- the IR code toggles power rather than being a
// dedicated "on" command, so blindly firing it on every launch would
// turn an already-on soundbar OFF instead.
//
// Blocking (not fire-and-forget) and returns a result rather than
// showing its own Toast -- the caller needs to actually wait for this
// before deciding whether to show a dialog or launch immediately, which
// is why this is a plain function meant to be called from
// withContext(Dispatchers.IO) rather than spawning its own background
// Thread the way it used to. Returns null on success, or a
// human-readable failure description otherwise.
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

        // Response code has to be checked BEFORE deciding which stream
        // to read -- HttpURLConnection.inputStream throws for non-2xx
        // responses (error bodies come from .errorStream instead), so
        // reading .inputStream unconditionally would itself throw
        // before a response-code check placed after it was ever reached.
        val responseCode = connection.responseCode
        val result = if (responseCode in 200..299) {
            connection.inputStream.use { it.readBytes() } // drain the response, no result needed
            null
        } else {
            // Actually capture the body, not just drain it -- our own
            // wake_soundbar.php returns a real JSON error message here
            // (e.g. {"OK":false,"error":"Could not read socket state..."}),
            // which is far more useful than the bare status code alone.
            // Truncated defensively in case something entirely different
            // is at this URL and returns something huge/malformed (an
            // HTML error page from a misconfigured server, etc.).
            val errorBody = connection.errorStream
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?.trim()
                ?.take(500)
                .orEmpty()
            // Logs the plain url, NOT fullUrl -- fullUrl has the secret
            // appended as ?key=..., and logcat is readable by anything
            // with adb/appropriate permissions, so leaking the secret
            // into logs would undermine a lot of the point of keeping
            // it out of view in the first place.
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
 * Opens Android's own System App Info page for the given package --
 * the standard "long-press an icon" destination on most launchers
 * (uninstall, permissions, storage, notifications, etc.), reached here
 * via Menu on whichever tile currently has D-pad focus instead of a
 * touch-only long-press gesture, which doesn't really have a D-pad
 * equivalent.
 */
private fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Same reasoning as launchAppIntent() -- fail quietly rather than
        // crash the launcher.
    }
}
