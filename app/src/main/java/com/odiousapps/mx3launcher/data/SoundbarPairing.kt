package com.odiousapps.mx3launcher.data

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * OAuth-device-flow-style pairing, scaled down for a personal setup: the TV
 * shows a short code and polls in the background; the URL+secret are typed
 * only on a phone, never the TV (see SoundbarPairingSection in
 * SettingsScreen.kt for why).
 *
 * Talks to mx3launcher.odiousapps.com's credential relay in "pull" mode:
 * this device asks to receive credentials for "MX3Launcher", and the account
 * holder picks a saved preset at credential_view.php. See that project's
 * README for the protocol; "URL"/"Secret" are its documented field names.
 *
 * All functions here perform blocking network I/O — callers must run them
 * off the main thread (Dispatchers.IO, as SettingsScreen.kt does).
 */
object SoundbarPairing {

    private const val TAG = "SoundbarPairing"

    // Self-service pairing site (accounts + saved-credential presets), separate
    // from any individual user's own home server.
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
            // Pull-mode request: asks to receive credentials, offers none.
            val body = JSONObject().put("app", APP_NAME)
            val response = httpPost(START_URL, body.toString())
            val json = JSONObject(response)
            if (!json.optBoolean("ok", false)) {
                Log.w(TAG, "startPairing: $START_URL returned ok=false: $response")
                return null
            }
            PairingSession(
                code = json.getString("code"),
                token = json.getString("token"),
                expiresInSeconds = json.optInt("expires_in", 600),
            )
        } catch (e: Exception) {
            Log.w(TAG, "startPairing: request to $START_URL failed", e)
            null
        }
    }

    fun pollPairing(token: String): PollResult {
        return try {
            val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
            val requestUrl = "$STATUS_URL?token=$encodedToken"
            val response = httpGet(requestUrl)
            val json = JSONObject(response)
            when (json.optString("status")) {
                "viewed" -> {
                    val fields = json.optJSONObject("fields") ?: JSONObject()
                    val url = fields.optString("URL")
                    val secret = fields.optString("Secret")
                    if (url.isBlank() || secret.isBlank()) {
                        // Don't silently pair with a blank URL/secret.
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
            Log.w(TAG, "pollPairing: request to $STATUS_URL failed", e)
            PollResult.Error(e.message ?: "Network error")
        }
    }

    /** Reads the response body on a 2xx status, or throws with the response
     *  code, URL, and server-provided error body (if any) baked into the
     *  message — so a caller's log line shows why a request failed instead
     *  of a bare, contextless stream exception. */
    private fun readResponseOrThrow(connection: HttpURLConnection): String {
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            return connection.inputStream.bufferedReader().use { it.readText() }
        }
        val errorBody = connection.errorStream
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?.trim()
            ?.take(500)
            .orEmpty()
        val suffix = if (errorBody.isNotEmpty()) ": $errorBody" else ""
        throw IOException("HTTP $responseCode from ${connection.url}$suffix")
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"
        return try {
            readResponseOrThrow(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPost(url: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        return try {
            readResponseOrThrow(connection)
        } finally {
            connection.disconnect()
        }
    }
}
