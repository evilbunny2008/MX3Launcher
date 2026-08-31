package com.odiousapps.mx3launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.odiousapps.mx3launcher.data.AppEntry

/**
 * Reordering here is move-up/move-down buttons rather than drag-and-drop.
 * Drag gestures don't have a sane D-pad equivalent -- this way every
 * action maps directly to a single remote press, no touch/pointer input
 * assumed anywhere.
 */
@Composable
fun AppDisplaySettingsScreen(
    allApps: List<AppEntry>,
    hiddenPackages: Set<String>,
    onToggleVisibility: (String) -> Unit,
    onMove: (packageName: String, direction: Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val listState = rememberLazyListState()

    // The actual reorder is async -- onMove persists to DataStore, which
    // flows back down as a new `allApps` list some time later, not
    // synchronously. LazyColumn keeps the moved item's own composable
    // identity correctly (thanks to key = { it.packageName }), but it
    // does NOT auto-scroll to keep a moved item in the visible viewport
    // on its own. This tracks which package to follow, and once the
    // updated order actually arrives (allApps changes), scrolls it back
    // into view.
    var pendingScrollTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(allApps, pendingScrollTarget) {
        val target = pendingScrollTarget ?: return@LaunchedEffect
        val index = allApps.indexOfFirst { it.packageName == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            pendingScrollTarget = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "App display")
        Text(text = "Choose which apps appear on the home screen and their order.")

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(allApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    hidden = app.packageName in hiddenPackages,
                    onToggleVisibility = { onToggleVisibility(app.packageName) },
                    onMoveUp = {
                        pendingScrollTarget = app.packageName
                        onMove(app.packageName, -1)
                    },
                    onMoveDown = {
                        pendingScrollTarget = app.packageName
                        onMove(app.packageName, 1)
                    },
                )
            }
        }

        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    hidden: Boolean,
    onToggleVisibility: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bitmap = remember(app.packageName) { app.icon.toBitmap().asImageBitmap() }
            Image(bitmap = bitmap, contentDescription = app.label, modifier = Modifier.size(40.dp))

            Text(
                text = app.label,
                modifier = Modifier.weight(1f),
            )

            Button(onClick = onMoveUp) { Text(text = "↑") }
            Button(onClick = onMoveDown) { Text(text = "↓") }
            Button(onClick = onToggleVisibility) {
                Text(text = if (hidden) "Show" else "Hide")
            }
        }
    }
}
