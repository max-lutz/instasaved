package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CollectionDao {
    /** All Collections, alphabetically (so emoji prefixes group them), each with its Post count. */
    @Query(
        "SELECT collections.*, COUNT(posts.id) AS postCount FROM collections " +
            "LEFT JOIN posts ON posts.collectionId = collections.id AND posts.deletedAt IS NULL " +
            "GROUP BY collections.id ORDER BY collections.name, collections.id",
    )
    abstract fun observeAll(): Flow<List<CollectionWithCount>>

    @Query("SELECT * FROM collections WHERE id = :id")
    abstract fun observe(id: Long): Flow<Collection?>

    @Query("SELECT * FROM collections WHERE id = :id")
    abstract suspend fun get(id: Long): Collection?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(collection: Collection): Long

    /** Creates a Collection. Returns its id, or null if [name] is blank or another Collection already has it. */
    open suspend fun create(name: String, color: Int, note: String = ""): Long? {
        if (name.isBlank()) return null
        return insert(Collection(name = name.trim(), color = color, note = note)).takeIf { it != -1L }
    }

    @Query("UPDATE OR IGNORE collections SET name = :name, color = :color, note = :note WHERE id = :id")
    protected abstract suspend fun updateRow(id: Long, name: String, color: Int, note: String): Int

    /**
     * Saves a renamed, recolored or re-noted Collection. Returns false, changing nothing, if its name is blank or
     * another Collection already has it.
     */
    open suspend fun update(collection: Collection): Boolean =
        collection.name.isNotBlank() &&
            updateRow(collection.id, collection.name.trim(), collection.color, collection.note) == 1

    /** Deletes the Collection; its Posts go to To sort through the `ON DELETE SET NULL` reference (ADR-0010). */
    @Query("DELETE FROM collections WHERE id = :id")
    abstract suspend fun deleteKeepingPosts(id: Long)

    @Insert
    protected abstract suspend fun insert(deletion: CollectionDeletion): Long

    @Query("UPDATE posts SET deletedAt = :at, deletionId = :deletionId WHERE collectionId = :id AND deletedAt IS NULL")
    protected abstract suspend fun deletePosts(id: Long, at: Long, deletionId: Long)

    /**
     * Deletes the Collection and moves its Posts to Recently deleted as one deletion action, recorded so that
     * restoring them can recreate the Collection (ADR-0010, ADR-0012).
     */
    @Transaction
    open suspend fun deleteWithPosts(id: Long, at: Long) {
        val collection = get(id) ?: return
        val deletionId = insert(
            CollectionDeletion(
                collectionName = collection.name,
                collectionColor = collection.color,
                collectionNote = collection.note,
            ),
        )
        deletePosts(id, at, deletionId)
        deleteKeepingPosts(id)
    }

    @Query("SELECT * FROM collection_deletions WHERE id = :id")
    abstract suspend fun getDeletion(id: Long): CollectionDeletion?
}
