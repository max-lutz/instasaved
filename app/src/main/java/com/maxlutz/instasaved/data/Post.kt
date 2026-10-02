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
 */
@Entity(tableName = "posts", indices = [Index(value = ["shortcode"], unique = true)])
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shortcode: String,
    val url: String,
    val addedAt: Long,
    val seenInExport: Boolean = false,
)
