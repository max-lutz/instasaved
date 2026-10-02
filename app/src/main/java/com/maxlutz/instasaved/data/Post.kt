package com.maxlutz.instasaved.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved Instagram post or reel. Its identity is the [shortcode] (unique), behind a surrogate [id] (ADR-0007).
 *
 * @property url the link the Post was first added from, kept as-is for opening it.
 * @property addedAt when the Post entered the app, in epoch milliseconds.
 * @property seenInExport whether any Export has contained this Post yet. Only Sync sets it (sync-spec R3a).
 * @property titleHandEdited once set, [title] no longer follows [description] (ADR-0005).
 * @property descriptionHandEdited once set, Sync no longer overwrites [description] with the caption (ADR-0005).
 * @property deletedAt when the user deleted the Post, in epoch milliseconds; set means it is in Recently deleted
 *   (ADR-0012).
 */
@Entity(tableName = "posts", indices = [Index(value = ["shortcode"], unique = true)])
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shortcode: String,
    val url: String,
    val addedAt: Long,
    val seenInExport: Boolean = false,
    val title: String = "",
    val titleHandEdited: Boolean = false,
    val description: String = "",
    val descriptionHandEdited: Boolean = false,
    val postNote: String = "",
    val deletedAt: Long? = null,
)
