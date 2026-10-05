package com.maxlutz.instasaved.thumbnails

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.data.Post
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class ThumbnailDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var store: ThumbnailStore
    private lateinit var runs: ThumbnailRuns
    private lateinit var downloader: ThumbnailDownloader
    private var clock = 1_000L

    /** What Instagram has: shortcode to image bytes. Anything else fails. */
    private val onInstagram = mutableMapOf<String, ByteArray>()
    private val fetched = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ThumbnailStore(folder.root)
        runs = ThumbnailRuns(context.getSharedPreferences("thumbnail-runs", Context.MODE_PRIVATE))
        downloader = newDownloader()
    }

    @After
    fun tearDown() = db.close()

    private fun newDownloader() = ThumbnailDownloader(
        db.postDao(),
        store,
        fetch = { shortcode -> onInstagram[shortcode].also { fetched += shortcode } },
        runs = runs,
        now = { clock },
        pause = {},
    )

    private suspend fun add(shortcode: String, addedAt: Long = 0): Long =
        db.postDao().insert(Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = addedAt))

    private fun wait(time: Duration) {
        clock += time.inWholeMilliseconds
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

        wait(59.seconds)
        downloader.downloadMissing()

        assertEquals(listOf("AAA"), fetched)
        assertFalse(store.has("AAA"))
    }

    @Test
    fun aFailedDownloadIsRetriedAMinuteLater() = runTest {
        add("AAA")
        downloader.downloadMissing()
        onInstagram["AAA"] = byteArrayOf(1)

        wait(1.minutes)
        downloader.downloadMissing()

        assertTrue(store.has("AAA"))
    }

    @Test
    fun eachFailureInARowWaitsLongerUpToAWeek() = runTest {
        assertEquals(
            listOf(1.minutes, 5.minutes, 30.minutes, 2.hours, 6.hours, 12.hours, 1.days, 2.days, 4.days, 7.days, 7.days),
            (1..11).map { ThumbnailDownloader.retryDelay(failures = it) },
        )
        assertEquals(7.days, ThumbnailDownloader.retryDelay(failures = 500))

        add("AAA")
        downloader.downloadMissing()
        wait(1.minutes)
        downloader.downloadMissing()
        wait(4.minutes)
        downloader.downloadMissing()

        assertEquals(listOf("AAA", "AAA"), fetched)

        wait(1.minutes)
        downloader.downloadMissing()

        assertEquals(listOf("AAA", "AAA", "AAA"), fetched)
    }

    @Test
    fun aThumbnailLostAfterItWasSavedIsDownloadedAgainWithoutWaiting() = runTest {
        add("AAA")
        downloader.downloadMissing()
        wait(1.minutes)
        onInstagram["AAA"] = byteArrayOf(1)
        downloader.downloadMissing()

        // A restored Backup has the Posts, never their Thumbnails.
        val restored =
            ThumbnailDownloader(db.postDao(), ThumbnailStore(folder.newFolder()), { fetched += it; null }, runs, { clock }, {})
        restored.downloadMissing()

        assertEquals(listOf("AAA", "AAA", "AAA"), fetched)
    }

    @Test
    fun triesNewestPostsFirstAndPostsThatFailedBeforeLast() = runTest {
        add("FAILED", addedAt = 3)
        downloader.downloadMissing()
        wait(1.minutes)
        fetched.clear()
        add("OLD", addedAt = 1)
        add("NEW", addedAt = 2)

        downloader.downloadMissing()

        assertEquals(listOf("NEW", "OLD", "FAILED"), fetched)
    }

    @Test
    fun tellsHowLongUntilTheNextDownloadIsDue() = runTest {
        assertNull(downloader.downloadMissing())

        add("GONE")
        assertEquals(1.minutes, downloader.downloadMissing())
        wait(1.minutes)
        assertEquals(5.minutes, downloader.downloadMissing())

        // The soonest of the missing Thumbnails counts.
        wait(2.minutes)
        add("NEW")
        assertEquals(1.minutes, downloader.downloadMissing())
        wait(20.seconds)
        assertEquals(40.seconds, downloader.downloadMissing())

        onInstagram += listOf("GONE", "NEW").associateWith { byteArrayOf(1) }
        wait(1.hours)
        assertNull(downloader.downloadMissing())
    }

    @Test
    fun stopsAfterFiveFailuresInARowAndLeavesTheRestUntried() = runTest {
        repeat(8) { add("P$it", addedAt = it.toLong()) }

        downloader.downloadMissing()

        assertEquals(listOf("P7", "P6", "P5", "P4", "P3"), fetched)
    }

    @Test
    fun afterARunThatStoppedEarlyTheUntriedPostsWaitAMinute() = runTest {
        repeat(8) { add("P$it", addedAt = it.toLong()) }
        assertEquals(1.minutes, downloader.downloadMissing())
        fetched.clear()
        onInstagram += listOf("P2", "P1", "P0").associateWith { byteArrayOf(1) }

        wait(59.seconds)
        assertEquals(1.seconds, downloader.downloadMissing())

        assertEquals(emptyList<String>(), fetched)

        // The next run starts with them.
        wait(1.seconds)
        downloader.downloadMissing()

        assertEquals(listOf("P2", "P1", "P0"), fetched.take(3))
        assertEquals(setOf("P2", "P1", "P0"), store.shortcodes.value)
    }

    @Test
    fun eachRunInARowThatStopsEarlyHoldsTheNextOneBackLongerUpToSixHours() = runTest {
        val ladder = listOf(1.minutes, 5.minutes, 30.minutes, 2.hours, 6.hours, 6.hours, 6.hours)
        assertEquals(ladder, (1..7).map { ThumbnailDownloader.runDelay(stops = it) })

        // Instagram is throttling: nothing downloads, and there is always something untried.
        repeat(40) { add("P$it", addedAt = it.toLong()) }

        val waits = ladder.map {
            val wait = downloader.downloadMissing()!!
            // A minute later the Posts that failed are due again, but the run is not: it tries nothing.
            if (wait > 1.minutes) {
                wait(1.minutes)
                downloader.downloadMissing()
                wait(wait - 1.minutes)
            } else {
                wait(wait)
            }
            wait
        }

        assertEquals(ladder, waits)
        assertEquals(ladder.size * 5, fetched.size)
    }

    @Test
    fun theFirstDownloadThatWorksResetsTheWaitBetweenRuns() = runTest {
        repeat(30) { add("P$it", addedAt = it.toLong()) }
        assertEquals(1.minutes, downloader.downloadMissing())
        wait(1.minutes)
        assertEquals(5.minutes, downloader.downloadMissing())
        wait(5.minutes)
        fetched.clear()

        // One works, then Instagram throttles again.
        onInstagram["P19"] = byteArrayOf(1)
        val wait = downloader.downloadMissing()

        assertEquals(listOf("P19", "P18", "P17", "P16", "P15", "P14"), fetched)
        assertEquals(1.minutes, wait)
    }

    @Test
    fun theWaitBetweenRunsSurvivesARestartOfTheApp() = runTest {
        repeat(20) { add("P$it", addedAt = it.toLong()) }
        downloader.downloadMissing()
        wait(1.minutes)
        downloader.downloadMissing()
        fetched.clear()

        val restarted = newDownloader()
        wait(4.minutes)
        assertEquals(1.minutes, restarted.downloadMissing())

        assertEquals(emptyList<String>(), fetched)

        wait(1.minutes)
        assertEquals(30.minutes, restarted.downloadMissing())
    }

    @Test
    fun aSuccessBetweenFailuresKeepsTheRunGoing() = runTest {
        repeat(8) { add("P$it", addedAt = it.toLong()) }
        onInstagram["P4"] = byteArrayOf(1)

        downloader.downloadMissing()

        assertEquals(8, fetched.size)
    }
}
