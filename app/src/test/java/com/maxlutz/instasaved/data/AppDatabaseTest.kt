package com.maxlutz.instasaved.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun handEditedFlagsSurviveReadingBack() = runTest {
        val id = db.postDao().insert(Post(shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L))
        val edited = db.postDao().get("ABC123")!!.editDescription("Hello. World").editTitle("Mine").editPostNote("note")

        db.postDao().updateText(edited)

        val stored = db.postDao().get("ABC123")!!
        assertEquals(edited, stored)
        assertTrue(stored.titleHandEdited)
        assertTrue(stored.descriptionHandEdited)
        assertEquals(id, stored.id)
    }

    @Test
    fun savingTextDoesNotUndoADeletion() = runTest {
        val id = db.postDao().insert(Post(shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L))
        val loaded = db.postDao().get("ABC123")!!
        db.postDao().delete(id, at = 9L)

        db.postDao().updateText(loaded.editPostNote("late keystroke"))

        assertEquals(9L, db.postDao().get("ABC123")?.deletedAt)
    }

    @Test
    fun deletedPostsLeaveToSortAndComeBackOnRestore() = runTest {
        val id = db.postDao().insert(Post(shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L))

        db.postDao().delete(id, at = 9L)
        assertEquals(emptyList<Post>(), db.postDao().observeToSort().first())

        db.postDao().restore(id)
        assertEquals(listOf("ABC123"), db.postDao().observeToSort().first().map { it.shortcode })
    }
}
