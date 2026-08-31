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

package com.odiousapps.mx3launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.odiousapps.mx3launcher.data.GRADIENT_PRESETS
import com.odiousapps.mx3launcher.data.GradientPreset
import com.odiousapps.mx3launcher.data.ThemeMode
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

@Composable
fun resolveIsDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun gradientFor(gradientId: String): GradientPreset =
    GRADIENT_PRESETS.firstOrNull { it.id == gradientId } ?: GRADIENT_PRESETS.first()

@Composable
fun LauncherTheme(
    themeMode: ThemeMode,
    gradientId: String,
    content: @Composable () -> Unit,
) {
    val isDark = resolveIsDark(themeMode)
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    val gradient = gradientFor(gradientId)

    // Computed directly rather than trusting MaterialTheme's own ambient
    // content-colour setup. This project mixes androidx.tv.material3.Text
    // with androidx.compose.material3.Icon in a couple of places (see
    // TopBar.kt). Relying on either family's internal colour-scheme
    // resolution to "just work" isn't something verifiable without a
    // real build against this specific young library. Forcing an
    // explicit, known-correct colour onto both families' content-colour
    // locals sidesteps that uncertainty entirely -- this is what was
    // actually missing before, which is why text rendered black
    // regardless of theme.
    val contentColor = if (isDark) Color(0xFFF2F2F2) else Color(0xFF1A1A1A)

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            androidx.tv.material3.LocalContentColor provides contentColor,
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(gradient.start, gradient.end)))
            ) {
                content()
            }
        }
    }
}
