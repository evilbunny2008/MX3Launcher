package com.odiousapps.mx3launcher.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings backup/restore over mx3launcher.odiousapps.com. Two phases:
 *
 * - Pairing (pairDevice/pollPairing): a one-time "pull" share over the same
 *   credential relay SoundbarPairing.kt uses (see that project's
 *   website/README.md for the protocol, and credential_view.php's
 *   DEVICE_PAIR_APP_NAME handling), under yet another distinct "app" name
 *   so it doesn't mix with either soundbar-wake presets or settings-backup
 *   presets in that picker. Approving it doesn't hand back saved
 *   credentials - the server mints a fresh, long-lived device token and
 *   sends that back instead, once.
 * - Backup/restore (uploadBackup/listBackups/fetchBackup): the device
 *   holds onto that token (see LauncherPreferences.observeConfigSyncDeviceToken)
 *   and calls device_backup.php/device_backups_list.php/device_backup_get.php
 *   directly from then on - no further code/QR, no further human approval
 *   per call, unlike the pairing step or SoundbarPairing's flow.
 *
 * All functions here perform blocking network I/O — callers must run them
 * off the main thread (Dispatchers.IO, as SettingsScreen.kt does).
 */
object LauncherConfigSync {

    private const val TAG = "LauncherConfigSync"

    private const val START_URL = "https://mx3launcher.odiousapps.com/credential_start.php"
    private const val STATUS_URL = "https://mx3launcher.odiousapps.com/credential_status.php"
    private const val BACKUP_URL = "https://mx3launcher.odiousapps.com/device_backup.php"
    private const val LIST_URL = "https://mx3launcher.odiousapps.com/device_backups_list.php"
    private const val GET_URL = "https://mx3launcher.odiousapps.com/device_backup_get.php"
    const val VIEW_URL_PREFIX = "https://mx3launcher.odiousapps.com/credential_view.php?code="

    // Kept in sync with credential_view.php's own copy of this constant.
    private const val DEVICE_PAIR_APP_NAME = "MX3Launcher Device"
    private const val FIELD_DEVICE_TOKEN = "DeviceToken"
    private const val FIELD_CONFIG = "Config"

    private const val KEY_THEME_MODE = "themeMode"
    private const val KEY_GRADIENT_ID = "gradientId"
    private const val KEY_COLUMNS = "columns"
    private const val KEY_HIDDEN_PACKAGES = "hiddenPackages"
    private const val KEY_APP_ORDER = "appOrder"
    private val KNOWN_KEYS = listOf(KEY_THEME_MODE, KEY_GRADIENT_ID, KEY_COLUMNS, KEY_HIDDEN_PACKAGES, KEY_APP_ORDER)

    data class PairingSession(val code: String, val token: String, val expiresInSeconds: Int) {
        val approveUrl: String get() = "$VIEW_URL_PREFIX$code"
    }

    sealed class PairingPollResult {
        object Pending : PairingPollResult()
        object Expired : PairingPollResult()
        data class Paired(val deviceToken: String) : PairingPollResult()
        data class Error(val message: String) : PairingPollResult()
    }

    data class RemoteBackup(val id: Int, val label: String, val createdAt: String)

    /** Starts a one-time "pull" share asking the account holder to pair
     *  this device. See credential_view.php's DEVICE_PAIR_APP_NAME branch. */
    fun pairDevice(): PairingSession? {
        return try {
            startSession(JSONObject().put("app", DEVICE_PAIR_APP_NAME))
        } catch (e: Exception) {
            Log.w(TAG, "pairDevice failed", e)
            null
        }
    }

    fun pollPairing(token: String): PairingPollResult {
        return try {
            val json = JSONObject(PairingHttp.get(pollUrl(token)))
            when (json.optString("status")) {
                "viewed" -> {
                    val deviceToken = (json.optJSONObject("fields") ?: JSONObject()).optString(FIELD_DEVICE_TOKEN)
                    if (deviceToken.isBlank()) {
                        PairingPollResult.Error("Approval didn't include a device token")
                    } else {
                        PairingPollResult.Paired(deviceToken)
                    }
                }
                "pending" -> PairingPollResult.Pending
                "expired" -> PairingPollResult.Expired
                else -> PairingPollResult.Error(json.optString("error", "Unknown error"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "pollPairing: request failed", e)
            PairingPollResult.Error(e.message ?: "Network error")
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

    private fun pollUrl(token: String): String {
        val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
        return "$STATUS_URL?token=$encodedToken"
    }

    /** Returns true on success. */
    fun uploadBackup(deviceToken: String, settings: LauncherSettings): Boolean {
        return try {
            val body = JSONObject().put("token", deviceToken).put("config", toJson(settings))
            JSONObject(PairingHttp.post(BACKUP_URL, body.toString())).optBoolean("ok", false)
        } catch (e: Exception) {
            Log.w(TAG, "uploadBackup failed", e)
            false
        }
    }

    /** Returns null on failure (network error, revoked token, ...); an
     *  empty list just means no backups exist yet. */
    fun listBackups(deviceToken: String): List<RemoteBackup>? {
        return try {
            val encodedToken = java.net.URLEncoder.encode(deviceToken, "UTF-8")
            val json = JSONObject(PairingHttp.get("$LIST_URL?token=$encodedToken"))
            if (!json.optBoolean("ok", false)) return null

            val array = json.optJSONArray("backups") ?: JSONArray()
            (0 until array.length()).map { i ->
                val entry = array.getJSONObject(i)
                RemoteBackup(
                    id = entry.optInt("id"),
                    label = entry.optString("label"),
                    createdAt = entry.optString("created_at"),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "listBackups failed", e)
            null
        }
    }

    /** Returns the parsed settings on success, or null if the fetch or
     *  parse failed. */
    fun fetchBackup(deviceToken: String, id: Int): LauncherSettings? {
        return try {
            val encodedToken = java.net.URLEncoder.encode(deviceToken, "UTF-8")
            val json = JSONObject(PairingHttp.get("$GET_URL?token=$encodedToken&id=$id"))
            if (!json.optBoolean("ok", false)) return null
            fromJson(json.optString("config"))
        } catch (e: Exception) {
            Log.w(TAG, "fetchBackup failed", e)
            null
        }
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
