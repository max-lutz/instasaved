package com.maxlutz.instasaved.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved Instagram post or reel, identified by its shortcode (ADR-0007). */
@Entity(tableName = "posts")
data class Post(
    @PrimaryKey val shortcode: String,
)
