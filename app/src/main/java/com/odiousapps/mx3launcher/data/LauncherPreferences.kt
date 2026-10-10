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
 *  RGB picker — much easier to navigate with a D-pad than a colour wheel. */
data class GradientPreset(val id: String, val start: Color, val end: Color)

val GRADIENT_PRESETS = listOf(
    GradientPreset("slate", Color(0xFF1F2430), Color(0xFF3A4152)),
    GradientPreset("indigo", Color(0xFF2B2F77), Color(0xFF5B4FE0)),
    GradientPreset("teal", Color(0xFF0F3D3E), Color(0xFF17836F)),
    GradientPreset("sunset", Color(0xFF3A1C4D), Color(0xFFB5482A)),
    GradientPreset("forest", Color(0xFF16301F), Color(0xFF3C7A4E)),
    GradientPreset("mono", Color(0xFF101014), Color(0xFF2C2C34)),
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
    val soundbarIrCodesToSend: String = DEFAULT_SOUNDBAR_IR_CODES,
    val soundbarCheckCurrent: Boolean = true,
)

val COLUMN_OPTIONS = listOf(5, 6, 7)
const val DEFAULT_COLUMNS = 6

/** Anything outside [COLUMN_OPTIONS] (e.g. 0 from a malformed backup) would
 *  crash the home grid - GridCells.Fixed(0) throws and AppTile divides by
 *  it - and since this app *is* the home screen, that's a crash loop. */
fun sanitizeColumns(columns: Int): Int = if (columns in COLUMN_OPTIONS) columns else DEFAULT_COLUMNS

// wake_soundbar.php's default IR code for the Philips 6000 soundbar this was
// built for - see SoundbarPairing.kt for where a paired preset can override it.
const val DEFAULT_SOUNDBAR_IR_CODES = "P6000_ON"

object LauncherPreferences {

    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_GRADIENT_ID = stringPreferencesKey("gradient_id")
    private val KEY_COLUMNS = intPreferencesKey("columns")
    private val KEY_HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
    private val KEY_APP_ORDER = stringPreferencesKey("app_order") // comma-joined, order preserved
    private val KEY_SOUNDBAR_WAKE_ENABLED = booleanPreferencesKey("soundbar_wake_enabled")
    private val KEY_SOUNDBAR_WAKE_URL = stringPreferencesKey("soundbar_wake_url")
    private val KEY_SOUNDBAR_WAKE_SECRET = stringPreferencesKey("soundbar_wake_secret")
    private val KEY_SOUNDBAR_IR_CODES = stringPreferencesKey("soundbar_ir_codes_to_send")
    private val KEY_SOUNDBAR_CHECK_CURRENT = booleanPreferencesKey("soundbar_check_current")
    // Deliberately not a LauncherSettings field, and not touched by
    // observe()/restoreAll() below - this identifies *this device install*
    // to the config-sync server (see LauncherConfigSync.kt's pairDevice()),
    // so it must never travel inside a settings backup/restore itself
    // (restoring one on a different device must not silently adopt the
    // backed-up device's identity/token).
    private val KEY_CONFIG_SYNC_DEVICE_TOKEN = stringPreferencesKey("config_sync_device_token")

    private const val ORDER_DELIMITER = ","

    fun observe(context: Context): Flow<LauncherSettings> =
        context.dataStore.data.map { prefs ->
            LauncherSettings(
                themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                gradientId = prefs[KEY_GRADIENT_ID] ?: GRADIENT_PRESETS.first().id,
                columns = sanitizeColumns(prefs[KEY_COLUMNS] ?: DEFAULT_COLUMNS),
                hiddenPackages = prefs[KEY_HIDDEN_PACKAGES] ?: emptySet(),
                appOrder = decodeOrder(prefs[KEY_APP_ORDER]),
                soundbarWakeEnabled = prefs[KEY_SOUNDBAR_WAKE_ENABLED] ?: false,
                soundbarWakeUrl = prefs[KEY_SOUNDBAR_WAKE_URL] ?: "",
                soundbarWakeSecret = prefs[KEY_SOUNDBAR_WAKE_SECRET] ?: "",
                soundbarIrCodesToSend = prefs[KEY_SOUNDBAR_IR_CODES] ?: DEFAULT_SOUNDBAR_IR_CODES,
                soundbarCheckCurrent = prefs[KEY_SOUNDBAR_CHECK_CURRENT] ?: true,
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

    /** Read-modify-write in one transaction, so concurrent callers each see
     *  the previous one's result rather than a stale snapshot. */
    suspend fun updateHiddenPackages(context: Context, transform: (Set<String>) -> Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIDDEN_PACKAGES] = transform(prefs[KEY_HIDDEN_PACKAGES] ?: emptySet())
        }
    }

    /** As [updateHiddenPackages], for the app order. */
    suspend fun updateAppOrder(context: Context, transform: (List<String>) -> List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_ORDER] = transform(decodeOrder(prefs[KEY_APP_ORDER])).joinToString(ORDER_DELIMITER)
        }
    }

    private fun decodeOrder(raw: String?): List<String> =
        raw?.split(ORDER_DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()

    suspend fun setSoundbarWakeEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUNDBAR_WAKE_ENABLED] = enabled }
    }

    /** Used once pairing resolves. Writes all four fields in one DataStore
     *  transaction - separate per-field writes would leave a window where
     *  only some of them (e.g. the URL but not the secret) are set. */
    suspend fun setSoundbarWakePairing(
        context: Context,
        url: String,
        secret: String,
        irCodesToSend: String,
        checkCurrent: Boolean,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUNDBAR_WAKE_URL] = url
            prefs[KEY_SOUNDBAR_WAKE_SECRET] = secret
            prefs[KEY_SOUNDBAR_IR_CODES] = irCodesToSend
            prefs[KEY_SOUNDBAR_CHECK_CURRENT] = checkCurrent
        }
    }

    fun observeConfigSyncDeviceToken(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_CONFIG_SYNC_DEVICE_TOKEN] ?: "" }

    suspend fun setConfigSyncDeviceToken(context: Context, token: String) {
        context.dataStore.edit { it[KEY_CONFIG_SYNC_DEVICE_TOKEN] = token }
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
            prefs[KEY_SOUNDBAR_IR_CODES] = settings.soundbarIrCodesToSend
            prefs[KEY_SOUNDBAR_CHECK_CURRENT] = settings.soundbarCheckCurrent
        }
    }
}
