package com.maxlutz.instasaved.sync

import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.data.sameName
import com.maxlutz.instasaved.data.titleFrom

/**
 * A Post as the sync rules read and change it.
 *
 * @property collection the name of the Collection the Post is in; null means To sort.
 * @property instagramCollections the Instagram Collections the last Export listed it in, for display only.
 */
data class SyncPost(
    val shortcode: String,
    val collection: String? = null,
    val title: String = "",
    val titleHandEdited: Boolean = false,
    val description: String = "",
    val descriptionHandEdited: Boolean = false,
    val ownerUsername: String = "",
    val ownerName: String = "",
    val seenInExport: Boolean = false,
    val noLongerSaved: Boolean = false,
    val instagramCollections: List<String> = emptyList(),
)

/** A Collection as the sync rules see it: Posts refer to it by [name], unique ignoring case (ADR-0006). */
data class SyncCollection(val name: String, val color: Int)

/**
 * What the app holds when a Sync starts.
 *
 * @property posts the Posts in the app, Deleted Posts excluded.
 * @property deletedShortcodes the Deleted Posts, those in Recently deleted and those reduced to a trace alike
 *   (ADR-0012).
 */
data class AppState(
    val posts: List<SyncPost> = emptyList(),
    val collections: List<SyncCollection> = emptyList(),
    val deletedShortcodes: Set<String> = emptySet(),
)

/**
 * A Post an Export brings into the app, to be marked New.
 *
 * @property savedAt when it was saved on Instagram, in epoch milliseconds.
 */
data class AddedPost(val post: SyncPost, val url: String, val savedAt: Long)

/**
 * What a Sync changes, to apply in one transaction.
 *
 * @property createdCollections the Collections to create, in the order to create them.
 * @property updatedPosts the Posts already in the app that change, as they are afterwards.
 */
data class SyncChanges(
    val createdCollections: List<SyncCollection> = emptyList(),
    val addedPosts: List<AddedPost> = emptyList(),
    val updatedPosts: List<SyncPost> = emptyList(),
) {
    val isEmpty get() = createdCollections.isEmpty() && addedPosts.isEmpty() && updatedPosts.isEmpty()
}

/** The Sync Summary's counts; [isEmpty] means there is nothing to show. */
data class SyncSummary(val new: Int = 0, val backOnInstagram: Int = 0, val captionsUpdated: Int = 0) {
    val isEmpty get() = new == 0 && backOnInstagram == 0 && captionsUpdated == 0
}

data class SyncResult(val changes: SyncChanges, val summary: SyncSummary)

// Ignoring case first, so that "books" and "Books" sort together whatever the Export's order.
private val alphabetical = String.CASE_INSENSITIVE_ORDER.then(naturalOrder())

/**
 * The changes an Export makes to the app, by the rules of `docs/sync-spec.md`: R1 to R3a and R5, which make it
 * idempotent (R6). Nothing here touches Drive or the database; the caller picks the newest Export (R7), parses it
 * and applies the result.
 *
 * Not here yet: R4 (No longer saved) and the sanity check S1, which wait on whether a scheduled Export is a
 * complete snapshot (sync-spec, Export file format).
 *
 * @param export the Export's posts, one per shortcode, as [parseExport] returns them.
 */
fun planSync(app: AppState, export: List<ExportedPost>): SyncResult {
    val posts = app.posts.associateBy { it.shortcode }
    val created = mutableListOf<SyncCollection>()
    val added = mutableListOf<AddedPost>()
    val updated = mutableListOf<SyncPost>()
    var backOnInstagram = 0
    var captionsUpdated = 0

    // Placement. Only the Collections the app had before this Sync count as existing: counting the ones created
    // along the way would make the pick depend on the Export's order.
    fun place(instagramCollections: List<String>): String? {
        val names = instagramCollections.sortedWith(alphabetical)
        val first = names.firstOrNull() ?: return null
        names.firstNotNullOfOrNull { name -> app.collections.find { sameName(it.name, name) } }
            ?.let { return it.name }
        created.find { sameName(it.name, first) }?.let { return it.name }
        created += SyncCollection(first, nextColor((app.collections + created).map { it.color }))
        return first
    }

    for (exported in export) {
        if (exported.shortcode in app.deletedShortcodes) continue // R2
        val caption = exported.caption.orEmpty()
        val post = posts[exported.shortcode]
        if (post == null) { // R1
            added += AddedPost(
                post = SyncPost(
                    shortcode = exported.shortcode,
                    collection = place(exported.instagramCollections),
                    title = titleFrom(caption),
                    description = caption,
                    ownerUsername = exported.ownerUsername.orEmpty(),
                    ownerName = exported.ownerName.orEmpty(),
                    seenInExport = true,
                    instagramCollections = exported.instagramCollections,
                ),
                url = exported.url,
                savedAt = exported.savedAt,
            )
            continue
        }
        // R3
        val captionChanged = !post.descriptionHandEdited && caption != post.description
        val firstSighting = !post.seenInExport && post.collection == null // R3a
        val synced = post.copy(
            collection = if (firstSighting) place(exported.instagramCollections) else post.collection,
            title = if (captionChanged && !post.titleHandEdited) titleFrom(caption) else post.title,
            description = if (captionChanged) caption else post.description,
            ownerUsername = post.ownerUsername.ifEmpty { exported.ownerUsername.orEmpty() },
            ownerName = post.ownerName.ifEmpty { exported.ownerName.orEmpty() },
            seenInExport = true,
            noLongerSaved = false,
            instagramCollections = exported.instagramCollections,
        )
        if (captionChanged) captionsUpdated++
        if (post.noLongerSaved) backOnInstagram++
        if (synced != post) updated += synced
    }

    return SyncResult(
        SyncChanges(created, added, updated),
        SyncSummary(new = added.size, backOnInstagram = backOnInstagram, captionsUpdated = captionsUpdated),
    )
}
