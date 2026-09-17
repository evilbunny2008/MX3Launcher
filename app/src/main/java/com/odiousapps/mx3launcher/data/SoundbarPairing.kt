package com.odiousapps.mx3launcher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * OAuth-device-flow-style pairing, scaled down for a personal setup:
 * the TV displays a short code and polls in the background; the actual
 * URL+secret get typed nowhere on the TV at all, only a short code
 * does (and that gets typed on a phone, not the TV) -- this avoids the
 * D-pad navigation trap that plain TextFields hit (once focused/typing
 * in one, Down doesn't reliably move focus to whatever's below it,
 * since text-edit mode captures D-pad input for cursor movement rather
 * than surfacing it for inter-component navigation).
 *
 * Talks to mx3launcher.odiousapps.com's generic credential relay in
 * "pull" mode -- this device has no credentials of its own, so it asks
 * to receive some for app "MX3Launcher", and the account holder picks
 * one of their own saved presets for it at credential_view.php. See
 * that project's website/README.md for the full protocol; the fields
 * this looks for by name below ("URL", "Secret") are the convention
 * documented there for anyone setting up a preset for this app.
 *
 * All functions here perform blocking network I/O -- callers must run
 * them off the main thread (a coroutine on Dispatchers.IO, in
 * SettingsScreen.kt's usage).
 */
object SoundbarPairing {

    // mx3launcher.odiousapps.com is the self-service pairing site
    // (accounts + named saved-credential presets per account) -- a
    // separate domain from any individual user's own home server.
    private const val START_URL = "https://mx3launcher.odiousapps.com/credential_start.php"
    private const val STATUS_URL = "https://mx3launcher.odiousapps.com/credential_status.php"

    private const val APP_NAME = "MX3Launcher"

    data class PairingSession(val code: String, val token: String, val expiresInSeconds: Int)

    sealed class PollResult {
        object Pending : PollResult()
        object Expired : PollResult()
        data class Approved(val url: String, val secret: String) : PollResult()
        data class Error(val message: String) : PollResult()
    }

    fun startPairing(): PairingSession? {
        return try {
            // No "fields" - this is a pull-mode request, asking to
            // RECEIVE credentials rather than offering any of its own.
            val body = JSONObject().put("app", APP_NAME)
            val response = httpPost(START_URL, body.toString()) ?: return null
            val json = JSONObject(response)
            if (!json.optBoolean("ok", false)) return null
            PairingSession(
                code = json.getString("code"),
                token = json.getString("token"),
                expiresInSeconds = json.optInt("expires_in", 600),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun pollPairing(token: String): PollResult {
        return try {
            val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
            val response = httpGet("$STATUS_URL?token=$encodedToken")
                ?: return PollResult.Error("No response")
            val json = JSONObject(response)
            when (json.optString("status")) {
                "viewed" -> {
                    val fields = json.optJSONObject("fields") ?: JSONObject()
                    val url = fields.optString("URL")
                    val secret = fields.optString("Secret")
                    if (url.isBlank() || secret.isBlank()) {
                        // The chosen preset didn't have both expected keys -
                        // report it plainly rather than silently pairing
                        // with a blank URL/secret.
                        PollResult.Error("Saved preset is missing a URL or Secret field")
                    } else {
                        PollResult.Approved(url = url, secret = secret)
                    }
                }
                "pending" -> PollResult.Pending
                "expired" -> PollResult.Expired
                else -> PollResult.Error(json.optString("error", "Unknown error"))
            }
        } catch (e: Exception) {
            PollResult.Error(e.message ?: "Network error")
        }
    }

    private fun httpGet(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPost(url: String, body: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
