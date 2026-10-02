package com.maxlutz.instasaved.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    /** Returns the new row id, or -1 if a Post with the same shortcode already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(post: Post): Long

    @Query("SELECT * FROM posts WHERE shortcode = :shortcode")
    suspend fun get(shortcode: String): Post?

    /** Posts in To sort, newest first. Every Post is in To sort until Collections exist. */
    @Query("SELECT * FROM posts ORDER BY addedAt DESC, id DESC")
    fun observeToSort(): Flow<List<Post>>
}
