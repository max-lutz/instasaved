package com.maxlutz.instasaved.thumbnails

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.data.Post
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
class ThumbnailDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var store: ThumbnailStore
    private lateinit var downloader: ThumbnailDownloader
    private var clock = 1_000L

    /** What Instagram has: shortcode to image bytes. Anything else fails. */
    private val onInstagram = mutableMapOf<String, ByteArray>()
    private val fetched = mutableListOf<String>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ThumbnailStore(folder.root)
        downloader = ThumbnailDownloader(
            db.postDao(),
            store,
            fetch = { shortcode -> onInstagram[shortcode].also { fetched += shortcode } },
            now = { clock },
            pause = {},
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(shortcode: String, addedAt: Long = 0): Long =
        db.postDao().insert(Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = addedAt))

    private fun wait(hours: Int) {
        clock += hours.hours.inWholeMilliseconds
    }

    @Test
    fun downloadsTheThumbnailOfAPostWithoutOne() = runTest {
        add("AAA")
        onInstagram["AAA"] = byteArrayOf(1, 2, 3)

        downloader.downloadMissing()

        assertArrayEquals(byteArrayOf(1, 2, 3), store.file("AAA").readBytes())
    }

    @Test
    fun leavesAPostThatAlreadyHasItsThumbnailAlone() = runTest {
        add("AAA")
        store.save("AAA", byteArrayOf(1))

        downloader.downloadMissing()

        assertEquals(emptyList<String>(), fetched)
    }

    @Test
    fun skipsDeletedPosts() = runTest {
        db.postDao().delete(add("AAA"), at = 5)

        downloader.downloadMissing()

        assertEquals(emptyList<String>(), fetched)
    }

    @Test
    fun aFailedDownloadIsNotRetriedRightAway() = runTest {
        add("AAA")
        downloader.downloadMissing()
        onInstagram["AAA"] = byteArrayOf(1)

        wait(hours = 5)
        downloader.downloadMissing()

        assertEquals(listOf("AAA"), fetched)
        assertFalse(store.has("AAA"))
    }

    @Test
    fun aFailedDownloadIsRetriedSixHoursLater() = runTest {
        add("AAA")
        downloader.downloadMissing()
        onInstagram["AAA"] = byteArrayOf(1)

        wait(hours = 6)
        downloader.downloadMissing()

        assertTrue(store.has("AAA"))
    }

    @Test
    fun eachFailureInARowDoublesTheWaitUpToAWeek() = runTest {
        assertEquals(6.hours, ThumbnailDownloader.retryDelay(failures = 1))
        assertEquals(12.hours, ThumbnailDownloader.retryDelay(failures = 2))
        assertEquals(4.days, ThumbnailDownloader.retryDelay(failures = 5))
        assertEquals(7.days, ThumbnailDownloader.retryDelay(failures = 6))
        assertEquals(7.days, ThumbnailDownloader.retryDelay(failures = 500))

        add("AAA")
        downloader.downloadMissing()
        wait(hours = 6)
        downloader.downloadMissing()
        wait(hours = 6)
        downloader.downloadMissing()

        assertEquals(listOf("AAA", "AAA"), fetched)
    }

    @Test
    fun aThumbnailLostAfterItWasSavedIsDownloadedAgainWithoutWaiting() = runTest {
        add("AAA")
        downloader.downloadMissing()
        wait(hours = 6)
        onInstagram["AAA"] = byteArrayOf(1)
        downloader.downloadMissing()

        // A restored Backup has the Posts, never their Thumbnails.
        val restored = ThumbnailDownloader(db.postDao(), ThumbnailStore(folder.newFolder()), { fetched += it; null }, { clock }, {})
        restored.downloadMissing()

        assertEquals(listOf("AAA", "AAA", "AAA"), fetched)
    }

    @Test
    fun triesNewestPostsFirstAndPostsThatFailedBeforeLast() = runTest {
        add("FAILED", addedAt = 3)
        downloader.downloadMissing()
        wait(hours = 6)
        fetched.clear()
        add("OLD", addedAt = 1)
        add("NEW", addedAt = 2)

        downloader.downloadMissing()

        assertEquals(listOf("NEW", "OLD", "FAILED"), fetched)
    }

    @Test
    fun stopsAfterFiveFailuresInARowAndLeavesTheRestUntried() = runTest {
        repeat(8) { add("P$it", addedAt = it.toLong()) }

        downloader.downloadMissing()

        assertEquals(listOf("P7", "P6", "P5", "P4", "P3"), fetched)

        // The untried ones are still due: the next run starts with them, without waiting.
        onInstagram += listOf("P2", "P1", "P0").associateWith { byteArrayOf(1) }
        downloader.downloadMissing()

        assertEquals(setOf("P2", "P1", "P0"), store.shortcodes.value)
    }

    @Test
    fun aSuccessBetweenFailuresKeepsTheRunGoing() = runTest {
        repeat(8) { add("P$it", addedAt = it.toLong()) }
        onInstagram["P4"] = byteArrayOf(1)

        downloader.downloadMissing()

        assertEquals(8, fetched.size)
    }
}
