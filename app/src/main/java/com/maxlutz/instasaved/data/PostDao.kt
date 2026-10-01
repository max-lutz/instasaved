package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(post: Post)

    @Query("SELECT * FROM posts WHERE shortcode = :shortcode")
    suspend fun get(shortcode: String): Post?
}
