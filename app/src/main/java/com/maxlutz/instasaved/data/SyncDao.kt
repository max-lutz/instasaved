package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.maxlutz.instasaved.sync.AppState
import com.maxlutz.instasaved.sync.ExportedPost
import com.maxlutz.instasaved.sync.SyncCollection
import com.maxlutz.instasaved.sync.SyncPost
import com.maxlutz.instasaved.sync.SyncResult
import com.maxlutz.instasaved.sync.planSync

/**
 * An Export that Sync has applied, so that it is never applied again (sync-spec R7).
 *
 * @property driveFileId the Export folder's id in Google Drive: its identity, unlike its name.
 * @property exportDate the day of the Export, as its folder name gives it, ISO (`2026-10-01`).
 * @property appliedAt when Sync applied it, in epoch milliseconds.
 */
@Entity(tableName = "applied_exports")
data class AppliedExport(
    @PrimaryKey val driveFileId: String,
    val name: String,
    val exportDate: String,
    val appliedAt: Long,
)

/** Sync's side of the database: what the sync rules read, and their changes applied with the Export's record. */
@Dao
abstract class SyncDao {
    @Query("SELECT driveFileId FROM applied_exports")
    abstract suspend fun appliedExportIds(): List<String>

    /** The day of the newest Export applied, ISO, or null if none was. */
    @Query("SELECT MAX(exportDate) FROM applied_exports")
    abstract suspend fun newestExportDate(): String?

    @Query("SELECT * FROM posts WHERE deletedAt IS NULL")
    protected abstract suspend fun posts(): List<Post>

    @Query("SELECT * FROM collections")
    protected abstract suspend fun collections(): List<Collection>

    @Query("SELECT shortcode FROM posts WHERE deletedAt IS NOT NULL UNION SELECT shortcode FROM deleted_post_traces")
    protected abstract suspend fun deletedShortcodes(): List<String>

    @Insert
    protected abstract suspend fun insert(collection: Collection): Long

    @Insert
    protected abstract suspend fun insert(post: Post): Long

    @Insert
    protected abstract suspend fun insert(export: AppliedExport)

    // Not the modified date: that one tells when the user last changed the Post.
    @Query(
        "UPDATE posts SET collectionId = :collectionId, title = :title, description = :description, " +
            "ownerUsername = :ownerUsername, ownerName = :ownerName, seenInExport = :seenInExport, " +
            "instagramCollections = :instagramCollections WHERE shortcode = :shortcode",
    )
    protected abstract suspend fun update(
        shortcode: String,
        collectionId: Long?,
        title: String,
        description: String,
        ownerUsername: String,
        ownerName: String,
        seenInExport: Boolean,
        instagramCollections: List<String>,
    )

    /** What the app holds, as the sync rules see it. A Deleted Post counts whichever of its two stages it is in. */
    @Transaction
    open suspend fun appState(): AppState {
        val collections = collections()
        val names = collections.associate { it.id to it.name }
        return AppState(
            posts = posts().map { post ->
                SyncPost(
                    shortcode = post.shortcode,
                    collection = post.collectionId?.let(names::get),
                    title = post.title,
                    titleHandEdited = post.titleHandEdited,
                    description = post.description,
                    descriptionHandEdited = post.descriptionHandEdited,
                    ownerUsername = post.ownerUsername,
                    ownerName = post.ownerName,
                    seenInExport = post.seenInExport,
                    instagramCollections = post.instagramCollections,
                )
            },
            collections = collections.map { SyncCollection(it.name, it.color) },
            deletedShortcodes = deletedShortcodes().toSet(),
        )
    }

    /**
     * Applies the Export's [posts] to the app by the sync rules, and records the Export as applied, all or nothing.
     * The app is read inside the same transaction, so a change made meanwhile is not overwritten.
     */
    @Transaction
    open suspend fun apply(export: AppliedExport, posts: List<ExportedPost>): SyncResult {
        val result = planSync(appState(), posts)
        val changes = result.changes
        // Collection names are unique ignoring case, and so is this map's key.
        val collectionIds = collections().associateTo(HashMap()) { it.name.lowercase() to it.id }
        for (created in changes.createdCollections) {
            collectionIds[created.name.lowercase()] = insert(Collection(name = created.name, color = created.color))
        }
        fun idOf(collection: String?) = collection?.let { collectionIds.getValue(it.lowercase()) }
        for (added in changes.addedPosts) {
            val post = added.post
            insert(
                Post(
                    shortcode = post.shortcode,
                    url = added.url,
                    addedAt = added.savedAt,
                    seenInExport = post.seenInExport,
                    title = post.title,
                    description = post.description,
                    collectionId = idOf(post.collection),
                    ownerUsername = post.ownerUsername,
                    ownerName = post.ownerName,
                    instagramCollections = post.instagramCollections,
                ),
            )
        }
        for (post in changes.updatedPosts) {
            update(
                shortcode = post.shortcode,
                collectionId = idOf(post.collection),
                title = post.title,
                description = post.description,
                ownerUsername = post.ownerUsername,
                ownerName = post.ownerName,
                seenInExport = post.seenInExport,
                instagramCollections = post.instagramCollections,
            )
        }
        insert(export)
        return result
    }
}
