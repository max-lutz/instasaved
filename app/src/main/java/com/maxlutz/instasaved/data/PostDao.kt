package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    @Query("UPDATE posts SET collectionId = :collectionId WHERE id = :id")
    suspend fun setCollection(id: Long, collectionId: Long?)

    /** Saves the user's text and its hand-edited flags, leaving everything else (e.g. deletion) as stored. */
    @Query(
        "UPDATE posts SET title = :title, titleHandEdited = :titleHandEdited, description = :description, " +
            "descriptionHandEdited = :descriptionHandEdited, postNote = :postNote WHERE id = :id",
    )
    suspend fun updateText(
        id: Long,
        title: String,
        titleHandEdited: Boolean,
        description: String,
        descriptionHandEdited: Boolean,
        postNote: String,
    )

    /** Moves the Post to Recently deleted (ADR-0012). */
    @Query("UPDATE posts SET deletedAt = :at WHERE id = :id")
    suspend fun delete(id: Long, at: Long)

    /** Takes the Post back out of Recently deleted. */
    @Query("UPDATE posts SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    /** Counts one more failed Thumbnail download. */
    @Query("UPDATE posts SET thumbnailFailures = thumbnailFailures + 1, thumbnailFailedAt = :at WHERE id = :id")
    suspend fun thumbnailFailed(id: Long, at: Long)

    /** Forgets the failed Thumbnail downloads, now that one worked. */
    @Query("UPDATE posts SET thumbnailFailures = 0, thumbnailFailedAt = NULL WHERE id = :id")
    suspend fun thumbnailSaved(id: Long)
}

suspend fun PostDao.updateText(post: Post) = updateText(
    id = post.id,
    title = post.title,
    titleHandEdited = post.titleHandEdited,
    description = post.description,
    descriptionHandEdited = post.descriptionHandEdited,
    postNote = post.postNote,
)
