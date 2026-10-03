package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * What a Desktop Import changed.
 *
 * @property added Posts the app did not have.
 * @property completed Posts the app already had, that got something they lacked.
 */
data class DesktopImported(val added: Int, val completed: Int)

@Dao
abstract class DesktopImportDao {
    // The name columns compare ignoring case.
    @Query("SELECT * FROM collections WHERE name = :name")
    protected abstract suspend fun collectionNamed(name: String): Collection?

    @Query("SELECT id FROM tags WHERE name = :name")
    protected abstract suspend fun tagNamed(name: String): Long?

    @Query("SELECT * FROM posts WHERE shortcode = :shortcode")
    protected abstract suspend fun post(shortcode: String): Post?

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_post_traces WHERE shortcode = :shortcode)")
    protected abstract suspend fun hasTrace(shortcode: String): Boolean

    @Query("SELECT COUNT(*) FROM post_tags WHERE postId = :postId")
    protected abstract suspend fun countTags(postId: Long): Int

    @Insert
    protected abstract suspend fun insert(collection: Collection): Long

    @Query("UPDATE collections SET note = :note WHERE id = :id")
    protected abstract suspend fun setCollectionNote(id: Long, note: String)

    @Insert
    protected abstract suspend fun insert(tag: Tag): Long

    @Insert
    protected abstract suspend fun insert(post: Post): Long

    @Update
    protected abstract suspend fun update(post: Post)

    @Insert
    protected abstract suspend fun insert(postTags: List<PostTag>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(trace: DeletedPostTrace)

    /**
     * Adds what [imported] holds to the app, all or nothing. Nothing the app already has is replaced:
     * - a Collection or Tag whose name is taken becomes the one of that name (a Collection without a note gets the
     *   imported one);
     * - a Post the app already has only gets what it lacks: a Collection if it is in To sort, Tags if it has none,
     *   a Post Note and owner if empty, and the imported Title and Description unless hand-edited in the app;
     * - a Deleted Post of the app stays deleted, and an imported trace never deletes a Post of the app.
     *
     * The ids in [imported] only tie its Posts to its Collections and Tags; the rows get new ones.
     */
    @Transaction
    open suspend fun add(imported: Backup): DesktopImported {
        val collectionIds = imported.collections.filter { it.name.isNotBlank() }.associate { collection ->
            val existing = collectionNamed(collection.name)
            if (existing != null && existing.note.isEmpty() && collection.note.isNotEmpty()) {
                setCollectionNote(existing.id, collection.note)
            }
            collection.id to (existing?.id ?: insert(collection.copy(id = 0)))
        }
        val tagIds = imported.tags.filter { it.name.isNotBlank() }.associate { tag ->
            tag.id to (tagNamed(tag.name) ?: insert(tag.copy(id = 0)))
        }
        val tagIdsByPost = imported.postTags.groupBy({ it.postId }, { it.tagId })

        var added = 0
        var completed = 0
        for (post in imported.posts) {
            if (hasTrace(post.shortcode)) continue
            val collectionId = post.collectionId?.let(collectionIds::get)
            val postTagIds = tagIdsByPost[post.id].orEmpty().mapNotNull(tagIds::get).distinct()
            val existing = post(post.shortcode)
            if (existing == null) {
                val id = insert(post.copy(id = 0, collectionId = collectionId))
                insert(postTagIds.map { PostTag(id, it) })
                added++
            } else if (existing.deletedAt == null) {
                val filled = existing.completedBy(post, collectionId)
                val untagged = postTagIds.isNotEmpty() && countTags(existing.id) == 0
                if (filled != existing) update(filled)
                if (untagged) insert(postTagIds.map { PostTag(existing.id, it) })
                if (filled != existing || untagged) completed++
            }
        }
        for (trace in imported.deletedPostTraces) {
            if (post(trace.shortcode) == null) insert(trace)
        }
        return DesktopImported(added, completed)
    }
}

/** This Post of the app, with what it lacks taken from the [imported] one. */
private fun Post.completedBy(imported: Post, collectionId: Long?): Post {
    val takeDescription = !descriptionHandEdited && imported.description.isNotBlank()
    val takeTitle = !titleHandEdited && (imported.titleHandEdited || takeDescription)
    return copy(
        seenInExport = seenInExport || imported.seenInExport,
        title = if (takeTitle) imported.title else title,
        titleHandEdited = if (takeTitle) imported.titleHandEdited else titleHandEdited,
        description = if (takeDescription) imported.description else description,
        // An imported Description counts as hand-edited for good (ADR-0009).
        descriptionHandEdited = descriptionHandEdited || takeDescription,
        postNote = postNote.ifEmpty { imported.postNote },
        collectionId = this.collectionId ?: collectionId,
        ownerUsername = ownerUsername.ifEmpty { imported.ownerUsername },
        ownerName = ownerName.ifEmpty { imported.ownerName },
    )
}
