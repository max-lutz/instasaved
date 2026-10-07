package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * A copy of the app's own data: every Post, Recently deleted ones included with their deletion dates, the
 * Collections with their Sections, the Tags, and the traces of Deleted Posts (ADR-0012). Never the Thumbnails
 * (ADR-0001).
 */
data class Backup(
    val posts: List<Post> = emptyList(),
    val collections: List<Collection> = emptyList(),
    val collectionDeletions: List<CollectionDeletion> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val postTags: List<PostTag> = emptyList(),
    val deletedPostTraces: List<DeletedPostTrace> = emptyList(),
    val sections: List<Section> = emptyList(),
)

@Dao
abstract class BackupDao {
    @Query("SELECT * FROM posts ORDER BY id")
    protected abstract suspend fun posts(): List<Post>

    @Query("SELECT * FROM collections ORDER BY id")
    protected abstract suspend fun collections(): List<Collection>

    @Query("SELECT * FROM collection_deletions ORDER BY id")
    protected abstract suspend fun collectionDeletions(): List<CollectionDeletion>

    @Query("SELECT * FROM tags ORDER BY id")
    protected abstract suspend fun tags(): List<Tag>

    @Query("SELECT * FROM post_tags ORDER BY postId, tagId")
    protected abstract suspend fun postTags(): List<PostTag>

    @Query("SELECT * FROM deleted_post_traces ORDER BY shortcode")
    protected abstract suspend fun deletedPostTraces(): List<DeletedPostTrace>

    @Query("SELECT * FROM sections ORDER BY id")
    protected abstract suspend fun sections(): List<Section>

    /** Everything the app holds, as of one moment. */
    @Transaction
    open suspend fun read(): Backup =
        Backup(posts(), collections(), collectionDeletions(), tags(), postTags(), deletedPostTraces(), sections())

    @Query("DELETE FROM post_tags")
    protected abstract suspend fun clearPostTags()

    @Query("DELETE FROM posts")
    protected abstract suspend fun clearPosts()

    @Query("DELETE FROM tags")
    protected abstract suspend fun clearTags()

    @Query("DELETE FROM collections")
    protected abstract suspend fun clearCollections()

    @Query("DELETE FROM sections")
    protected abstract suspend fun clearSections()

    @Query("DELETE FROM collection_deletions")
    protected abstract suspend fun clearCollectionDeletions()

    @Query("DELETE FROM deleted_post_traces")
    protected abstract suspend fun clearDeletedPostTraces()

    @Query("DELETE FROM applied_exports")
    protected abstract suspend fun clearAppliedExports()

    @Insert
    protected abstract suspend fun insertPosts(posts: List<Post>)

    @Insert
    protected abstract suspend fun insertSections(sections: List<Section>)

    @Insert
    protected abstract suspend fun insertCollections(collections: List<Collection>)

    @Insert
    protected abstract suspend fun insertCollectionDeletions(deletions: List<CollectionDeletion>)

    @Insert
    protected abstract suspend fun insertTags(tags: List<Tag>)

    @Insert
    protected abstract suspend fun insertPostTags(postTags: List<PostTag>)

    @Insert
    protected abstract suspend fun insertDeletedPostTraces(traces: List<DeletedPostTrace>)

    /**
     * Wipe-and-replace: everything the app holds goes, and [backup] takes its place, ids included. The record of the
     * Exports applied goes too, so that the next Sync applies every Export again: those applied after the Backup was
     * taken bring back the Posts they added, and the rules leave the others as they are. All or nothing:
     * a Backup that does not hold together (a Post in a Collection it does not have, two Tags of one name) throws
     * and leaves the app's data as it was.
     */
    @Transaction
    open suspend fun replaceAll(backup: Backup) {
        clearPostTags()
        clearPosts()
        clearTags()
        clearCollections()
        clearSections()
        clearCollectionDeletions()
        clearDeletedPostTraces()
        clearAppliedExports()
        insertSections(backup.sections)
        insertCollections(backup.collections)
        insertCollectionDeletions(backup.collectionDeletions)
        insertTags(backup.tags)
        insertPosts(backup.posts)
        insertPostTags(backup.postTags)
        insertDeletedPostTraces(backup.deletedPostTraces)
    }
}
