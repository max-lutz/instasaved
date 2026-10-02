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

    /** Posts in To sort, newest first. Every Post is in To sort until Collections exist. */
    @Query("SELECT * FROM posts WHERE deletedAt IS NULL ORDER BY addedAt DESC, id DESC")
    fun observeToSort(): Flow<List<Post>>

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
}

suspend fun PostDao.updateText(post: Post) = updateText(
    id = post.id,
    title = post.title,
    titleHandEdited = post.titleHandEdited,
    description = post.description,
    descriptionHandEdited = post.descriptionHandEdited,
    postNote = post.postNote,
)
