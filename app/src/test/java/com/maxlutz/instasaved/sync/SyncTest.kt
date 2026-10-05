package com.maxlutz.instasaved.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.data.Post
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
import java.io.IOException
import java.time.LocalDate

/** The Sync pipeline (sync-spec "Pipeline", R7) between a fake Drive and the real database. */
@RunWith(RobolectricTestRunner::class)
class SyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var status: SyncStatusStore
    private lateinit var sync: Sync
    private val drive = FakeDrive()
    private var thumbnailQueues = 0
    private var now = 1_000L
    private val posts get() = db.postDao()
    private val collections get() = db.collectionDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        status = SyncStatusStore(context.getSharedPreferences("sync-test", Context.MODE_PRIVATE))
        sync = Sync(db.syncDao(), status, queueThumbnails = { thumbnailQueues++ }, now = { now })
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("sync-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** A Drive holding [exports], each read from its files by path; a missing path is no such file. */
    private class FakeDrive : Drive {
        val exports = mutableListOf<DriveExport>()
        val files = mutableMapOf<String, Map<String, String>>()
        val failing = mutableMapOf<String, IOException>()
        var listing: IOException? = null
        val reads = mutableListOf<String>()

        fun add(day: String, savedPosts: String, savedCollections: String? = null, createdTime: String = "${day}T03:00:00Z") =
            DriveExport("id-$day-$createdTime", "instagram-someone-$day-Xy12AbCd", LocalDate.parse(day), createdTime)
                .also { export ->
                    exports += export
                    files[export.id] = listOfNotNull(
                        SAVED_POSTS_PATH to savedPosts,
                        savedCollections?.let { SAVED_COLLECTIONS_PATH to it },
                    ).toMap()
                }

        override suspend fun exports(): List<DriveExport> {
            listing?.let { throw it }
            return exports.toList()
        }

        override suspend fun read(export: DriveExport, path: String): String? {
            reads += export.id
            failing[export.id]?.let { throw it }
            return files.getValue(export.id)[path]
        }
    }

    private fun entry(shortcode: String, caption: String = "Caption $shortcode", savedAtSeconds: Long = 100) = """
        {"timestamp": $savedAtSeconds, "media": [], "fbid": "1", "label_values": [
          {"label": "URL", "value": "https://www.instagram.com/p/$shortcode/", "href": ""},
          {"label": "Caption", "value": "$caption"},
          {"title": "Owner", "dict": [{"title": "", "dict": [
            {"label": "Name", "value": "Owner $shortcode"}, {"label": "Username", "value": "owner.$shortcode"}]}]}
        ]}
    """

    private fun savedPosts(vararg entries: String) = entries.joinToString(",", "[", "]")

    /** A saved_collections.json with each Instagram Collection and the shortcodes of its posts. */
    private fun savedCollections(vararg collections: Pair<String, List<String>>) =
        collections.joinToString(",", "[", "]") { (name, shortcodes) ->
            """{"title": "", "media": [], "label_values": [{"label": "Name", "value": "$name"},
                {"title": "Media", "dict": [${shortcodes.joinToString(",") {
                """{"title": "", "dict": [{"label": "URL", "value": "https://www.instagram.com/p/$it/"}]}"""
            }}]}]}"""
        }

    private suspend fun post(shortcode: String): Post = checkNotNull(posts.get(shortcode))

    private suspend fun collectionNames() = collections.observeAll().first().map { it.collection.name }

    private suspend fun appliedIds() = db.syncDao().appliedExportIds().toSet()

    @Test
    fun appliesEveryExportNotAppliedYetOldestFirst() = runTest {
        drive.add("2026-10-03", savedPosts(entry("A", caption = "third")))
        drive.add("2026-10-01", savedPosts(entry("A", caption = "first"), entry("B")))
        drive.add("2026-10-02", savedPosts(entry("A", caption = "second"), entry("C")))

        val report = sync.run(drive)

        assertEquals("third", post("A").description)
        assertEquals(setOf("A", "B", "C"), posts.observeAll().first().map { it.shortcode }.toSet())
        assertEquals(drive.exports.map { it.id }.toSet(), appliedIds())
        assertEquals(SyncSummary(new = 3, captionsUpdated = 2), report.summary)
        assertNull(report.problem)
    }

    @Test
    fun exportsOfTheSameDayGoInTheOrderDriveGotThem() = runTest {
        drive.add("2026-10-02", savedPosts(entry("A", caption = "later")), createdTime = "2026-10-02T09:00:00Z")
        drive.add("2026-10-02", savedPosts(entry("A", caption = "earlier")), createdTime = "2026-10-02T08:00:00Z")

        sync.run(drive)

        assertEquals("later", post("A").description)
    }

    @Test
    fun anAppliedExportIsNeverAppliedAgain() = runTest {
        val first = drive.add("2026-10-01", savedPosts(entry("A")))
        sync.run(drive)
        posts.delete(post("A").id, at = 5)
        drive.reads.clear()

        drive.add("2026-10-02", savedPosts(entry("B")))
        val report = sync.run(drive)

        assertTrue(first.id !in drive.reads)
        assertEquals(SyncSummary(new = 1), report.summary)
        // A stays deleted: the Export that brought it is not read again.
        assertEquals(listOf("B"), posts.observeAll().first().map { it.shortcode })
    }

    @Test
    fun aSyncWithNothingNewChangesNothing() = runTest {
        drive.add("2026-10-01", savedPosts(entry("A")))
        sync.run(drive)
        thumbnailQueues = 0

        val report = sync.run(drive)

        assertTrue(report.summary.isEmpty)
        assertNull(report.problem)
        assertEquals(0, thumbnailQueues)
    }

    @Test
    fun anExportThatFailsIsReportedNotRecordedAndDoesNotStopTheOthers() = runTest {
        val broken = drive.add("2026-10-01", "not json")
        val unreachable = drive.add("2026-10-02", savedPosts(entry("A")))
        drive.failing[unreachable.id] = IOException("reset")
        val missing = drive.add("2026-10-03", savedPosts(entry("B")))
        drive.files[missing.id] = emptyMap()
        val fine = drive.add("2026-10-04", savedPosts(entry("C")))

        val report = sync.run(drive)

        val failed = SyncProblem.ExportsFailed(listOf(broken, unreachable, missing).map { it.date })
        assertEquals(failed, report.problem)
        assertEquals(failed, status.status.value.problem)
        assertEquals(setOf(fine.id), appliedIds())
        assertEquals(listOf("C"), posts.observeAll().first().map { it.shortcode })

        // The next Sync tries them again.
        drive.files[broken.id] = mapOf(SAVED_POSTS_PATH to savedPosts(entry("D")))
        drive.failing.clear()
        drive.files[missing.id] = mapOf(SAVED_POSTS_PATH to savedPosts(entry("B")))
        val retry = sync.run(drive)

        assertNull(retry.problem)
        assertNull(status.status.value.problem)
        assertEquals(drive.exports.map { it.id }.toSet(), appliedIds())
        assertEquals(setOf("A", "B", "C", "D"), posts.observeAll().first().map { it.shortcode }.toSet())
    }

    @Test
    fun deletedPostsInEitherStageNeverComeBack() = runTest {
        posts.insert(Post(shortcode = "A", url = "https://www.instagram.com/p/A/", addedAt = 1))
        posts.insert(Post(shortcode = "B", url = "https://www.instagram.com/p/B/", addedAt = 1))
        posts.delete(listOf(post("A").id, post("B").id), at = 2)
        db.recentlyDeletedDao().purge(deletedUpTo = 2)
        posts.insert(Post(shortcode = "C", url = "https://www.instagram.com/p/C/", addedAt = 3))
        posts.delete(post("C").id, at = 4)
        drive.add("2026-10-01", savedPosts(entry("A"), entry("B"), entry("C")))

        val report = sync.run(drive)

        assertEquals(SyncSummary(), report.summary)
        assertTrue(posts.observeAll().first().isEmpty())
        assertEquals(listOf("C"), db.recentlyDeletedDao().observe().first().map { it.shortcode })
        assertTrue(db.recentlyDeletedDao().hasTrace("A"))
    }

    @Test
    fun addsPostsWithTheirTextOwnerSavedDateAndInstagramCollections() = runTest {
        drive.add(
            "2026-10-01",
            savedPosts(entry("A", caption = "Pasta night. With friends", savedAtSeconds = 1_790_000_000)),
            savedCollections("Recipes" to listOf("A"), "Dinner" to listOf("A")),
        )

        sync.run(drive)

        val a = post("A")
        assertEquals("Pasta night. With friends", a.description)
        assertEquals("Pasta night", a.title)
        assertEquals("owner.A" to "Owner A", a.ownerUsername to a.ownerName)
        assertEquals(1_790_000_000_000, a.addedAt)
        assertTrue(a.seenInExport)
        assertEquals(listOf("Recipes", "Dinner"), a.instagramCollections)
        // Placement: the first alphabetically, created.
        assertEquals(listOf("Dinner"), collectionNames())
        assertEquals(collections.observeAll().first().single().collection.id, a.collectionId)
    }

    // sync-spec test 2, through the database.
    @Test
    fun placesIntoAnExistingCollectionIgnoringCase() = runTest {
        val recipes = checkNotNull(collections.create("recipes", color = 1, note = ""))
        drive.add("2026-10-01", savedPosts(entry("A")), savedCollections("Recipes" to listOf("A")))

        sync.run(drive)

        assertEquals(recipes, post("A").collectionId)
        assertEquals(listOf("recipes"), collectionNames())
    }

    // sync-spec test 11b, through the database.
    @Test
    fun aShareInPostStillInToSortIsPlacedByItsFirstSightingWithoutChangingItsModifiedDate() = runTest {
        posts.insert(Post(shortcode = "A", url = "https://www.instagram.com/reel/A/", addedAt = 7))
        drive.add("2026-10-01", savedPosts(entry("A")), savedCollections("Recipes" to listOf("A")))

        val report = sync.run(drive)

        val a = post("A")
        assertEquals(listOf("Recipes"), collectionNames())
        assertEquals(collections.observeAll().first().single().collection.id, a.collectionId)
        assertTrue(a.seenInExport)
        assertEquals("https://www.instagram.com/reel/A/", a.url)
        assertEquals(7L to 7L, a.addedAt to a.modifiedAt)
        assertEquals(0, report.summary.new)
    }

    @Test
    fun queuesThumbnailsOnceAfterTheExportsThatAddedPosts() = runTest {
        drive.add("2026-10-01", savedPosts(entry("A")))
        drive.add("2026-10-02", savedPosts(entry("B")))

        sync.run(drive)

        assertEquals(1, thumbnailQueues)
    }

    @Test
    fun recordsWhenItSyncedAndTheNewestExportApplied() = runTest {
        drive.add("2026-10-02", savedPosts(entry("A")))
        drive.add("2026-10-01", savedPosts(entry("B")))
        now = 42

        sync.run(drive)

        assertEquals(SyncStatus(syncedAt = 42, newestExport = LocalDate.of(2026, 10, 2)), status.status.value)
    }

    @Test
    fun theStatusSurvivesARestartOfTheApp() = runTest {
        drive.add("2026-10-01", savedPosts(entry("A")))
        sync.run(drive)

        val reloaded = SyncStatusStore(context.getSharedPreferences("sync-test", Context.MODE_PRIVATE))

        assertEquals(status.status.value, reloaded.status.value)
    }

    @Test
    fun aSyncThatCannotReachDriveSaysWhyAndKeepsTheLastSync() = runTest {
        drive.add("2026-10-01", savedPosts(entry("A")))
        now = 10
        sync.run(drive)
        now = 20

        drive.listing = IOException("offline")
        assertEquals(SyncProblem.Offline, sync.run(drive).problem)
        assertEquals(SyncStatus(10, LocalDate.of(2026, 10, 1), SyncProblem.Offline), status.status.value)

        drive.listing = DriveAccessException("401")
        assertEquals(SyncProblem.AccessRefused, sync.run(drive).problem)
        assertEquals(SyncProblem.AccessRefused, status.status.value.problem)

        drive.listing = null
        sync.run(drive)
        assertEquals(SyncStatus(20, LocalDate.of(2026, 10, 1)), status.status.value)
    }

    @Test
    fun accessRefusedHalfwayKeepsTheExportsAppliedSoFar() = runTest {
        val first = drive.add("2026-10-01", savedPosts(entry("A")))
        val second = drive.add("2026-10-02", savedPosts(entry("B")))
        drive.failing[second.id] = DriveAccessException("401")

        val report = sync.run(drive)

        assertEquals(SyncProblem.AccessRefused, report.problem)
        assertEquals(setOf(first.id), appliedIds())
        assertEquals(1, thumbnailQueues)
    }

    @Test
    fun aDriveWithoutExportsIsAProblem() = runTest {
        assertEquals(SyncProblem.NoExport, sync.run(drive).problem)
        assertEquals(SyncStatus(problem = SyncProblem.NoExport), status.status.value)
    }

    // sync-spec test 9, through the pipeline.
    @Test
    fun anExportWithNoPostsIsAppliedWithoutChanges() = runTest {
        val empty = drive.add("2026-10-01", "[]")

        val report = sync.run(drive)

        assertTrue(report.summary.isEmpty)
        assertNull(report.problem)
        assertEquals(setOf(empty.id), appliedIds())
    }

    @Test
    fun notGrantingAccessIsRecorded() {
        sync.failed(SyncProblem.AccessNotGranted)

        assertEquals(SyncProblem.AccessNotGranted, status.status.value.problem)
    }
}
