package com.maxlutz.instasaved.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        db.postDao().updateText(edited, at = 1L)

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

        db.postDao().updateText(loaded.editPostNote("late keystroke"), at = 1L)

        assertEquals(9L, db.postDao().get("ABC123")?.deletedAt)
    }

    @Test
    fun aPostIsModifiedWhenAddedThenWhenItsTextOrCollectionChanges() = runTest {
        val id = db.postDao().insert(Post(shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L))
        val added = db.postDao().get("ABC123")!!

        db.postDao().updateText(added.editPostNote("note"), at = 5L)
        val edited = db.postDao().get("ABC123")!!
        db.postDao().setCollection(id, db.collectionDao().create("Recipes", PALETTE[0]), at = 8L)
        val moved = db.postDao().get("ABC123")!!

        assertEquals(listOf(1L, 5L, 8L), listOf(added, edited, moved).map { it.modifiedAt })
        assertEquals(1L, moved.addedAt)
    }

    @Test
    fun allPostsAreListedNewestFirstWithoutTheRecentlyDeletedOnes() = runTest {
        val ids = listOf("A" to 1L, "B" to 3L, "C" to 2L, "D" to 4L).map { (shortcode, addedAt) ->
            db.postDao().insert(Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = addedAt))
        }
        db.postDao().setCollection(ids[0], db.collectionDao().create("Recipes", PALETTE[0]), at = 9L)
        db.postDao().delete(ids[3], at = 9L)

        assertEquals(listOf("B", "C", "A"), db.postDao().observeAll().first().map { it.shortcode })
    }

    @Test
    fun deletedPostsLeaveToSortAndComeBackOnRestore() = runTest {
        val id = db.postDao().insert(Post(shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L))

        db.postDao().delete(id, at = 9L)
        assertEquals(emptyList<Post>(), db.postDao().observeToSort().first())

        db.recentlyDeletedDao().restore(id)
        assertEquals(listOf("ABC123"), db.postDao().observeToSort().first().map { it.shortcode })
    }

    private suspend fun insertNew(shortcode: String) = db.postDao().insert(
        Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = 1L, isNew = true),
    )

    @Test
    fun openingAPostClearsItsNewMarkerOnly() = runTest {
        val a = insertNew("A")
        insertNew("B")

        db.postDao().markSeen(a)

        val post = checkNotNull(db.postDao().get("A"))
        assertFalse(post.isNew)
        // Not a change by the user to the Post itself.
        assertEquals(post.addedAt, post.modifiedAt)
        assertTrue(checkNotNull(db.postDao().get("B")).isNew)
    }

    @Test
    fun markAllAsSeenLeavesNoPostNew() = runTest {
        insertNew("A")
        val b = insertNew("B")
        db.postDao().delete(b, at = 9L)

        db.postDao().markAllSeen()

        assertFalse(checkNotNull(db.postDao().get("A")).isNew)
        // Restored from Recently deleted, it would not be New either.
        assertFalse(checkNotNull(db.postDao().get("B")).isNew)
    }

    @Test
    fun editingTheTextOfANewPostLeavesItsMarker() = runTest {
        insertNew("A")
        val post = checkNotNull(db.postDao().get("A"))

        db.postDao().updateText(post.copy(postNote = "Try it", isNew = false), at = 9L)

        assertTrue(checkNotNull(db.postDao().get("A")).isNew)
    }
}
