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
        shareIn = ShareIn(db.postDao(), now = { clock })
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
