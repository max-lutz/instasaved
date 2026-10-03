package com.maxlutz.instasaved.deleted

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.thumbnails.ThumbnailStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
class RecentlyDeletedTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var thumbnails: ThumbnailStore
    private lateinit var recentlyDeleted: RecentlyDeleted
    private var clock = 0L
    private val posts get() = db.postDao()
    private val collections get() = db.collectionDao()
    private val dao get() = db.recentlyDeletedDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        thumbnails = ThumbnailStore(folder.root)
        recentlyDeleted = RecentlyDeleted(dao, thumbnails, now = { clock })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addPost(shortcode: String, collectionId: Long? = null): Long {
        val id = posts.insert(Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = 1L))
        posts.setCollection(id, collectionId, at = 1L)
        return id
    }

    private suspend fun create(name: String, color: Int = PALETTE[0], note: String = "") =
        checkNotNull(collections.create(name, color, note))

    private suspend fun listed() = dao.observe().first().map { it.shortcode }
    private suspend fun names() = collections.observeAll().first().map { it.collection.name }
    private suspend fun toSort() = posts.observeToSort().first().map { it.shortcode }
    private suspend fun inCollection(id: Long) = posts.observeInCollection(id).first().map { it.shortcode }
    private suspend fun collectionNamed(name: String) =
        collections.observeAll().first().single { it.collection.name == name }.collection

    private fun wait(days: Int, hours: Int = 0) {
        clock += days.days.inWholeMilliseconds + hours.hours.inWholeMilliseconds
    }

    // The view

    @Test
    fun listsDeletedPostsLastDeletedFirst() = runTest {
        val a = addPost("A")
        val b = addPost("B")
        addPost("C")

        posts.delete(a, at = 1)
        posts.delete(b, at = 2)

        assertEquals(listOf("B", "A"), listed())
    }

    // Restore

    @Test
    fun restorePutsThePostBackInItsCollection() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A", recipes)
        posts.delete(post, at = 1)

        dao.restore(post)

        assertEquals(listOf("A"), inCollection(recipes))
        assertEquals(emptyList<String>(), listed())
    }

    @Test
    fun restoreKeepsThePostWhole() = runTest {
        val post = addPost("A")
        val before = checkNotNull(posts.get("A")).copy(title = "Carbonara", postNote = "Try on Sunday")
        posts.updateText(post, before.title, true, "", false, before.postNote, at = 1L)
        posts.delete(post, at = 1)

        dao.restore(post)

        assertEquals(before.copy(titleHandEdited = true), posts.get("A"))
    }

    @Test
    fun restoreRecreatesACollectionDeletedTogetherWithThePost() = runTest {
        val recipes = create("Recipes", PALETTE[4], "Weeknight dinners")
        val post = addPost("A", recipes)
        collections.deleteWithPosts(recipes, at = 1)

        dao.restore(post)

        val recreated = collectionNamed("Recipes")
        assertEquals(Collection(recreated.id, "Recipes", PALETTE[4], "Weeknight dinners"), recreated)
        assertEquals(listOf("A"), inCollection(recreated.id))
        assertNull(posts.get("A")?.deletionId)
    }

    @Test
    fun restoringEveryPostOfADeletedCollectionRebuildsItOnce() = runTest {
        val recipes = create("Recipes")
        val a = addPost("A", recipes)
        val b = addPost("B", recipes)
        collections.deleteWithPosts(recipes, at = 1)

        dao.restore(a)
        dao.restore(b)

        assertEquals(listOf("Recipes"), names())
        assertEquals(setOf("A", "B"), inCollection(collectionNamed("Recipes").id).toSet())
    }

    @Test
    fun restoreJoinsACollectionThatTookTheNameMeanwhile() = runTest {
        val post = addPost("A", create("Recipes", PALETTE[4]))
        collections.deleteWithPosts(collectionNamed("Recipes").id, at = 1)
        val newer = create("recipes", PALETTE[7])

        dao.restore(post)

        assertEquals(listOf("recipes"), names())
        assertEquals(listOf("A"), inCollection(newer))
        assertEquals(PALETTE[7], collectionNamed("recipes").color)
    }

    @Test
    fun aCollectionDeletedSeparatelyLeavesTheRestoredPostInToSort() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A", recipes)
        posts.delete(post, at = 1)
        collections.deleteKeepingPosts(recipes)

        dao.restore(post)

        assertEquals(listOf("A"), toSort())
        assertEquals(emptyList<String>(), names())
    }

    @Test
    fun restoringAPostThatIsNotDeletedChangesNothing() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A", recipes)

        dao.restore(post)

        assertEquals(listOf("A"), inCollection(recipes))
    }

    // Empty now

    @Test
    fun emptyNowReducesEveryDeletedPostToItsTrace() = runTest {
        val a = addPost("A")
        addPost("B")
        posts.delete(a, at = 1)

        recentlyDeleted.empty()

        assertEquals(emptyList<String>(), listed())
        assertNull(posts.get("A"))
        assertTrue(dao.hasTrace("A"))
        assertFalse(dao.hasTrace("B"))
        assertEquals(listOf("B"), toSort())
    }

    @Test
    fun emptyNowRemovesTheTagsAndTheThumbnail() = runTest {
        val post = addPost("A")
        val vegan = checkNotNull(db.tagDao().create("Vegan", PALETTE[0]))
        db.tagDao().addToPost(post, vegan, at = 1L)
        thumbnails.save("A", byteArrayOf(1))
        thumbnails.save("B", byteArrayOf(1))
        posts.delete(post, at = 1)

        recentlyDeleted.empty()

        assertEquals(setOf("B"), thumbnails.shortcodes.value)
        assertFalse(thumbnails.file("A").exists())
        assertEquals(0, db.tagDao().observeAll().first().single().postCount)
        assertEquals(emptyList<String>(), db.tagDao().observeOnPost(post).first().map { it.name })
    }

    @Test
    fun aCollectionDeletionIsForgottenWithItsLastPost() = runTest {
        val recipes = create("Recipes")
        val a = addPost("A", recipes)
        addPost("B", recipes)
        collections.deleteWithPosts(recipes, at = 1)
        val deletionId = checkNotNull(posts.get("A")?.deletionId)

        dao.restore(a)
        assertEquals("Recipes", collections.getDeletion(deletionId)?.collectionName)

        recentlyDeleted.empty()
        assertNull(collections.getDeletion(deletionId))
    }

    // The 30 days

    @Test
    fun aDeletedPostStaysThirtyDays() = runTest {
        posts.delete(addPost("A"), at = clock)

        wait(days = 29, hours = 23)
        recentlyDeleted.purgeExpired()

        assertEquals(listOf("A"), listed())
        assertFalse(dao.hasTrace("A"))
    }

    @Test
    fun afterThirtyDaysOnlyTheTraceIsLeft() = runTest {
        posts.delete(addPost("A"), at = clock)
        wait(days = 20)
        posts.delete(addPost("B"), at = clock)

        wait(days = 10)
        recentlyDeleted.purgeExpired()

        assertEquals(listOf("B"), listed())
        assertTrue(dao.hasTrace("A"))
        assertNull(posts.get("A"))
    }

    @Test
    fun daysLeftCountsDownFromThirtyRoundingUp() {
        val post = Post(shortcode = "A", url = "", addedAt = 0, deletedAt = 0)
        val day = 1.days.inWholeMilliseconds

        assertEquals(30, RecentlyDeleted.daysLeft(post, now = 0))
        assertEquals(30, RecentlyDeleted.daysLeft(post, now = day - 1))
        assertEquals(29, RecentlyDeleted.daysLeft(post, now = day))
        assertEquals(1, RecentlyDeleted.daysLeft(post, now = 30 * day - 1))
        assertEquals(0, RecentlyDeleted.daysLeft(post, now = 30 * day))
        assertEquals(0, RecentlyDeleted.daysLeft(post.copy(deletedAt = null), now = 0))
    }
}
