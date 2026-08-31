package com.odiousapps.mx3launcher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
 * All functions here perform blocking network I/O -- callers must run
 * them off the main thread (a coroutine on Dispatchers.IO, in
 * SettingsScreen.kt's usage).
 */
object SoundbarPairing {

    // mx3launcher.odiousapps.com is the self-service pairing site
    // (accounts + multiple named pairing URLs per account) -- a
    // separate domain from any individual user's own home server.
    private const val PAIR_START_URL = "https://mx3launcher.odiousapps.com/pair_start.php"
    private const val PAIR_POLL_URL = "https://mx3launcher.odiousapps.com/pair_poll.php"

    data class PairingSession(val code: String, val token: String, val expiresInSeconds: Int)

    sealed class PollResult {
        object Pending : PollResult()
        object Expired : PollResult()
        data class Approved(val url: String, val secret: String) : PollResult()
        data class Error(val message: String) : PollResult()
    }

    fun startPairing(): PairingSession? {
        return try {
            val response = httpGet(PAIR_START_URL) ?: return null
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
            val response = httpGet("$PAIR_POLL_URL?token=$encodedToken")
                ?: return PollResult.Error("No response")
            val json = JSONObject(response)
            when (json.optString("status")) {
                "approved" -> PollResult.Approved(
                    url = json.getString("url"),
                    secret = json.getString("secret"),
                )
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
}
