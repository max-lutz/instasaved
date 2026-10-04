package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TagDao {
    /** All Tags, alphabetically, each with its Post count. */
    @Query(
        "SELECT tags.*, COUNT(posts.id) AS postCount FROM tags " +
            "LEFT JOIN post_tags ON post_tags.tagId = tags.id " +
            "LEFT JOIN posts ON posts.id = post_tags.postId AND posts.deletedAt IS NULL " +
            "GROUP BY tags.id ORDER BY tags.name, tags.id",
    )
    abstract fun observeAll(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE id = :id")
    abstract suspend fun get(id: Long): Tag?

    /** The Post's Tags, alphabetically. */
    @Query(
        "SELECT tags.* FROM tags JOIN post_tags ON post_tags.tagId = tags.id WHERE post_tags.postId = :postId " +
            "ORDER BY tags.name, tags.id",
    )
    abstract fun observeOnPost(postId: Long): Flow<List<Tag>>

    /** Which Tags are on which Posts, for grouping a view by Tag. */
    @Query("SELECT * FROM post_tags")
    abstract fun observePostTags(): Flow<List<PostTag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(tag: Tag): Long

    /** Creates a Tag. Returns its id, or null if [name] is blank or another Tag already has it. */
    open suspend fun create(name: String, color: Int): Long? {
        if (name.isBlank()) return null
        return insert(Tag(name = name.trim(), color = color)).takeIf { it != -1L }
    }

    @Query("UPDATE OR IGNORE tags SET name = :name, color = :color WHERE id = :id")
    protected abstract suspend fun updateRow(id: Long, name: String, color: Int): Int

    /** Saves a renamed or recolored Tag. Returns false, changing nothing, if its name is blank or another Tag has it. */
    open suspend fun update(tag: Tag): Boolean =
        tag.name.isNotBlank() && updateRow(tag.id, tag.name.trim(), tag.color) == 1

    @Query("SELECT COUNT(*) FROM post_tags WHERE postId = :postId")
    protected abstract suspend fun countOnPost(postId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(postTag: PostTag): Long

    @Query("DELETE FROM post_tags WHERE postId = :postId AND tagId = :tagId")
    protected abstract suspend fun delete(postId: Long, tagId: Long): Int

    @Query("UPDATE posts SET modifiedAt = :at WHERE id = :postId")
    protected abstract suspend fun setPostModified(postId: Long, at: Long)

    /**
     * Puts the Tag on the Post. Returns false, changing nothing, if the Post already carries [MAX_TAGS_PER_POST]
     * other Tags; a Tag the Post already has counts as added.
     */
    @Transaction
    open suspend fun addToPost(postId: Long, tagId: Long, at: Long): Boolean {
        if (insert(PostTag(postId, tagId)) == -1L) return true
        if (countOnPost(postId) > MAX_TAGS_PER_POST) {
            delete(postId, tagId)
            return false
        }
        setPostModified(postId, at)
        return true
    }

    @Transaction
    open suspend fun removeFromPost(postId: Long, tagId: Long, at: Long) {
        if (delete(postId, tagId) == 1) setPostModified(postId, at)
    }

    /**
     * Puts the Tag on the Posts [addTo] and takes it off the Posts [removeFrom], as one change. A Post that
     * already carries [MAX_TAGS_PER_POST] other Tags is left as it is.
     */
    @Transaction
    open suspend fun setOnPosts(tagId: Long, addTo: List<Long>, removeFrom: List<Long>, at: Long) {
        addTo.forEach { addToPost(it, tagId, at) }
        removeFrom.forEach { removeFromPost(it, tagId, at) }
    }
}
