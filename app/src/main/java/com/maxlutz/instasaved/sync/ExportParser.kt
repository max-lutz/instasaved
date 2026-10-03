package com.maxlutz.instasaved.sync

import com.maxlutz.instasaved.share.PostLink
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Where an Export keeps its saved posts, relative to the Export's folder. */
const val SAVED_POSTS_PATH = "your_instagram_activity/saved/saved_posts.json"

/** Where an Export keeps its Instagram Collections. An Export with no Instagram Collection has no such file. */
const val SAVED_COLLECTIONS_PATH = "your_instagram_activity/saved/saved_collections.json"

/**
 * A saved post as an Export describes it (sync-spec, pipeline step 4).
 *
 * @property url the post's link as the Export gives it; [shortcode] is its identity (ADR-0007).
 * @property caption the post's first non-blank caption (a carousel repeats the field per slide), or null.
 * @property savedAt when the post was saved on Instagram, in epoch milliseconds.
 * @property instagramCollections the trimmed names of the Instagram Collections the post is in, in the Export's
 *   order, each exact name once.
 */
data class ExportedPost(
    val shortcode: String,
    val url: String,
    val caption: String?,
    val ownerUsername: String?,
    val ownerName: String?,
    val savedAt: Long,
    val instagramCollections: List<String>,
)

/** The Export is not in a format the parser knows; nothing from it should be applied. */
class ExportFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Meta writes field labels in the account's language, and it varies from one Export to the next.
private val captionLabels = setOf("Caption", "Légende")
private val ownerTitles = setOf("Owner", "Propriétaire")
private val usernameLabels = setOf("Username", "Nom de profil")
private val nameLabels = setOf("Name", "Nom")

/**
 * The saved posts of one Export, newest first, one per shortcode.
 *
 * @param savedPostsJson the content of [SAVED_POSTS_PATH].
 * @param savedCollectionsJson the content of [SAVED_COLLECTIONS_PATH], or null when the Export has no such file.
 * @throws ExportFormatException when either file is not the JSON this parser knows, including labels in a
 *   language it does not know: reading those as "no caption, no owner" would silently blank Descriptions.
 */
fun parseExport(savedPostsJson: String, savedCollectionsJson: String?): List<ExportedPost> {
    val entries = entriesOf(savedPostsJson, "saved_posts.json")
    if (entries.isNotEmpty() && entries.none { it.fields().any { field -> field.string("title") in ownerTitles } }) {
        throw ExportFormatException("saved_posts.json has no owner field under a known label")
    }
    val collections = savedCollectionsJson?.let(::collectionsByShortcode).orEmpty()
    return entries.mapNotNull { entry ->
        val fields = entry.fields()
        val link = fields.valuesOf(setOf("URL")).firstNotNullOfOrNull(PostLink::find) ?: return@mapNotNull null
        val owner = fields.filter { it.string("title") in ownerTitles }.flatMap { it.items("dict") }
            .firstOrNull()?.items("dict").orEmpty()
        val savedAtSeconds = (entry["timestamp"] as? JsonPrimitive)?.longOrNull
            ?: throw ExportFormatException("saved_posts.json has a post without a timestamp")
        ExportedPost(
            shortcode = link.shortcode,
            url = link.url,
            caption = fields.valuesOf(captionLabels).firstOrNull { it.isNotBlank() },
            ownerUsername = owner.valuesOf(usernameLabels).firstOrNull { it.isNotBlank() },
            ownerName = owner.valuesOf(nameLabels).firstOrNull { it.isNotBlank() },
            savedAt = savedAtSeconds * 1000,
            instagramCollections = collections[link.shortcode]?.toList().orEmpty(),
        )
    }.distinctBy { it.shortcode }
}

/**
 * Repairs text from Meta's JSON, which stores UTF-8 bytes as Latin-1 code points (`Câ€™est` for `C’est`).
 * Text that is not such a mix-up is returned as it is.
 */
fun repairText(text: String): String {
    if (text.any { it.code > 0xFF }) return text
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1))).toString()
    } catch (_: CharacterCodingException) {
        text
    }
}

/** The names of the Instagram Collections each post is in, by shortcode. */
private fun collectionsByShortcode(savedCollectionsJson: String): Map<String, Set<String>> {
    val entries = entriesOf(savedCollectionsJson, "saved_collections.json")
    val collections = mutableMapOf<String, MutableSet<String>>()
    for (entry in entries) {
        val fields = entry.fields()
        val name = fields.valuesOf(nameLabels).firstOrNull()?.trim()
            ?: throw ExportFormatException("saved_collections.json has no name field under a known label")
        if (name.isEmpty()) continue
        // The only group of a collection is its posts, so its title ("Contenu multimédia") is not needed.
        fields.flatMap { it.items("dict") }
            .mapNotNull { post -> post.items("dict").valuesOf(setOf("URL")).firstNotNullOfOrNull(PostLink::find) }
            .forEach { collections.getOrPut(it.shortcode, ::mutableSetOf).add(name) }
    }
    return collections
}

/** A full Export is a list of entries; one with a single entry is that entry alone, not in a list. */
private fun entriesOf(json: String, file: String): List<JsonObject> {
    val root = try {
        Json.parseToJsonElement(json)
    } catch (e: SerializationException) {
        throw ExportFormatException("$file is not valid JSON", e)
    }
    val entries = when (root) {
        is JsonArray -> root.toList()
        is JsonObject -> listOf(root)
        else -> throw ExportFormatException("$file is neither a list nor an entry")
    }
    return entries.map { entry ->
        (entry as? JsonObject)?.takeIf { it["label_values"] is JsonArray }
            ?: throw ExportFormatException("$file has an entry without label_values")
    }
}

private fun JsonObject.fields(): List<JsonObject> = items("label_values")

private fun JsonObject.items(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::repairText)

private fun List<JsonObject>.valuesOf(labels: Set<String>): List<String> =
    filter { it.string("label") in labels }.mapNotNull { it.string("value") }
