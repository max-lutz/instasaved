package com.maxlutz.instasaved.share

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.share.ShareIn.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareInTest {
    private lateinit var db: AppDatabase
    private var clock = 1_000L
    private lateinit var shareIn: ShareIn

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        shareIn = ShareIn(db.postDao(), db.recentlyDeletedDao(), now = { clock })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun toSort() = db.postDao().observeToSort().first()

    @Test
    fun sharedPostLandsInToSortWithItsOriginalUrl() = runTest {
        val url = "https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=abc"

        val result = shareIn.receive(url)

        assertTrue(result is Result.Added)
        val post = toSort().single()
        assertEquals("C1a2B3c4D5e", post.shortcode)
        assertEquals(url, post.url)
        assertEquals(1_000L, post.addedAt)
    }

    @Test
    fun shareInNeverMarksAPostSeenInAnExport() = runTest {
        shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")

        assertFalse(toSort().single().seenInExport)
    }

    @Test
    fun sharingTheSamePostTwiceDoesNotDuplicate() = runTest {
        shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")

        val again = shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")

        assertTrue(again is Result.AlreadySaved)
        assertEquals(1, toSort().size)
    }

    @Test
    fun sharingTheSamePostThroughAnotherLinkShapeDoesNotDuplicateNorChangeIt() = runTest {
        shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")
        val first = toSort().single()
        clock = 2_000L

        val again = shareIn.receive("https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=xyz")

        assertEquals(Result.AlreadySaved(first), again)
        assertEquals(listOf(first), toSort())
    }

    @Test
    fun sharingADeletedPostReportsItWithoutBringingItBack() = runTest {
        shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")
        db.postDao().delete(toSort().single().id, at = 5L)

        val again = shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")

        assertTrue(again is Result.PreviouslyDeleted)
        assertEquals(emptyList<Post>(), toSort())
    }

    @Test
    fun addingBackAPostStillInRecentlyDeletedRestoresItWhole() = runTest {
        val url = "https://www.instagram.com/p/C1a2B3c4D5e/"
        shareIn.receive(url)
        val recipes = checkNotNull(db.collectionDao().create("Recipes", 0))
        val saved = toSort().single().copy(postNote = "Try on Sunday", collectionId = recipes)
        db.postDao().updateText(saved.id, "", false, "", false, saved.postNote)
        db.postDao().setCollection(saved.id, recipes)
        db.postDao().delete(saved.id, at = 5L)
        clock = 2_000L

        val asked = shareIn.receive("https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=xyz") as Result.PreviouslyDeleted
        val back = shareIn.addBack(asked.link)

        assertEquals(Result.Restored(saved), back)
        assertEquals(listOf(saved), db.postDao().observeInCollection(recipes).first())
    }

    @Test
    fun sharingAPostDownToItsTraceAsksAndAddsNothing() = runTest {
        shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")
        db.postDao().delete(toSort().single().id, at = 5L)
        db.recentlyDeletedDao().purge(deletedUpTo = 5L)

        val again = shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")

        assertTrue(again is Result.PreviouslyDeleted)
        assertEquals(emptyList<Post>(), toSort())
        assertTrue(db.recentlyDeletedDao().hasTrace("C1a2B3c4D5e"))
    }

    @Test
    fun addingBackAPostDownToItsTraceAddsAFreshPostToToSort() = runTest {
        shareIn.receive("https://www.instagram.com/p/C1a2B3c4D5e/")
        val first = toSort().single()
        db.postDao().updateText(first.id, "", false, "", false, "Try on Sunday")
        db.postDao().delete(first.id, at = 5L)
        db.recentlyDeletedDao().purge(deletedUpTo = 5L)
        clock = 2_000L
        val url = "https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=xyz"

        val back = shareIn.addBack((shareIn.receive(url) as Result.PreviouslyDeleted).link)

        val fresh = toSort().single()
        assertEquals(Result.Added(fresh), back)
        assertEquals(Post(id = fresh.id, shortcode = "C1a2B3c4D5e", url = url, addedAt = 2_000L), fresh)
        assertFalse(db.recentlyDeletedDao().hasTrace("C1a2B3c4D5e"))
        // No longer a Deleted Post: the next share finds it saved.
        assertEquals(Result.AlreadySaved(fresh), shareIn.receive(url))
    }

    @Test
    fun ignoresTextWithoutAPostLink() = runTest {
        assertEquals(Result.NotAPostLink, shareIn.receive("https://www.instagram.com/some.user/"))
        assertEquals(emptyList<Post>(), toSort())
    }

    @Test
    fun toSortListsNewestFirst() = runTest {
        shareIn.receive("https://www.instagram.com/p/AAA/")
        clock = 2_000L
        shareIn.receive("https://www.instagram.com/p/BBB/")

        assertEquals(listOf("BBB", "AAA"), toSort().map { it.shortcode })
    }
}
