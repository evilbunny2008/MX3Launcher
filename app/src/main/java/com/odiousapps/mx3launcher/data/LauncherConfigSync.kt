package com.odiousapps.mx3launcher.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings backup/restore over mx3launcher.odiousapps.com's credential
 * relay — the same site/protocol SoundbarPairing.kt uses for a different
 * purpose (see that project's website/README.md for the full protocol),
 * under a separate "app" name so the two kinds of saved presets don't mix
 * together in the picker at credential_view.php.
 *
 * Backup is a "push" share: this device already has the data (its current
 * settings), sends it up front, and shows a code/QR linking to
 * credential_view.php. The account holder there confirms the code, which
 * both reveals it to them and — per credential_view.php's push-mode
 * handling — saves it as one of their saved_credentials presets in the
 * same step, so this device's poll seeing it resolved means it was
 * actually kept, not just glanced at.
 *
 * Restore is a "pull" share: this device has nothing of its own yet, asks
 * to receive it, and the account holder picks one of their previously-saved
 * "MX3Launcher Settings" presets at credential_view.php (typically one
 * made by a previous backup, though nothing stops them adding one by hand
 * at manage_credentials.php).
 *
 * Deliberately not a device-storage-based approach (MediaStore, SAF, or
 * otherwise) — those all ultimately depend on either scoped-storage
 * per-app file ownership (which doesn't survive an uninstall that changes
 * signing key) or a document-picker app being present (which many TV boxes
 * don't have). This only needs network access, which the app already
 * requires for the soundbar-wake feature.
 *
 * All functions here perform blocking network I/O — callers must run them
 * off the main thread (Dispatchers.IO, as SettingsScreen.kt does).
 */
object LauncherConfigSync {

    private const val TAG = "LauncherConfigSync"

    private const val START_URL = "https://mx3launcher.odiousapps.com/credential_start.php"
    private const val STATUS_URL = "https://mx3launcher.odiousapps.com/credential_status.php"
    const val VIEW_URL_PREFIX = "https://mx3launcher.odiousapps.com/credential_view.php?code="

    private const val APP_NAME = "MX3Launcher Settings"
    private const val FIELD_CONFIG = "Config"

    private const val KEY_THEME_MODE = "themeMode"
    private const val KEY_GRADIENT_ID = "gradientId"
    private const val KEY_COLUMNS = "columns"
    private const val KEY_HIDDEN_PACKAGES = "hiddenPackages"
    private const val KEY_APP_ORDER = "appOrder"
    private val KNOWN_KEYS = listOf(KEY_THEME_MODE, KEY_GRADIENT_ID, KEY_COLUMNS, KEY_HIDDEN_PACKAGES, KEY_APP_ORDER)

    private val LABEL_TIMESTAMP_FORMAT = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    data class PairingSession(val code: String, val token: String, val expiresInSeconds: Int) {
        val approveUrl: String get() = "$VIEW_URL_PREFIX$code"
    }

    sealed class BackupPollResult {
        object Pending : BackupPollResult()
        object Expired : BackupPollResult()
        object Saved : BackupPollResult()
        data class Error(val message: String) : BackupPollResult()
    }

    sealed class RestorePollResult {
        object Pending : RestorePollResult()
        object Expired : RestorePollResult()
        data class Restored(val settings: LauncherSettings) : RestorePollResult()
        data class Error(val message: String) : RestorePollResult()
    }

    /** Starts a "push" share carrying this device's current settings. */
    fun startBackup(settings: LauncherSettings): PairingSession? {
        return try {
            val label = "Backup — ${LABEL_TIMESTAMP_FORMAT.format(Date())}"
            val fields = JSONObject().put(FIELD_CONFIG, toJson(settings))
            val body = JSONObject().put("app", APP_NAME).put("label", label).put("fields", fields)
            startSession(body)
        } catch (e: Exception) {
            Log.w(TAG, "startBackup failed", e)
            null
        }
    }

    /** Starts a "pull" share asking to receive a previously-saved backup. */
    fun startRestore(): PairingSession? {
        return try {
            startSession(JSONObject().put("app", APP_NAME))
        } catch (e: Exception) {
            Log.w(TAG, "startRestore failed", e)
            null
        }
    }

    private fun startSession(body: JSONObject): PairingSession? {
        val response = PairingHttp.post(START_URL, body.toString())
        val json = JSONObject(response)
        if (!json.optBoolean("ok", false)) {
            Log.w(TAG, "startSession: $START_URL returned ok=false: $response")
            return null
        }
        return PairingSession(
            code = json.getString("code"),
            token = json.getString("token"),
            expiresInSeconds = json.optInt("expires_in", 600),
        )
    }

    fun pollBackup(token: String): BackupPollResult {
        return try {
            when (val status = JSONObject(PairingHttp.get(pollUrl(token))).optString("status")) {
                "viewed" -> BackupPollResult.Saved
                "pending" -> BackupPollResult.Pending
                "expired" -> BackupPollResult.Expired
                else -> BackupPollResult.Error("Unexpected status: $status")
            }
        } catch (e: Exception) {
            Log.w(TAG, "pollBackup: request failed", e)
            BackupPollResult.Error(e.message ?: "Network error")
        }
    }

    fun pollRestore(token: String): RestorePollResult {
        return try {
            val json = JSONObject(PairingHttp.get(pollUrl(token)))
            when (json.optString("status")) {
                "viewed" -> {
                    val raw = (json.optJSONObject("fields") ?: JSONObject()).optString(FIELD_CONFIG)
                    val settings = raw.takeIf { it.isNotBlank() }?.let { fromJson(it) }
                    if (settings == null) {
                        RestorePollResult.Error("That saved preset doesn't look like a settings backup")
                    } else {
                        RestorePollResult.Restored(settings)
                    }
                }
                "pending" -> RestorePollResult.Pending
                "expired" -> RestorePollResult.Expired
                else -> RestorePollResult.Error(json.optString("error", "Unknown error"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "pollRestore: request failed", e)
            RestorePollResult.Error(e.message ?: "Network error")
        }
    }

    private fun pollUrl(token: String): String {
        val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
        return "$STATUS_URL?token=$encodedToken"
    }

    private fun toJson(settings: LauncherSettings): String {
        val json = JSONObject()
        json.put(KEY_THEME_MODE, settings.themeMode.name)
        json.put(KEY_GRADIENT_ID, settings.gradientId)
        json.put(KEY_COLUMNS, settings.columns)
        json.put(KEY_HIDDEN_PACKAGES, JSONArray(settings.hiddenPackages.toList()))
        json.put(KEY_APP_ORDER, JSONArray(settings.appOrder))
        return json.toString()
    }

    /** Returns null (rather than throwing) on malformed input, so a
     *  restore fails cleanly instead of crashing the launcher.
     *
     *  Every field below is read leniently (opt* with a default) so a
     *  backup from an older app version — missing a key added later —
     *  still restores instead of failing outright. That same leniency
     *  means a JSON object with none of our keys would otherwise parse
     *  "successfully" into all-default settings with no way to tell that
     *  apart from a real restore — so that case is rejected explicitly
     *  up front instead. */
    private fun fromJson(raw: String): LauncherSettings? {
        return try {
            val json = JSONObject(raw)
            if (KNOWN_KEYS.none { json.has(it) }) return null
            val themeMode = json.optString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
                .let { name -> runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM) }
            val gradientId = json.optString(KEY_GRADIENT_ID, GRADIENT_PRESETS.first().id)
            val columns = json.optInt(KEY_COLUMNS, 6)
            val hiddenPackages = json.optJSONArray(KEY_HIDDEN_PACKAGES)?.toStringSet() ?: emptySet()
            val appOrder = json.optJSONArray(KEY_APP_ORDER)?.toStringList() ?: emptyList()

            LauncherSettings(
                themeMode = themeMode,
                gradientId = gradientId,
                columns = columns,
                hiddenPackages = hiddenPackages,
                appOrder = appOrder,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.toStringSet(): Set<String> = toStringList().toSet()
}
