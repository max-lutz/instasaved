package com.maxlutz.instasaved.backup

import com.maxlutz.instasaved.data.Backup
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.CollectionDeletion
import com.maxlutz.instasaved.data.DeletedPostTrace
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The manual backup file's format. A file of a higher version comes from a newer app and is refused. */
const val BACKUP_FILE_VERSION = 1

private const val APP = "instasaved"

/**
 * The file is not a backup file this app can restore; nothing from it should be applied.
 *
 * @property fromNewerApp the file is a backup file, but of a format this version of the app does not know yet.
 */
class BackupFormatException(message: String, cause: Throwable? = null, val fromNewerApp: Boolean = false) :
    Exception(message, cause)

/**
 * The Backup as the content of a manual backup file: one JSON object, independent of the database's schema.
 * A Post's Thumbnail download history stays out, like the Thumbnails themselves (ADR-0001).
 *
 * @param createdAt when the Backup is taken, in epoch milliseconds.
 */
fun Backup.toJson(createdAt: Long): String {
    val tagIdsByPost = postTags.groupBy({ it.postId }, { it.tagId })
    return buildJsonObject {
        put("app", APP)
        put("version", BACKUP_FILE_VERSION)
        put("createdAt", createdAt)
        putJsonArray("collections") {
            collections.forEach {
                add(
                    buildJsonObject {
                        put("id", it.id)
                        put("name", it.name)
                        put("color", it.color)
                        put("note", it.note)
                    },
                )
            }
        }
        putJsonArray("tags") {
            tags.forEach {
                add(
                    buildJsonObject {
                        put("id", it.id)
                        put("name", it.name)
                        put("color", it.color)
                    },
                )
            }
        }
        putJsonArray("posts") {
            posts.forEach { post ->
                add(
                    buildJsonObject {
                        put("id", post.id)
                        put("shortcode", post.shortcode)
                        put("url", post.url)
                        put("addedAt", post.addedAt)
                        put("modifiedAt", post.modifiedAt)
                        put("seenInExport", post.seenInExport)
                        put("title", post.title)
                        put("titleHandEdited", post.titleHandEdited)
                        put("description", post.description)
                        put("descriptionHandEdited", post.descriptionHandEdited)
                        put("postNote", post.postNote)
                        put("ownerUsername", post.ownerUsername)
                        put("ownerName", post.ownerName)
                        putJsonArray("instagramCollections") { post.instagramCollections.forEach { add(JsonPrimitive(it)) } }
                        post.collectionId?.let { put("collectionId", it) }
                        put("tagIds", buildJsonArray { tagIdsByPost[post.id].orEmpty().forEach { add(JsonPrimitive(it)) } })
                        post.deletedAt?.let { put("deletedAt", it) }
                        post.deletionId?.let { put("deletionId", it) }
                    },
                )
            }
        }
        putJsonArray("collectionDeletions") {
            collectionDeletions.forEach {
                add(
                    buildJsonObject {
                        put("id", it.id)
                        put("collectionName", it.collectionName)
                        put("collectionColor", it.collectionColor)
                        put("collectionNote", it.collectionNote)
                    },
                )
            }
        }
        putJsonArray("deletedPostTraces") { deletedPostTraces.forEach { add(JsonPrimitive(it.shortcode)) } }
    }.toString()
}

/**
 * The Backup a manual backup file holds. Every Post comes back with no Thumbnail download history, so each one
 * missing its Thumbnail is tried again.
 *
 * @throws BackupFormatException when [json] is not a backup file of this app, or is one from a newer app.
 */
fun parseBackup(json: String): Backup {
    val root = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (e: SerializationException) {
        throw BackupFormatException("The file is not JSON", e)
    } ?: throw BackupFormatException("The file is not a JSON object")
    if ((root["app"] as? JsonPrimitive)?.takeIf { it.isString }?.content != APP) {
        throw BackupFormatException("The file is not an InstaSaved backup")
    }
    val version = root.int("version")
    if (version > BACKUP_FILE_VERSION) {
        throw BackupFormatException("The backup file is of version $version", fromNewerApp = true)
    }

    val postTags = mutableListOf<PostTag>()
    val posts = root.objects("posts").map { post ->
        val id = post.long("id")
        post.array("tagIds").forEach { postTags += PostTag(id, it.asLong("tagIds")) }
        Post(
            id = id,
            shortcode = post.string("shortcode"),
            url = post.string("url"),
            addedAt = post.long("addedAt"),
            modifiedAt = post.long("modifiedAt"),
            seenInExport = post.boolean("seenInExport"),
            title = post.string("title"),
            titleHandEdited = post.boolean("titleHandEdited"),
            description = post.string("description"),
            descriptionHandEdited = post.boolean("descriptionHandEdited"),
            postNote = post.string("postNote"),
            deletedAt = post.longOrNull("deletedAt"),
            collectionId = post.longOrNull("collectionId"),
            deletionId = post.longOrNull("deletionId"),
            ownerUsername = post.string("ownerUsername"),
            ownerName = post.string("ownerName"),
            // Absent from the files of apps before Sync.
            instagramCollections = post.optionalArray("instagramCollections").map { it.asString("instagramCollections") },
        )
    }
    return Backup(
        posts = posts,
        collections = root.objects("collections").map {
            Collection(it.long("id"), it.string("name"), it.int("color"), it.string("note"))
        },
        collectionDeletions = root.objects("collectionDeletions").map {
            CollectionDeletion(
                it.long("id"),
                it.string("collectionName"),
                it.int("collectionColor"),
                it.string("collectionNote"),
            )
        },
        tags = root.objects("tags").map { Tag(it.long("id"), it.string("name"), it.int("color")) },
        postTags = postTags,
        deletedPostTraces = root.array("deletedPostTraces").map {
            DeletedPostTrace((it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: malformed("deletedPostTraces"))
        },
    )
}

private fun malformed(key: String): Nothing = throw BackupFormatException("The backup file has no valid \"$key\"")

private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: malformed(key)

private fun JsonObject.optionalArray(key: String): JsonArray = if (this[key] == null) JsonArray(emptyList()) else array(key)

private fun JsonObject.objects(key: String): List<JsonObject> = array(key).map { it as? JsonObject ?: malformed(key) }

private fun JsonObject.primitive(key: String): JsonPrimitive =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull } ?: malformed(key)

private fun JsonObject.string(key: String): String = primitive(key).takeIf { it.isString }?.content ?: malformed(key)

private fun JsonObject.long(key: String): Long = primitive(key).asLong(key)

private fun JsonObject.longOrNull(key: String): Long? =
    if (this[key] == null || this[key] is JsonNull) null else long(key)

private fun JsonObject.int(key: String): Int =
    primitive(key).takeIf { !it.isString }?.intOrNull ?: malformed(key)

private fun JsonObject.boolean(key: String): Boolean =
    primitive(key).takeIf { !it.isString }?.booleanOrNull ?: malformed(key)

private fun JsonElement.asString(key: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: malformed(key)

private fun JsonElement.asLong(key: String): Long =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: malformed(key)
