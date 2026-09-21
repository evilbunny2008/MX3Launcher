package com.odiousapps.mx3launcher.data

import android.util.Log
import org.json.JSONObject

/**
 * OAuth-device-flow-style pairing, scaled down for a personal setup: the TV
 * shows a short code and polls in the background; the URL+secret are typed
 * only on a phone, never the TV (see SoundbarPairingSection in
 * SettingsScreen.kt for why).
 *
 * Talks to sync.odiousapps.com's credential relay in "pull" mode:
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
    private const val START_URL = "https://sync.odiousapps.com/credential_start.php"
    private const val STATUS_URL = "https://sync.odiousapps.com/credential_status.php"

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
            val response = PairingHttp.post(START_URL, body.toString())
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
            val response = PairingHttp.get(requestUrl)
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

}
