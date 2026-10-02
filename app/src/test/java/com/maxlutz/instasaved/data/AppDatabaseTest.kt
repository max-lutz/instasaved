package com.maxlutz.instasaved.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun storesAndReadsAPostByShortcode() = runTest {
        val id = db.postDao().insert(Post(shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L))

        assertEquals(
            Post(id = id, shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L),
            db.postDao().get("ABC123"),
        )
        assertNull(db.postDao().get("XYZ789"))
    }

    @Test
    fun shortcodeIsUnique() = runTest {
        db.postDao().insert(Post(shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L))

        val id = db.postDao().insert(Post(shortcode = "ABC123", url = "https://www.instagram.com/reel/ABC123/", addedAt = 2L))

        assertEquals(-1L, id)
        assertEquals("https://www.instagram.com/p/ABC123/", db.postDao().get("ABC123")?.url)
    }
}
