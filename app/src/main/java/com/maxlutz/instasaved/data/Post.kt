package com.maxlutz.instasaved.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved Instagram post or reel. Its identity is the [shortcode] (unique), behind a surrogate [id] (ADR-0007).
 *
 * @property url the link the Post was first added from, kept as-is for opening it.
 * @property addedAt when the Post was saved, in epoch milliseconds: on Instagram for a Post that Sync added, as the
 *   Export gives it, else when the Post entered the app.
 * @property modifiedAt when the user last changed the Post's text, Collection or Tags, in epoch milliseconds;
 *   [addedAt] until they do.
 * @property seenInExport whether any Export has contained this Post yet. Only Sync sets it (sync-spec R3a).
 * @property titleHandEdited once set, [title] no longer follows [description] (ADR-0005).
 * @property descriptionHandEdited once set, Sync no longer overwrites [description] with the caption (ADR-0005).
 * @property deletedAt when the user deleted the Post, in epoch milliseconds; set means it is in Recently deleted
 *   (ADR-0012).
 * @property collectionId the Collection the Post is in; null means To sort. A deleted Collection leaves it null.
 * @property deletionId set when the Post was deleted together with its Collection: the [CollectionDeletion] to
 *   recreate that Collection from on restore (ADR-0012).
 * @property ownerUsername the Instagram account that posted it, as the Export gives it; empty until Sync fills it.
 * @property ownerName that account's display name; empty until Sync fills it.
 * @property thumbnailFailures how many times in a row downloading the Thumbnail failed; 0 once it is saved.
 * @property thumbnailFailedAt when it last failed, in epoch milliseconds: the retry waits from then (ADR-0008).
 * @property instagramCollections the Instagram Collections the last Export that listed the Post put it in, for
 *   display only: Sync never moves a Post by them (sync-spec R1).
 */
@Entity(
    tableName = "posts",
    indices = [Index(value = ["shortcode"], unique = true), Index("collectionId"), Index("deletionId")],
    foreignKeys = [
        // SET NULL is the safe default if a Collection goes away outside the delete prompt (ADR-0010).
        ForeignKey(Collection::class, ["id"], ["collectionId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(CollectionDeletion::class, ["id"], ["deletionId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shortcode: String,
    val url: String,
    val addedAt: Long,
    val modifiedAt: Long = addedAt,
    val seenInExport: Boolean = false,
    val title: String = "",
    val titleHandEdited: Boolean = false,
    val description: String = "",
    val descriptionHandEdited: Boolean = false,
    val postNote: String = "",
    val deletedAt: Long? = null,
    val collectionId: Long? = null,
    val deletionId: Long? = null,
    val ownerUsername: String = "",
    val ownerName: String = "",
    val thumbnailFailures: Int = 0,
    val thumbnailFailedAt: Long? = null,
    val instagramCollections: List<String> = emptyList(),
)
