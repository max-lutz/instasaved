package com.maxlutz.instasaved.desktop

import com.maxlutz.instasaved.data.Backup
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.DeletedPostTrace
import com.maxlutz.instasaved.data.MAX_TAGS_PER_POST
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.data.titleFrom
import com.maxlutz.instasaved.share.PostLink
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** The only Socials Organizer backup format the Desktop Import reads (ADR-0009). */
const val DESKTOP_SCHEMA_VERSION = 6

// The one provenance that means the Post came out of an Instagram export.
private const val INSTAGRAM_IMPORT = "instagram-import"

private val hexColor = Regex("#[0-9a-fA-F]{6}")

/** The file is not a Socials Organizer v6 backup; nothing from it should be applied. */
class DesktopBackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * What a Socials Organizer backup holds, as the app's own data. The ids are the desktop app's: they only tie the
 * Posts to the Collections and Tags of [data].
 *
 * @property skippedPosts desktop Posts left out: their link is not an Instagram post link, or another Post of the
 *   backup already has their shortcode.
 */
class DesktopBackup(val data: Backup, val skippedPosts: Int)

/**
 * Reads a Socials Organizer v6 backup, mapped as ADR-0009 says: Sections, `source` and `reimport_dismissed` are
 * dropped, shortcodes are extracted from links, `title_manual` carries over, every Post counts as Description
 * hand-edited, Posts that came from an Instagram import count as seen in an Export, and `deleted_posts` become
 * traces of Deleted Posts.
 *
 * @throws DesktopBackupFormatException when [json] is not such a backup.
 */
fun parseDesktopBackup(json: String): DesktopBackup {
    val root = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (e: SerializationException) {
        throw DesktopBackupFormatException("The file is not JSON", e)
    } ?: throw DesktopBackupFormatException("The file is not a JSON object")
    if (root.longOrNull("schema_version") != DESKTOP_SCHEMA_VERSION.toLong()) {
        throw DesktopBackupFormatException("The file is not a Socials Organizer v$DESKTOP_SCHEMA_VERSION backup")
    }

    val posts = mutableListOf<Post>()
    val postTags = mutableListOf<PostTag>()
    val shortcodes = HashSet<String>()
    var skipped = 0
    for (post in root.objects("posts")) {
        val link = PostLink.find(post.string("link"))
        if (link == null || !shortcodes.add(link.shortcode)) {
            skipped++
            continue
        }
        val id = post.long("id")
        val description = post.stringOrNull("description").orEmpty()
        val titleManual = post.flag("title_manual")
        val addedAt = post.long("created_at")
        posts += Post(
            id = id,
            shortcode = link.shortcode,
            url = link.url,
            addedAt = addedAt,
            modifiedAt = post.longOrNull("updated_at") ?: addedAt,
            seenInExport = post.stringOrNull("provenance") == INSTAGRAM_IMPORT,
            title = post.stringOrNull("title") ?: if (titleManual) "" else titleFrom(description),
            titleHandEdited = titleManual,
            description = description,
            // Desktop keeps no such flag: this protects the edits made there (ADR-0009).
            descriptionHandEdited = true,
            postNote = post.stringOrNull("note").orEmpty(),
            collectionId = post.longOrNull("collection_id"),
            ownerUsername = post.stringOrNull("owner_username").orEmpty(),
            ownerName = post.stringOrNull("owner_name").orEmpty(),
        )
        post.arrayOrEmpty("tag_ids").map { it.asLong("tag_ids") }.distinct().take(MAX_TAGS_PER_POST)
            .forEach { postTags += PostTag(id, it) }
    }

    return DesktopBackup(
        data = Backup(
            posts = posts,
            collections = root.objects("collections").map {
                Collection(it.long("id"), it.string("name").trim(), it.color(), it.stringOrNull("note").orEmpty())
            },
            tags = root.objectsOrEmpty("tags").map { Tag(it.long("id"), it.string("name").trim(), it.color()) },
            postTags = postTags,
            deletedPostTraces = root.objectsOrEmpty("deleted_posts")
                .mapNotNull { PostLink.find(it.string("link"))?.shortcode }
                // A Post the backup also holds live is not a Deleted Post.
                .filter { it !in shortcodes }
                .distinct()
                .map(::DeletedPostTrace),
        ),
        skippedPosts = skipped,
    )
}

private fun malformed(key: String): Nothing =
    throw DesktopBackupFormatException("The desktop backup has no valid \"$key\"")

private fun JsonObject.present(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
    present(key)?.let { it as? JsonArray ?: malformed(key) } ?: JsonArray(emptyList())

private fun JsonObject.objectsOrEmpty(key: String): List<JsonObject> =
    arrayOrEmpty(key).map { it as? JsonObject ?: malformed(key) }

private fun JsonObject.objects(key: String): List<JsonObject> =
    if (present(key) == null) malformed(key) else objectsOrEmpty(key)

private fun JsonObject.stringOrNull(key: String): String? =
    present(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: malformed(key) }

private fun JsonObject.string(key: String): String = stringOrNull(key) ?: malformed(key)

private fun JsonObject.longOrNull(key: String): Long? = present(key)?.asLong(key)

private fun JsonObject.long(key: String): Long = longOrNull(key) ?: malformed(key)

// SQLite's 0 / 1, or a JSON boolean.
private fun JsonObject.flag(key: String): Boolean {
    val value = present(key) as? JsonPrimitive ?: return false
    return value.booleanOrNull ?: (value.asLong(key) != 0L)
}

/** The desktop's `#RRGGBB`, as ARGB. Both apps share one palette; anything else gets its first color. */
private fun JsonObject.color(): Int {
    val hex = stringOrNull("color")?.takeIf(hexColor::matches) ?: return PALETTE.first()
    return (0xFF000000 or hex.drop(1).toLong(16)).toInt()
}

private fun JsonElement.asLong(key: String): Long =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: malformed(key)
