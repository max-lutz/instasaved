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
        listOf("A", "B", "C").forEach { posts.setCollection(addPost(it), recipes, at = 1L) }
        posts.delete(checkNotNull(posts.get("C")).id, at = 9L)

        assertEquals(
            mapOf("Recipes" to 2, "Travel" to 0),
            collections.observeAll().first().associate { it.collection.name to it.postCount },
        )
    }

    // Bare Posts

    private suspend fun addBare(shortcode: String, collectionId: Long? = null): Long =
        addPost(shortcode).also { if (collectionId != null) posts.setCollection(it, collectionId, at = 1L) }

    private suspend fun setNote(shortcode: String, note: String) =
        posts.updateText(checkNotNull(posts.get(shortcode)).copy(postNote = note), at = 2L)

    private suspend fun bareCounts() =
        collections.observeAll().first().associate { it.collection.name to it.bareCount }

    private suspend fun bareCount() = posts.observeBareCount().first()

    @Test
    fun aPostWithNoTagAndNoPostNoteIsBare() = runTest {
        val recipes = create("Recipes")
        create("Travel")
        listOf("A", "B", "C").forEach { addBare(it, recipes) }

        assertEquals(mapOf("Recipes" to 3, "Travel" to 0), bareCounts())
        assertEquals(3, bareCount())
    }

    @Test
    fun aPostWithATagIsNotBare() = runTest {
        val recipes = create("Recipes")
        val a = addBare("A", recipes)
        addBare("B", recipes)
        val vegan = checkNotNull(db.tagDao().create("Vegan", PALETTE[0]))
        val quick = checkNotNull(db.tagDao().create("Quick", PALETTE[1]))
        db.tagDao().addToPost(a, vegan, at = 2L)
        db.tagDao().addToPost(a, quick, at = 2L)

        assertEquals(mapOf("Recipes" to 1), bareCounts())
        assertEquals(1, bareCount())
        assertEquals(2, collections.observeAll().first().single().postCount)
    }

    @Test
    fun aPostWithAPostNoteIsNotBareUnlessTheNoteIsBlank() = runTest {
        val recipes = create("Recipes")
        listOf("A", "B", "C").forEach { addBare(it, recipes) }
        setNote("A", "Try on Sunday")
        setNote("B", " \n\t ")

        assertEquals(mapOf("Recipes" to 2), bareCounts())
        assertEquals(2, bareCount())
    }

    @Test
    fun aHandEditedTitleOrDescriptionLeavesAPostBare() = runTest {
        val recipes = create("Recipes")
        addBare("A", recipes)
        val edited = checkNotNull(posts.get("A")).copy(
            title = "Ramen",
            titleHandEdited = true,
            description = "The one from Kyoto",
            descriptionHandEdited = true,
        )
        posts.updateText(edited, at = 2L)

        assertEquals(mapOf("Recipes" to 1), bareCounts())
        assertEquals(1, bareCount())
    }

    @Test
    fun recentlyDeletedPostsAreNotCountedAsBare() = runTest {
        val recipes = create("Recipes")
        addBare("A", recipes)
        posts.delete(addBare("B", recipes), at = 9L)
        posts.delete(addBare("C"), at = 9L)

        assertEquals(mapOf("Recipes" to 1), bareCounts())
        assertEquals(1, bareCount())
    }

    @Test
    fun theBareCountOfAllPostsIncludesToSort() = runTest {
        val recipes = create("Recipes")
        addBare("A", recipes)
        addBare("B")
        addBare("C")
        setNote("C", "Gift idea")

        assertEquals(mapOf("Recipes" to 1), bareCounts())
        assertEquals(2, bareCount())
    }

    @Test
    fun theBareCountFollowsTagsAndPostNotesAddedAndRemoved() = runTest {
        val recipes = create("Recipes")
        val a = addBare("A", recipes)
        val vegan = checkNotNull(db.tagDao().create("Vegan", PALETTE[0]))

        db.tagDao().addToPost(a, vegan, at = 2L)
        assertEquals(mapOf("Recipes" to 0), bareCounts())
        assertEquals(0, bareCount())

        db.tagDao().removeFromPost(a, vegan, at = 3L)
        assertEquals(mapOf("Recipes" to 1), bareCounts())

        setNote("A", "Try on Sunday")
        assertEquals(mapOf("Recipes" to 0), bareCounts())

        setNote("A", "")
        assertEquals(mapOf("Recipes" to 1), bareCounts())
        assertEquals(1, bareCount())
    }

    // Assigning

    @Test
    fun assigningAPostTakesItOutOfToSort() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A")
        addPost("B")

        posts.setCollection(post, recipes, at = 1L)

        assertEquals(listOf("B"), toSort())
        assertEquals(listOf("A"), inCollection(recipes))
    }

    @Test
    fun aPostIsInOneCollectionAtATime() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        val post = addPost("A")

        posts.setCollection(post, recipes, at = 1L)
        posts.setCollection(post, travel, at = 1L)

        assertEquals(emptyList<String>(), inCollection(recipes))
        assertEquals(listOf("A"), inCollection(travel))
    }

    @Test
    fun clearingTheCollectionPutsThePostBackInToSort() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A")
        posts.setCollection(post, recipes, at = 1L)

        posts.setCollection(post, null, at = 1L)

        assertEquals(listOf("A"), toSort())
        assertEquals(emptyList<String>(), inCollection(recipes))
    }

    @Test
    fun aCollectionListsItsPostsNewestFirstWithoutDeletedOnes() = runTest {
        val recipes = create("Recipes")
        posts.setCollection(addPost("A", addedAt = 1L), recipes, at = 1L)
        posts.setCollection(addPost("B", addedAt = 2L), recipes, at = 1L)
        posts.setCollection(addPost("C", addedAt = 3L), recipes, at = 1L)
        posts.delete(checkNotNull(posts.get("B")).id, at = 9L)

        assertEquals(listOf("C", "A"), inCollection(recipes))
    }

    // Moving several Posts

    @Test
    fun movesSeveralPostsAtOnce() = runTest {
        val recipes = create("Recipes")
        val a = addPost("A", addedAt = 1L)
        val b = addPost("B", addedAt = 2L)
        addPost("C", addedAt = 3L)

        posts.setCollection(listOf(a, b), recipes, at = 7L)

        assertEquals(listOf("B", "A"), inCollection(recipes))
        assertEquals(listOf("C"), toSort())
        assertEquals(listOf(7L, 7L), listOf("A", "B").map { posts.get(it)?.modifiedAt })
    }

    @Test
    fun undoingAMovePutsEveryPostBackWhereItWasAsItWas() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        val a = addPost("A", addedAt = 1L)
        val b = addPost("B", addedAt = 2L)
        posts.setCollection(a, recipes, at = 4L)
        val before = listOf("A", "B").map { checkNotNull(posts.get(it)) }

        posts.setCollection(listOf(a, b), travel, at = 7L)
        posts.undoMove(before)

        assertEquals(before, listOf("A", "B").map { posts.get(it) })
    }

    @Test
    fun undoingAMoveLeavesInToSortAPostWhoseCollectionIsGone() = runTest {
        val recipes = create("Recipes")
        val a = addPost("A")
        posts.setCollection(a, recipes, at = 4L)
        val before = listOf(checkNotNull(posts.get("A")))

        posts.setCollection(listOf(a), null, at = 7L)
        collections.deleteKeepingPosts(recipes)
        posts.undoMove(before)

        assertEquals(listOf("A"), toSort())
    }

    // Moving all the Posts of a Collection

    @Test
    fun movingAllPostsLeavesTheCollectionInPlaceEmpty() = runTest {
        val recipes = create("Recipes", PALETTE[4], "Weeknight dinners")
        val travel = create("Travel")
        posts.setCollection(addPost("A", addedAt = 1L), recipes, at = 2L)
        posts.setCollection(addPost("B", addedAt = 2L), recipes, at = 2L)
        posts.setCollection(addPost("C", addedAt = 3L), travel, at = 2L)
        addPost("D")

        val move = collections.moveAllPosts(recipes, travel, at = 7L, deleteFrom = false)

        assertEquals(2, move?.movedCount)
        assertEquals(listOf("C", "B", "A"), inCollection(travel))
        assertEquals(emptyList<String>(), inCollection(recipes))
        assertEquals(listOf("D"), toSort())
        assertEquals(Collection(recipes, "Recipes", PALETTE[4], "Weeknight dinners"), collections.get(recipes))
    }

    @Test
    fun movingAllPostsMarksThemModifiedLikeMovingOne() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes, at = 2L)
        posts.setCollection(addPost("B"), travel, at = 2L)

        collections.moveAllPosts(recipes, travel, at = 7L, deleteFrom = false)

        assertEquals(listOf(7L, 2L), listOf("A", "B").map { posts.get(it)?.modifiedAt })
    }

    @Test
    fun movingAllPostsLeavesRecentlyDeletedOnesWhereTheyWere() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        val a = addPost("A")
        posts.setCollection(a, recipes, at = 2L)
        posts.delete(a, at = 5L)

        val move = collections.moveAllPosts(recipes, travel, at = 7L, deleteFrom = false)

        assertEquals(0, move?.movedCount)
        assertEquals(recipes, posts.get("A")?.collectionId)
        assertEquals(2L, posts.get("A")?.modifiedAt)
    }

    @Test
    fun movingAllPostsCanDeleteTheEmptiedCollection() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes, at = 2L)

        collections.moveAllPosts(recipes, travel, at = 7L, deleteFrom = true)

        assertEquals(listOf("Travel"), names())
        assertEquals(listOf("A"), inCollection(travel))
        assertEquals(emptyList<String>(), toSort())
    }

    @Test
    fun movingAllPostsToTheCollectionItselfOrToAMissingOneChangesNothing() = runTest {
        val recipes = create("Recipes")
        posts.setCollection(addPost("A"), recipes, at = 2L)

        assertNull(collections.moveAllPosts(recipes, recipes, at = 7L, deleteFrom = true))
        assertNull(collections.moveAllPosts(recipes, recipes + 1, at = 7L, deleteFrom = true))
        assertNull(collections.moveAllPosts(recipes + 1, recipes, at = 7L, deleteFrom = false))

        assertEquals(listOf("Recipes"), names())
        assertEquals(listOf("A"), inCollection(recipes))
        assertEquals(2L, posts.get("A")?.modifiedAt)
    }

    @Test
    fun undoingAMoveOfAllPostsPutsThemBackAsTheyWere() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes, at = 2L)
        posts.setCollection(addPost("B"), recipes, at = 3L)
        posts.setCollection(addPost("C"), travel, at = 4L)
        val before = listOf("A", "B", "C").map { posts.get(it) }

        val move = checkNotNull(collections.moveAllPosts(recipes, travel, at = 7L, deleteFrom = false))
        collections.undoMoveAllPosts(move)

        assertEquals(before, listOf("A", "B", "C").map { posts.get(it) })
    }

    @Test
    fun undoingAMoveThatDeletedTheCollectionBringsItBackWithItsNoteAndColor() = runTest {
        val recipes = create("Recipes", PALETTE[4], "Weeknight dinners")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes, at = 2L)
        posts.setCollection(addPost("B"), travel, at = 3L)
        val before = listOf("A", "B").map { posts.get(it) }

        val move = checkNotNull(collections.moveAllPosts(recipes, travel, at = 7L, deleteFrom = true))
        collections.undoMoveAllPosts(move)

        assertEquals(Collection(recipes, "Recipes", PALETTE[4], "Weeknight dinners"), collections.get(recipes))
        assertEquals(before, listOf("A", "B").map { posts.get(it) })
    }

    @Test
    fun undoingAMoveThatDeletedTheCollectionPutsItsRecentlyDeletedPostsBackInItToo() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        val a = addPost("A")
        posts.setCollection(a, recipes, at = 2L)
        posts.delete(a, at = 5L)
        posts.setCollection(addPost("B"), recipes, at = 3L)
        val before = posts.get("A")

        val move = checkNotNull(collections.moveAllPosts(recipes, travel, at = 7L, deleteFrom = true))
        assertEquals(1, move.movedCount)
        assertNull(posts.get("A")?.collectionId)
        collections.undoMoveAllPosts(move)

        assertEquals(before, posts.get("A"))
    }

    @Test
    fun undoingAMoveThatDeletedTheCollectionJoinsTheOneThatTookItsNameMeanwhile() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes, at = 2L)

        val move = checkNotNull(collections.moveAllPosts(recipes, travel, at = 7L, deleteFrom = true))
        val again = create("recipes", PALETTE[2])
        collections.undoMoveAllPosts(move)

        assertEquals(listOf("recipes", "Travel"), names())
        assertEquals(listOf("A"), inCollection(again))
        assertEquals(2L, posts.get("A")?.modifiedAt)
    }

    // Deleting

    @Test
    fun deletingAndKeepingPostsMovesThemToToSort() = runTest {
        val recipes = create("Recipes")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes, at = 1L)
        posts.setCollection(addPost("B"), travel, at = 1L)

        collections.deleteKeepingPosts(recipes)

        assertEquals(listOf("Travel"), names())
        assertEquals(listOf("A"), toSort())
        assertEquals(listOf("B"), inCollection(travel))
    }

    @Test
    fun aRecentlyDeletedPostOfADeletedCollectionWouldBeRestoredToToSort() = runTest {
        val recipes = create("Recipes")
        val post = addPost("A")
        posts.setCollection(post, recipes, at = 1L)
        posts.delete(post, at = 9L)

        collections.deleteKeepingPosts(recipes)
        db.recentlyDeletedDao().restore(post)

        assertEquals(listOf("A"), toSort())
    }

    @Test
    fun deletingWithPostsMovesThemToRecentlyDeletedAsOneAction() = runTest {
        val recipes = create("Recipes", PALETTE[4], "Weeknight dinners")
        val travel = create("Travel")
        posts.setCollection(addPost("A"), recipes, at = 1L)
        posts.setCollection(addPost("B"), recipes, at = 1L)
        posts.setCollection(addPost("C"), travel, at = 1L)
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
        posts.setCollection(post, recipes, at = 1L)
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
