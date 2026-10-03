package com.maxlutz.instasaved.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.data.Backup
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.updateText
import com.maxlutz.instasaved.deleted.RecentlyDeleted
import com.maxlutz.instasaved.thumbnails.ThumbnailStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
class BackupsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var thumbnails: ThumbnailStore
    private lateinit var backups: Backups
    private var clock = 0L
    private val posts get() = db.postDao()
    private val collections get() = db.collectionDao()
    private val tags get() = db.tagDao()
    private val deleted get() = db.recentlyDeletedDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        thumbnails = ThumbnailStore(folder.root)
        backups = Backups(db.backupDao(), thumbnails, now = { clock })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addPost(shortcode: String, collectionId: Long? = null): Long {
        val id = posts.insert(Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = 1L))
        posts.setCollection(id, collectionId, at = 1L)
        return id
    }

    private suspend fun written(): String = ByteArrayOutputStream().also { backups.write(it) }.toString("UTF-8")

    private suspend fun restore(file: String) = backups.restore(file.byteInputStream())

    private suspend fun held(): Backup = db.backupDao().read()

    private suspend fun shortcodes() = posts.observeAll().first().map { it.shortcode }
    private suspend fun collectionNames() = collections.observeAll().first().map { it.collection.name }
    private suspend fun recentlyDeleted() = deleted.observe().first().map { it.shortcode }

    /** What [restore] refuses [file] with, after checking that it left the app's data alone. */
    private suspend fun refused(file: String): BackupFormatException {
        val before = held()
        val refusal = try {
            restore(file)
            null
        } catch (e: BackupFormatException) {
            e
        }
        assertEquals(before, held())
        return checkNotNull(refusal) { "The file was restored" }
    }

    // Write, then restore

    @Test
    fun restoreBringsBackEverythingTheBackupWasTakenWith() = runTest {
        val recipes = checkNotNull(collections.create("Recipes", PALETTE[2], "Weeknights"))
        val vegan = checkNotNull(tags.create("Vegan", PALETTE[4]))
        val quick = checkNotNull(tags.create("Quick", PALETTE[1]))
        val a = addPost("A", recipes)
        addPost("B")
        tags.addToPost(a, vegan, at = 2)
        tags.addToPost(a, quick, at = 3)
        val post = checkNotNull(posts.get("A"))
        posts.updateText(
            post.copy(title = "Dal", titleHandEdited = true, description = "Lentils", postNote = "Double the garlic"),
            at = 4,
        )
        val taken = held()
        val file = written()

        deleted.purge(deletedUpTo = Long.MAX_VALUE)
        collections.deleteKeepingPosts(recipes)
        posts.delete(a, at = 5)
        restore(file)

        assertEquals(taken, held())
        assertEquals(listOf("Quick", "Vegan"), tags.observeOnPost(a).first().map { it.name })
        assertEquals("Double the garlic", posts.get("A")?.postNote)
    }

    @Test
    fun restoreWipesWhatTheAppHeldBefore() = runTest {
        addPost("A")
        val file = written()
        addPost("B", checkNotNull(collections.create("Later", PALETTE[0])))
        checkNotNull(tags.create("Vegan", PALETTE[0]))

        restore(file)

        assertEquals(listOf("A"), shortcodes())
        assertEquals(emptyList<String>(), collectionNames())
        assertEquals(emptyList<String>(), tags.observeAll().first().map { it.tag.name })
    }

    @Test
    fun anEmptyAppBacksUpAndRestores() = runTest {
        val file = written()
        addPost("A")

        restore(file)

        assertEquals(Backup(), held())
    }

    @Test
    fun theAppKeepsWorkingOnARestoredBackup() = runTest {
        val recipes = checkNotNull(collections.create("Recipes", PALETTE[0]))
        addPost("A", recipes)
        restore(written())

        val b = addPost("B", recipes)
        val travel = checkNotNull(collections.create("Travel", PALETTE[1]))

        assertEquals(listOf("A", "B"), posts.observeInCollection(recipes).first().map { it.shortcode }.sorted())
        assertTrue(b != checkNotNull(posts.get("A")).id)
        assertTrue(travel != recipes)
    }

    // Deleted Posts (ADR-0012)

    @Test
    fun recentlyDeletedPostsComeBackWholeWithTheirDeletionDates() = runTest {
        val recipes = checkNotNull(collections.create("Recipes", PALETTE[0]))
        val a = addPost("A", recipes)
        tags.addToPost(a, checkNotNull(tags.create("Vegan", PALETTE[0])), at = 2)
        posts.delete(a, at = 10.days.inWholeMilliseconds)
        val file = written()
        deleted.purge(deletedUpTo = Long.MAX_VALUE)

        restore(file)

        assertEquals(listOf("A"), recentlyDeleted())
        assertEquals(emptyList<String>(), shortcodes())
        assertEquals(10.days.inWholeMilliseconds, posts.get("A")?.deletedAt)
        deleted.restore(a)
        assertEquals(listOf("A"), posts.observeInCollection(recipes).first().map { it.shortcode })
        assertEquals(listOf("Vegan"), tags.observeOnPost(a).first().map { it.name })
    }

    @Test
    fun theThirtyDayCountdownGoesOnFromTheDeletionDate() = runTest {
        val a = addPost("A")
        val b = addPost("B")
        posts.delete(a, at = 1.days.inWholeMilliseconds)
        posts.delete(b, at = 20.days.inWholeMilliseconds)
        val file = written()

        restore(file)
        RecentlyDeleted(deleted, thumbnails, now = { 35.days.inWholeMilliseconds }).purgeExpired()

        assertEquals(listOf("B"), recentlyDeleted())
        assertTrue(deleted.hasTrace("A"))
    }

    @Test
    fun aCollectionDeletedWithItsPostsCanStillBeRebuilt() = runTest {
        val recipes = checkNotNull(collections.create("Recipes", PALETTE[3], "Weeknights"))
        val a = addPost("A", recipes)
        collections.deleteWithPosts(recipes, at = 5)
        val file = written()
        deleted.purge(deletedUpTo = Long.MAX_VALUE)

        restore(file)
        deleted.restore(a)

        val rebuilt = collections.observeAll().first().single().collection
        assertEquals(Triple("Recipes", PALETTE[3], "Weeknights"), Triple(rebuilt.name, rebuilt.color, rebuilt.note))
        assertEquals(rebuilt.id, posts.get("A")?.collectionId)
    }

    @Test
    fun tracesOfDeletedPostsComeBack() = runTest {
        posts.delete(addPost("A"), at = 1)
        deleted.purge(deletedUpTo = Long.MAX_VALUE)
        val file = written()
        deleted.forgetTrace("A")

        restore(file)

        assertTrue(deleted.hasTrace("A"))
    }

    @Test
    fun tracesTheBackupDoesNotHaveAreWiped() = runTest {
        val file = written()
        posts.delete(addPost("A"), at = 1)
        deleted.purge(deletedUpTo = Long.MAX_VALUE)

        restore(file)

        assertFalse(deleted.hasTrace("A"))
    }

    // Thumbnails (ADR-0001)

    @Test
    fun thumbnailDownloadsStartOverAfterARestore() = runTest {
        val a = addPost("A")
        posts.thumbnailFailed(a, at = 7)

        restore(written())

        assertEquals(0, posts.get("A")?.thumbnailFailures)
        assertEquals(null, posts.get("A")?.thumbnailFailedAt)
    }

    @Test
    fun onlyThumbnailsOfPostsInTheBackupAreKept() = runTest {
        val a = addPost("A")
        posts.delete(a, at = 1)
        val file = written()
        addPost("B")
        thumbnails.save("A", byteArrayOf(1))
        thumbnails.save("B", byteArrayOf(2))

        restore(file)

        assertEquals(setOf("A"), thumbnails.shortcodes.value)
        assertFalse(thumbnails.file("B").exists())
    }

    // Files that are refused

    @Test
    fun aFileThatIsNotABackupChangesNothing() = runTest {
        addPost("A")

        assertFalse(refused("not json").fromNewerApp)
        assertFalse(refused("[]").fromNewerApp)
        assertFalse(refused("""{"saved_saved_media": []}""").fromNewerApp)
        assertFalse(refused("""{"app": "instasaved", "version": 1}""").fromNewerApp)
    }

    @Test
    fun aBackupFromANewerAppIsRefusedAsSuch() = runTest {
        addPost("A")
        val file = written().replace("\"version\":$BACKUP_FILE_VERSION", "\"version\":${BACKUP_FILE_VERSION + 1}")

        assertTrue(refused(file).fromNewerApp)
    }

    @Test
    fun aBackupThatDoesNotHoldTogetherChangesNothing() = runTest {
        val recipes = checkNotNull(collections.create("Recipes", PALETTE[0]))
        addPost("A", recipes)
        val file = written()
        addPost("B")

        // A Post in a Collection the file does not have.
        refused(file.replace("\"collectionId\":$recipes", "\"collectionId\":${recipes + 100}"))
        // A Post without its shortcode.
        refused(file.replace("\"shortcode\":\"A\",", ""))

        assertEquals(listOf("A", "B"), shortcodes().sorted())
    }

    @Test
    fun aRefusedFileKeepsEveryThumbnail() = runTest {
        addPost("A")
        thumbnails.save("A", byteArrayOf(1))

        assertThrows(BackupFormatException::class.java) { parseBackup("{}") }
        refused("{}")

        assertEquals(setOf("A"), thumbnails.shortcodes.value)
    }
}
