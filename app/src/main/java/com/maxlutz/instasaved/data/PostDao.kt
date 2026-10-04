package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    /** Returns the new row id, or -1 if a Post with the same shortcode already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(post: Post): Long

    /** The Post with this shortcode, Deleted Posts included. */
    @Query("SELECT * FROM posts WHERE shortcode = :shortcode")
    suspend fun get(shortcode: String): Post?

    @Query("SELECT * FROM posts WHERE id = :id")
    fun observe(id: Long): Flow<Post?>

    /** Every Post not deleted, newest first. */
    @Query("SELECT * FROM posts WHERE deletedAt IS NULL ORDER BY addedAt DESC, id DESC")
    fun observeAll(): Flow<List<Post>>

    /** Posts in To sort (no Collection), newest first. */
    @Query("SELECT * FROM posts WHERE collectionId IS NULL AND deletedAt IS NULL ORDER BY addedAt DESC, id DESC")
    fun observeToSort(): Flow<List<Post>>

    /** Posts in the Collection, newest first. */
    @Query(
        "SELECT * FROM posts WHERE collectionId = :collectionId AND deletedAt IS NULL ORDER BY addedAt DESC, id DESC",
    )
    fun observeInCollection(collectionId: Long): Flow<List<Post>>

    /** Every Post not deleted: those whose Thumbnail download failed least first, then newest first. */
    @Query("SELECT * FROM posts WHERE deletedAt IS NULL ORDER BY thumbnailFailures, addedAt DESC, id DESC")
    suspend fun getAllFewestThumbnailFailuresFirst(): List<Post>

    /** Puts the Post in a Collection, or in To sort when [collectionId] is null. */
    @Query("UPDATE posts SET collectionId = :collectionId, modifiedAt = :at WHERE id = :id")
    suspend fun setCollection(id: Long, collectionId: Long?, at: Long)

    /** Puts the Posts in a Collection, or in To sort when [collectionId] is null, as one change. */
    @Transaction
    suspend fun setCollection(ids: List<Long>, collectionId: Long?, at: Long) {
        ids.forEach { setCollection(it, collectionId, at) }
    }

    // The subquery leaves the Post in To sort if the Collection is gone.
    @Query(
        "UPDATE posts SET collectionId = (SELECT id FROM collections WHERE id = :collectionId), " +
            "modifiedAt = :modifiedAt WHERE id = :id",
    )
    suspend fun putBack(id: Long, collectionId: Long?, modifiedAt: Long)

    /**
     * Undoes a move: each of [posts], as they were before it, is back in its Collection with its modified date.
     * A Collection deleted meanwhile leaves its Posts in To sort.
     */
    @Transaction
    suspend fun undoMove(posts: List<Post>) {
        posts.forEach { putBack(it.id, it.collectionId, it.modifiedAt) }
    }

    /** Saves the user's text and its hand-edited flags, leaving everything else (e.g. deletion) as stored. */
    @Query(
        "UPDATE posts SET title = :title, titleHandEdited = :titleHandEdited, description = :description, " +
            "descriptionHandEdited = :descriptionHandEdited, postNote = :postNote, modifiedAt = :at WHERE id = :id",
    )
    suspend fun updateText(
        id: Long,
        title: String,
        titleHandEdited: Boolean,
        description: String,
        descriptionHandEdited: Boolean,
        postNote: String,
        at: Long,
    )

    /** Moves the Post to Recently deleted (ADR-0012). [RecentlyDeletedDao.restore] takes it back out. */
    @Query("UPDATE posts SET deletedAt = :at WHERE id = :id")
    suspend fun delete(id: Long, at: Long)

    /** Moves the Posts to Recently deleted as one change. [RecentlyDeletedDao.restore] takes them back out. */
    @Transaction
    suspend fun delete(ids: List<Long>, at: Long) {
        ids.forEach { delete(it, at) }
    }

    /** Counts one more failed Thumbnail download. */
    @Query("UPDATE posts SET thumbnailFailures = thumbnailFailures + 1, thumbnailFailedAt = :at WHERE id = :id")
    suspend fun thumbnailFailed(id: Long, at: Long)

    /** Forgets the failed Thumbnail downloads, now that one worked. */
    @Query("UPDATE posts SET thumbnailFailures = 0, thumbnailFailedAt = NULL WHERE id = :id")
    suspend fun thumbnailSaved(id: Long)
}

suspend fun PostDao.updateText(post: Post, at: Long) = updateText(
    id = post.id,
    title = post.title,
    titleHandEdited = post.titleHandEdited,
    description = post.description,
    descriptionHandEdited = post.descriptionHandEdited,
    postNote = post.postNote,
    at = at,
)
