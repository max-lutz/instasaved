package com.maxlutz.instasaved.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionsTest {
    private lateinit var db: AppDatabase
    private val collections get() = db.collectionDao()
    private val posts get() = db.postDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addPost(shortcode: String, addedAt: Long = 1L): Long =
        posts.insert(Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = addedAt))

    private suspend fun create(name: String, color: Int = PALETTE[0], note: String = "") =
        checkNotNull(collections.create(name, color, note))

    private suspend fun names() = collections.observeAll().first().map { it.collection.name }
    private suspend fun toSort() = posts.observeToSort().first().map { it.shortcode }
    private suspend fun inCollection(id: Long) = posts.observeInCollection(id).first().map { it.shortcode }

    // Create, rename, recolor, note

    @Test
    fun createsACollectionWithItsColorAndNote() = runTest {
        val id = create("Recipes", PALETTE[3], "Weeknight dinners")

        assertEquals(Collection(id, "Recipes", PALETTE[3], "Weeknight dinners"), collections.get(id))
    }

    @Test
    fun namesAreTrimmed() = runTest {
        val id = create("  Recipes ")

        assertEquals("Recipes", collections.get(id)?.name)
    }

    @Test
    fun aBlankNameIsRefused() = runTest {
        assertNull(collections.create("  ", PALETTE[0]))
        assertEquals(emptyList<String>(), names())
    }

    @Test
    fun namesAreUniqueIgnoringCase() = runTest {
        create("Recipes")

        assertNull(collections.create("recipes", PALETTE[1]))
        assertNull(collections.create(" RECIPES ", PALETTE[1]))
        assertEquals(listOf("Recipes"), names())
    }

    @Test
    fun renamesRecolorsAndEditsTheNote() = runTest {
        val id = create("Recipes", PALETTE[0])

        assertTrue(collections.update(Collection(id, " Cooking ", PALETTE[5], "Fast ones")))
        assertEquals(Collection(id, "Cooking", PALETTE[5], "Fast ones"), collections.get(id))
    }

    @Test
    fun renamingToAnotherCollectionsNameChangesNothing() = runTest {
        create("Recipes")
        val travel = create("Travel", PALETTE[1])

        assertFalse(collections.update(Collection(travel, "recipes", PALETTE[5], "note")))
        assertFalse(collections.update(Collection(travel, " ", PALETTE[5], "note")))
        assertEquals(Collection(travel, "Travel", PALETTE[1], ""), collections.get(travel))
    }

    @Test
    fun renamingOnlyTheCaseIsAllowed() = runTest {
        val id = create("recipes")

        assertTrue(collections.update(Collection(id, "Recipes", PALETTE[0])))
        assertEquals("Recipes", collections.get(id)?.name)
    }

    @Test
    fun sameNameIgnoresCaseAndSurroundingSpaces() {
        assertTrue(sameName("Recipes", " recipes "))
        assertFalse(sameName("Recipes", "Recipe"))
    }

    // Listing

    @Test
    fun collectionsAreListedAlphabeticallyIgnoringCase() = runTest {
        listOf("travel", "Apartment", "recipes", "Books").forEach { create(it) }

        assertEquals(listOf("Apartment", "Books", "recipes", "travel"), names())
    }

    @Test
    fun anEmojiPrefixGroupsCollectionsTogether() = runTest {
        listOf("🍝 Pasta", "Books", "✈️ Japan", "🍝 Desserts", "✈️ Italy").forEach { create(it) }

        assertEquals(listOf("Books", "✈️ Italy", "✈️ Japan", "🍝 Desserts", "🍝 Pasta"), names())
    }

    @Test
    fun theListCountsPostsLeavingOutRecentlyDeletedOnes() = runTest {
        val recipes = create("Recipes")
        create("Travel")
        listOf("A", "B", "C").forEach { posts.setCollection(addPost(it), recipes) }
        posts.delete(checkNotNull(posts.get("C")).id, at = 9L)

        assertEquals(
            mapOf("Recipes" to 2, "Travel" to 0),
            collections.observeAll().first().associate { it.collection.name to it.postCount },
        )
    }

    // Assigning

    @Test
    fun assigningAPostTakesItOutOfToSort() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A")
        addPost("B")

        posts.setCollection(post, recipes)

        assertEquals(listOf("B"), toSort())
        assertEquals(listOf("A"), inCollection(recipes))
    }

    @Test
    fun aPostIsInOneCollectionAtATime() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        val post = addPost("A")

        posts.setCollection(post, recipes)
        posts.setCollection(post, travel)

        assertEquals(emptyList<String>(), inCollection(recipes))
        assertEquals(listOf("A"), inCollection(travel))
    }

    @Test
    fun clearingTheCollectionPutsThePostBackInToSort() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A")
        posts.setCollection(post, recipes)

        posts.setCollection(post, null)

        assertEquals(listOf("A"), toSort())
        assertEquals(emptyList<String>(), inCollection(recipes))
    }

    @Test
    fun aCollectionListsItsPostsNewestFirstWithoutDeletedOnes() = runTest {
        val recipes = create("Recipes")
        posts.setCollection(addPost("A", addedAt = 1L), recipes)
        posts.setCollection(addPost("B", addedAt = 2L), recipes)
        posts.setCollection(addPost("C", addedAt = 3L), recipes)
        posts.delete(checkNotNull(posts.get("B")).id, at = 9L)

        assertEquals(listOf("C", "A"), inCollection(recipes))
    }

    // Deleting

    @Test
    fun deletingAndKeepingPostsMovesThemToToSort() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes)
        posts.setCollection(addPost("B"), travel)

        collections.deleteKeepingPosts(recipes)

        assertEquals(listOf("Travel"), names())
        assertEquals(listOf("A"), toSort())
        assertEquals(listOf("B"), inCollection(travel))
    }

    @Test
    fun aRecentlyDeletedPostOfADeletedCollectionWouldBeRestoredToToSort() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A")
        posts.setCollection(post, recipes)
        posts.delete(post, at = 9L)

        collections.deleteKeepingPosts(recipes)
        db.recentlyDeletedDao().restore(post)

        assertEquals(listOf("A"), toSort())
    }

    @Test
    fun deletingWithPostsMovesThemToRecentlyDeletedAsOneAction() = runTest {
        val recipes = create("Recipes", PALETTE[4], "Weeknight dinners")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes)
        posts.setCollection(addPost("B"), recipes)
        posts.setCollection(addPost("C"), travel)
        addPost("D")

        collections.deleteWithPosts(recipes, at = 9L)

        assertEquals(listOf("Travel"), names())
        assertEquals(listOf("D"), toSort())
        assertEquals(listOf("C"), inCollection(travel))
        val deleted = listOf("A", "B").map { checkNotNull(posts.get(it)) }
        assertEquals(listOf(9L, 9L), deleted.map { it.deletedAt })
        val deletionId = deleted.map { it.deletionId }.distinct().single()
        assertNotNull(deletionId)
        assertEquals(
            CollectionDeletion(deletionId!!, "Recipes", PALETTE[4], "Weeknight dinners"),
            collections.getDeletion(deletionId),
        )
    }

    @Test
    fun deletingWithPostsLeavesAlreadyDeletedPostsAsTheyWere() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A")
        posts.setCollection(post, recipes)
        posts.delete(post, at = 5L)

        collections.deleteWithPosts(recipes, at = 9L)

        assertEquals(5L, posts.get("A")?.deletedAt)
        assertNull(posts.get("A")?.deletionId)
    }

    @Test
    fun aDeletedCollectionsNameIsFreeAgain() = runTest {
        val recipes = create("Recipes")
        collections.deleteWithPosts(recipes, at = 9L)

        assertNotNull(collections.create("Recipes", PALETTE[0]))
    }
}
