package com.odiousapps.mx3launcher.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Uses org.json (built-in) rather than adding a JSON dependency, given this
 * project's existing Compose/tv-material dependency friction.
 *
 * Writes to MediaStore's Downloads collection (no storage permission needed,
 * API 29+) instead of: SAF's picker, which fails on TV boxes with no
 * DocumentsUI-equivalent handler; or the app's own external-files dir,
 * which scoped storage hides from file managers on Android 11+.
 *
 * Each backup gets its own timestamped filename, so restore can list what's
 * available rather than assuming exactly one.
 */
object LauncherBackup {

    private const val KEY_THEME_MODE = "themeMode"
    private const val KEY_GRADIENT_ID = "gradientId"
    private const val KEY_COLUMNS = "columns"
    private const val KEY_HIDDEN_PACKAGES = "hiddenPackages"
    private const val KEY_APP_ORDER = "appOrder"

    private const val BACKUP_FILE_PREFIX = "mx3launcher_backup_"
    private const val BACKUP_FILE_EXTENSION = ".json"
    private const val BACKUP_MIME_TYPE = "application/json"

    private val FILENAME_TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** One entry in the "pick a backup to restore" list. */
    data class BackupEntry(
        val uri: Uri,
        val displayName: String,
        val timestampMillis: Long,
    ) {
        // Built fresh each access, not cached, so a system locale change while
        // running takes effect immediately.
        val displayLabel: String
            get() = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestampMillis))
    }

    /** Shown in the UI to describe where backups live generally, since
     *  there's no longer a single fixed file path. */
    const val BACKUP_LOCATION_DESCRIPTION = "Downloads/$BACKUP_FILE_PREFIX*.json"

    fun toJson(settings: LauncherSettings): String {
        val json = JSONObject()
        json.put(KEY_THEME_MODE, settings.themeMode.name)
        json.put(KEY_GRADIENT_ID, settings.gradientId)
        json.put(KEY_COLUMNS, settings.columns)
        json.put(KEY_HIDDEN_PACKAGES, JSONArray(settings.hiddenPackages.toList()))
        json.put(KEY_APP_ORDER, JSONArray(settings.appOrder))
        return json.toString(2)
    }

    private val KNOWN_KEYS = listOf(KEY_THEME_MODE, KEY_GRADIENT_ID, KEY_COLUMNS, KEY_HIDDEN_PACKAGES, KEY_APP_ORDER)

    /** Returns null (rather than throwing) on malformed input, so a
     *  hand-edited or corrupted backup fails the restore cleanly instead
     *  of crashing the launcher.
     *
     *  Every field below is read leniently (opt* with a default) so a
     *  backup from an older app version — missing a key added later —
     *  still restores instead of failing outright. That same leniency
     *  means a JSON object with none of our keys (e.g. an empty "{}" from
     *  a truncated or otherwise-not-really-a-backup file) would otherwise
     *  parse "successfully" into all-default settings with no way to tell
     *  that apart from a real restore — so that case is rejected explicitly
     *  up front instead. */
    fun fromJson(raw: String): LauncherSettings? {
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

    /** Returns true on success. Always creates a NEW timestamped entry —
     *  never overwrites a previous backup. */
    fun writeBackup(context: Context, settings: LauncherSettings): Boolean {
        return try {
            val resolver = context.contentResolver
            val fileName = "$BACKUP_FILE_PREFIX${FILENAME_TIMESTAMP_FORMAT.format(Date())}$BACKUP_FILE_EXTENSION"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, BACKUP_MIME_TYPE)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(toJson(settings).toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Every backup this app has created, newest first. */
    fun listBackups(context: Context): List<BackupEntry> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("$BACKUP_FILE_PREFIX%$BACKUP_FILE_EXTENSION")
        val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

        val results = mutableListOf<BackupEntry>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            // DATE_MODIFIED is in seconds, not millis — must multiply below.
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val dateSeconds = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                results.add(BackupEntry(uri, name, dateSeconds * 1000L))
            }
        }
        return results
    }

    /** Returns the parsed settings on success, or null if the specific
     *  entry couldn't be read or didn't parse as valid. */
    fun readBackup(context: Context, uri: Uri): LauncherSettings? {
        return try {
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return null
            fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.toStringSet(): Set<String> = toStringList().toSet()
}
