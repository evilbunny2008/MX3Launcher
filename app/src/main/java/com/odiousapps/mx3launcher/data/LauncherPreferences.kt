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

package com.odiousapps.mx3launcher.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A selectable background gradient. Curated presets rather than a full
 *  RGB picker -- much easier to navigate with a D-pad than a colour wheel. */
data class GradientPreset(val id: String, val label: String, val start: Color, val end: Color)

val GRADIENT_PRESETS = listOf(
    GradientPreset("slate", "Slate", Color(0xFF1F2430), Color(0xFF3A4152)),
    GradientPreset("indigo", "Indigo", Color(0xFF2B2F77), Color(0xFF5B4FE0)),
    GradientPreset("teal", "Teal", Color(0xFF0F3D3E), Color(0xFF17836F)),
    GradientPreset("sunset", "Sunset", Color(0xFF3A1C4D), Color(0xFFB5482A)),
    GradientPreset("forest", "Forest", Color(0xFF16301F), Color(0xFF3C7A4E)),
    GradientPreset("mono", "Monochrome", Color(0xFF101014), Color(0xFF2C2C34)),
)

data class LauncherSettings(
    val themeMode: ThemeMode,
    val gradientId: String,
    val columns: Int,
    val hiddenPackages: Set<String>,
    val appOrder: List<String>, // package names, explicit user order
    val soundbarWakeEnabled: Boolean = false,
    val soundbarWakeUrl: String = "",
    val soundbarWakeSecret: String = "",
)

object LauncherPreferences {

    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_GRADIENT_ID = stringPreferencesKey("gradient_id")
    private val KEY_COLUMNS = intPreferencesKey("columns")
    private val KEY_HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
    private val KEY_APP_ORDER = stringPreferencesKey("app_order") // comma-joined, order preserved
    private val KEY_SOUNDBAR_WAKE_ENABLED = booleanPreferencesKey("soundbar_wake_enabled")
    private val KEY_SOUNDBAR_WAKE_URL = stringPreferencesKey("soundbar_wake_url")
    private val KEY_SOUNDBAR_WAKE_SECRET = stringPreferencesKey("soundbar_wake_secret")

    private const val DEFAULT_COLUMNS = 6
    private const val ORDER_DELIMITER = ","

    fun observe(context: Context): Flow<LauncherSettings> =
        context.dataStore.data.map { prefs ->
            LauncherSettings(
                themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                gradientId = prefs[KEY_GRADIENT_ID] ?: GRADIENT_PRESETS.first().id,
                columns = prefs[KEY_COLUMNS] ?: DEFAULT_COLUMNS,
                hiddenPackages = prefs[KEY_HIDDEN_PACKAGES] ?: emptySet(),
                appOrder = prefs[KEY_APP_ORDER]
                    ?.split(ORDER_DELIMITER)
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                soundbarWakeEnabled = prefs[KEY_SOUNDBAR_WAKE_ENABLED] ?: false,
                soundbarWakeUrl = prefs[KEY_SOUNDBAR_WAKE_URL] ?: "",
                soundbarWakeSecret = prefs[KEY_SOUNDBAR_WAKE_SECRET] ?: "",
            )
        }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setGradient(context: Context, gradientId: String) {
        context.dataStore.edit { it[KEY_GRADIENT_ID] = gradientId }
    }

    suspend fun setColumns(context: Context, columns: Int) {
        context.dataStore.edit { it[KEY_COLUMNS] = columns }
    }

    suspend fun setHiddenPackages(context: Context, hidden: Set<String>) {
        context.dataStore.edit { it[KEY_HIDDEN_PACKAGES] = hidden }
    }

    suspend fun setAppOrder(context: Context, order: List<String>) {
        context.dataStore.edit { it[KEY_APP_ORDER] = order.joinToString(ORDER_DELIMITER) }
    }

    suspend fun setSoundbarWakeEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUNDBAR_WAKE_ENABLED] = enabled }
    }

    suspend fun setSoundbarWakeUrl(context: Context, url: String) {
        context.dataStore.edit { it[KEY_SOUNDBAR_WAKE_URL] = url }
    }

    suspend fun setSoundbarWakeSecret(context: Context, secret: String) {
        context.dataStore.edit { it[KEY_SOUNDBAR_WAKE_SECRET] = secret }
    }

    /** Writes every field at once (used by restore) rather than calling
     *  each individual setter in sequence, so a restore is a single
     *  atomic DataStore write instead of five separate ones. */
    suspend fun restoreAll(context: Context, settings: LauncherSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = settings.themeMode.name
            prefs[KEY_GRADIENT_ID] = settings.gradientId
            prefs[KEY_COLUMNS] = settings.columns
            prefs[KEY_HIDDEN_PACKAGES] = settings.hiddenPackages
            prefs[KEY_APP_ORDER] = settings.appOrder.joinToString(ORDER_DELIMITER)
            prefs[KEY_SOUNDBAR_WAKE_ENABLED] = settings.soundbarWakeEnabled
            prefs[KEY_SOUNDBAR_WAKE_URL] = settings.soundbarWakeUrl
            prefs[KEY_SOUNDBAR_WAKE_SECRET] = settings.soundbarWakeSecret
        }
    }
}
