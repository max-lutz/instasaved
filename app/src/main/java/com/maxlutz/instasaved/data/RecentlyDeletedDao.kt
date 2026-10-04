package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * What is left of a Deleted Post once it is out of Recently deleted: its shortcode, so that Sync never brings
 * it back (ADR-0012).
 */
@Entity(tableName = "deleted_post_traces")
data class DeletedPostTrace(@PrimaryKey val shortcode: String)

/** The two stages of a Deleted Post (ADR-0012): Recently deleted, whole and restorable, then the trace. */
@Dao
abstract class RecentlyDeletedDao {
    /** The Posts in Recently deleted, last deleted first. */
    @Query("SELECT * FROM posts WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id DESC")
    abstract fun observe(): Flow<List<Post>>

    @Query("SELECT * FROM posts WHERE id = :id AND deletedAt IS NOT NULL")
    protected abstract suspend fun get(id: Long): Post?

    @Query("SELECT * FROM collection_deletions WHERE id = :id")
    protected abstract suspend fun getDeletion(id: Long): CollectionDeletion?

    // The name column compares ignoring case.
    @Query("SELECT id FROM collections WHERE name = :name")
    protected abstract suspend fun collectionNamed(name: String): Long?

    @Insert
    protected abstract suspend fun insert(collection: Collection): Long

    @Query("UPDATE posts SET deletedAt = NULL, deletionId = NULL, collectionId = :collectionId WHERE id = :id")
    protected abstract suspend fun putBack(id: Long, collectionId: Long?)

    /** A Collection deletion is only remembered while a Recently deleted Post could still be restored from it. */
    @Query(
        "DELETE FROM collection_deletions WHERE id NOT IN (SELECT deletionId FROM posts WHERE deletionId IS NOT NULL)",
    )
    protected abstract suspend fun forgetUnusedDeletions()

    /**
     * Takes the Post out of Recently deleted, back into its Collection. If that Collection was deleted together
     * with the Post, it is recreated with the same name, color and note, unless a Collection has that name by
     * now, which the Post joins instead. A Collection deleted any other way leaves the Post in To sort.
     */
    @Transaction
    open suspend fun restore(id: Long) {
        val post = get(id) ?: return
        val deletion = post.deletionId?.let { getDeletion(it) }
        val collectionId = if (deletion == null) {
            post.collectionId
        } else {
            collectionNamed(deletion.collectionName)
                ?: insert(Collection(0, deletion.collectionName, deletion.collectionColor, deletion.collectionNote))
        }
        putBack(id, collectionId)
        forgetUnusedDeletions()
    }

    /** [restore] for several Posts, as one change. */
    @Transaction
    open suspend fun restore(ids: List<Long>) {
        ids.forEach { restore(it) }
    }

    @Query("SELECT shortcode FROM posts WHERE deletedAt <= :deletedUpTo")
    protected abstract suspend fun shortcodesDeleted(deletedUpTo: Long): List<String>

    @Query(
        "INSERT OR IGNORE INTO deleted_post_traces (shortcode) SELECT shortcode FROM posts WHERE deletedAt <= :deletedUpTo",
    )
    protected abstract suspend fun leaveTraces(deletedUpTo: Long)

    @Query("DELETE FROM posts WHERE deletedAt <= :deletedUpTo")
    protected abstract suspend fun removePosts(deletedUpTo: Long)

    /**
     * Reduces the Posts deleted at [deletedUpTo] or before to their trace: the Post, its notes and its Tags are
     * gone for good. Returns their shortcodes.
     */
    @Transaction
    open suspend fun purge(deletedUpTo: Long): List<String> {
        val shortcodes = shortcodesDeleted(deletedUpTo)
        if (shortcodes.isEmpty()) return shortcodes
        leaveTraces(deletedUpTo)
        removePosts(deletedUpTo)
        forgetUnusedDeletions()
        return shortcodes
    }

    /** Whether a Deleted Post with this shortcode is down to its trace. */
    @Query("SELECT EXISTS(SELECT 1 FROM deleted_post_traces WHERE shortcode = :shortcode)")
    abstract suspend fun hasTrace(shortcode: String): Boolean

    /** Drops the trace: the post may be added again. */
    @Query("DELETE FROM deleted_post_traces WHERE shortcode = :shortcode")
    abstract suspend fun forgetTrace(shortcode: String)
}
