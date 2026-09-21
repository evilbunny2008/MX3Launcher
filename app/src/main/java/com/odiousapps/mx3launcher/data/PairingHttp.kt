package com.odiousapps.mx3launcher.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Shared blocking HTTP helpers for mx3launcher.odiousapps.com's credential
 * relay (see that project's website/README.md for the protocol), used by
 * both SoundbarPairing.kt and LauncherConfigSync.kt - two independent
 * pairings against the same site, under different "app" names so their
 * saved presets don't mix together in the picker at credential_view.php.
 *
 * All functions here perform blocking network I/O — callers must run them
 * off the main thread (Dispatchers.IO).
 */
internal object PairingHttp {

    fun get(url: String): String {
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

    fun post(url: String, body: String): String {
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
}
