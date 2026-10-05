package com.maxlutz.instasaved.sync

import com.maxlutz.instasaved.thumbnails.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * An Export folder in Google Drive.
 *
 * @property id the folder's Drive file id: what tells Exports apart, and what Sync remembers once it applied one.
 * @property date the day of the Export, from the folder's name.
 * @property createdTime when Drive got the folder, RFC 3339: orders two Exports of the same day.
 */
data class DriveExport(val id: String, val name: String, val date: LocalDate, val createdTime: String)

/** Oldest first (sync-spec R7). */
val oldestFirst: Comparator<DriveExport> =
    compareBy<DriveExport> { it.date }.thenBy { it.createdTime }.thenBy { it.name }.thenBy { it.id }

/** Google Drive refused the app's access: it was revoked, or the sign-in expired. */
class DriveAccessException(message: String) : IOException(message)

/** The Exports in the user's Google Drive, read-only (ADR-0004). */
interface Drive {
    /** Every Export folder in Drive, applied or not. */
    @Throws(IOException::class)
    suspend fun exports(): List<DriveExport>

    /**
     * The text of the file at [path] in the Export's folder, or null if there is no such file.
     *
     * @param path relative to the Export's folder, like [SAVED_POSTS_PATH].
     */
    @Throws(IOException::class)
    suspend fun read(export: DriveExport, path: String): String?
}

// instagram-<username>-<YYYY-MM-DD>-<random>. Usernames have no hyphen, but the random part might.
private val EXPORT_NAME = Regex("""instagram-[^-]+-(\d{4}-\d{2}-\d{2})-.+""")

/** The day of the Export a Drive folder of this name is, or null if it is not an Export's name. */
fun exportDate(folderName: String): LocalDate? {
    val date = EXPORT_NAME.matchEntire(folderName)?.groupValues?.get(1) ?: return null
    return try {
        LocalDate.parse(date)
    } catch (_: DateTimeParseException) {
        null
    }
}

private const val FOLDER = "application/vnd.google-apps.folder"
private const val FILES = "https://www.googleapis.com/drive/v3/files"

/**
 * [Drive] over the Drive REST API v3, with [http] sending the user's access token.
 *
 * @throws DriveAccessException when Drive answers 401 or 403.
 */
class DriveRest(private val http: Http) : Drive {
    override suspend fun exports(): List<DriveExport> =
        // "contains" matches names by word prefix, so the name is checked again here.
        list("mimeType = '$FOLDER' and name contains 'instagram' and trashed = false").mapNotNull { file ->
            val name = file.string("name") ?: return@mapNotNull null
            val date = exportDate(name) ?: return@mapNotNull null
            DriveExport(file.string("id") ?: return@mapNotNull null, name, date, file.string("createdTime").orEmpty())
        }

    override suspend fun read(export: DriveExport, path: String): String? {
        var parentId = export.id
        val names = path.split('/')
        for ((index, name) in names.withIndex()) {
            val folder = index < names.lastIndex
            val type = if (folder) "mimeType = '$FOLDER'" else "mimeType != '$FOLDER'"
            parentId = list("'$parentId' in parents and name = '${name.quoted()}' and $type and trashed = false")
                .firstNotNullOfOrNull { it.string("id") } ?: return null
        }
        return get("$FILES/$parentId?alt=media").decodeToString()
    }

    /** Every file the query finds, page after page. */
    private suspend fun list(query: String): List<JsonObject> {
        val files = mutableListOf<JsonObject>()
        var pageToken: String? = null
        do {
            val url = "$FILES?q=${query.encoded()}&fields=${"nextPageToken,files(id,name,createdTime)".encoded()}" +
                "&pageSize=1000" + pageToken?.let { "&pageToken=${it.encoded()}" }.orEmpty()
            val page = try {
                Json.parseToJsonElement(get(url).decodeToString()) as? JsonObject
            } catch (e: SerializationException) {
                throw IOException("Drive answered with something else than JSON", e)
            } ?: throw IOException("Drive answered with something else than a JSON object")
            files += (page["files"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
            pageToken = page.string("nextPageToken")
        } while (pageToken != null)
        return files
    }

    private suspend fun get(url: String): ByteArray {
        val response = withContext(Dispatchers.IO) { http.get(url) }
        when (response.status) {
            HttpURLConnection.HTTP_OK -> return response.body
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                throw DriveAccessException("Drive answered ${response.status}")
            else -> throw IOException("Drive answered ${response.status}")
        }
    }
}

private fun String.encoded() = URLEncoder.encode(this, Charsets.UTF_8)

private fun String.quoted() = replace("\\", "\\\\").replace("'", "\\'")

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** [Http] that sends a Google access token, for [DriveRest]. */
class BearerHttp(private val accessToken: String) : Http {
    override fun get(url: String): Http.Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) return Http.Response(status, "", ByteArray(0))
            val body = connection.inputStream.use { it.readBytes() }
            return Http.Response(status, connection.contentType.orEmpty(), body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
