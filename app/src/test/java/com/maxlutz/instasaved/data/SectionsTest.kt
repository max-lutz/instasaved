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
class SectionsTest {
    private lateinit var db: AppDatabase
    private val sections get() = db.sectionDao()
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

    private suspend fun section(name: String) = checkNotNull(sections.create(name))

    private suspend fun collection(name: String, sectionId: Long? = null) =
        checkNotNull(collections.create(name, PALETTE[0], sectionId = sectionId))

    private suspend fun names() = sections.observeAll().first().map { it.name }

    /** Each Collection's name with the name of its Section, alphabetically. */
    private suspend fun sectionOfEach(): List<Pair<String, String?>> {
        val nameOf = sections.observeAll().first().associate { it.id to it.name }
        return collections.observeAll().first().map { it.collection.name to nameOf[it.collection.sectionId] }
    }

    // Create, rename

    @Test
    fun createsASectionExpandedWithItsNameTrimmed() = runTest {
        val id = section("  Food ")

        assertEquals(Section(id, "Food", collapsed = false), sections.get(id))
    }

    @Test
    fun aBlankNameIsRefused() = runTest {
        assertNull(sections.create("  "))
        assertEquals(emptyList<String>(), names())
    }

    @Test
    fun namesAreUniqueAmongSectionsIgnoringCase() = runTest {
        section("Food")

        assertNull(sections.create("food"))
        assertNull(sections.create(" FOOD "))
        assertEquals(listOf("Food"), names())
    }

    @Test
    fun aSectionAndACollectionMayShareAName() = runTest {
        section("Food")

        collection("Food")

        assertEquals(listOf("Food"), names())
        assertEquals(listOf("Food" to null), sectionOfEach())
    }

    @Test
    fun renamesASection() = runTest {
        val id = section("Food")

        assertTrue(sections.rename(id, " Cooking "))

        assertEquals(listOf("Cooking"), names())
    }

    @Test
    fun renamingToAnotherSectionsNameOrToNothingChangesNothing() = runTest {
        val id = section("Food")
        section("Travel")

        assertFalse(sections.rename(id, "travel"))
        assertFalse(sections.rename(id, " "))

        assertEquals(listOf("Food", "Travel"), names())
    }

    @Test
    fun renamingOnlyTheCaseIsAllowed() = runTest {
        val id = section("food")

        assertTrue(sections.rename(id, "Food"))

        assertEquals(listOf("Food"), names())
    }

    // Listing

    @Test
    fun sectionsAreListedAlphabeticallyIgnoringCase() = runTest {
        listOf("travel", "Food", "Art").forEach { section(it) }

        assertEquals(listOf("Art", "Food", "travel"), names())
    }

    @Test
    fun anEmptySectionStaysListed() = runTest {
        val food = section("Food")
        val pasta = collection("Pasta", food)

        collections.deleteKeepingPosts(pasta)

        assertEquals(listOf("Food"), names())
    }

    // Collapse

    @Test
    fun collapseIsRememberedPerSection() = runTest {
        val food = section("Food")
        val travel = section("Travel")

        sections.setCollapsed(food, true)

        assertEquals(true, sections.get(food)?.collapsed)
        assertEquals(false, sections.get(travel)?.collapsed)
        sections.setCollapsed(food, false)
        assertEquals(false, sections.get(food)?.collapsed)
    }

    // Putting a Collection in a Section

    @Test
    fun aCollectionIsCreatedInASectionOrInNone() = runTest {
        val food = section("Food")

        collection("Pasta", food)
        collection("Japan")

        assertEquals(listOf("Japan" to null, "Pasta" to "Food"), sectionOfEach())
    }

    @Test
    fun aCollectionChangesSectionAndLeavesIt() = runTest {
        val food = section("Food")
        val travel = section("Travel")
        val pasta = checkNotNull(collections.get(collection("Pasta", food)))

        assertTrue(collections.update(pasta.copy(sectionId = travel)))
        assertEquals(listOf("Pasta" to "Travel"), sectionOfEach())

        assertTrue(collections.update(pasta.copy(sectionId = null)))
        assertEquals(listOf("Pasta" to null), sectionOfEach())
    }

    @Test
    fun aSectionDeletedMeanwhileLeavesTheCollectionWithNone() = runTest {
        val food = section("Food")
        val pasta = checkNotNull(collections.get(collection("Pasta")))
        sections.delete(food)

        assertTrue(collections.update(pasta.copy(name = "Pasta!", sectionId = food)))
        collection("Salads", food)

        assertEquals(listOf("Pasta!" to null, "Salads" to null), sectionOfEach())
    }

    // Deleting

    @Test
    fun deletingASectionOnlyTakesItsCollectionsOutOfIt() = runTest {
        val food = section("Food")
        val travel = section("Travel")
        val pasta = collection("Pasta", food)
        collection("Japan", travel)
        val post = posts.insert(Post(shortcode = "A", url = "https://www.instagram.com/p/A/", addedAt = 1L))
        posts.setCollection(post, pasta, at = 1L)

        sections.delete(food)

        assertEquals(listOf("Travel"), names())
        assertEquals(listOf("Japan" to "Travel", "Pasta" to null), sectionOfEach())
        assertEquals(listOf("A"), posts.observeInCollection(pasta).first().map { it.shortcode })
    }

    @Test
    fun aDeletedSectionsNameIsFreeAgain() = runTest {
        sections.delete(section("Food"))

        section("food")

        assertEquals(listOf("food"), names())
    }

    @Test
    fun deletingASectionThatIsGoneChangesNothing() = runTest {
        val food = section("Food")
        sections.delete(food)

        assertNull(sections.delete(food))
    }

    @Test
    fun undoingADeletionBringsTheSectionBackWithItsCollectionsInIt() = runTest {
        val food = section("Food")
        sections.setCollapsed(food, true)
        collection("Pasta", food)
        collection("Salads", food)
        collection("Japan")
        val before = sections.get(food)
        val deletion = checkNotNull(sections.delete(food))

        sections.undoDelete(deletion)

        assertEquals(before, sections.get(food))
        assertEquals(listOf("Japan" to null, "Pasta" to "Food", "Salads" to "Food"), sectionOfEach())
    }

    @Test
    fun undoingTheDeletionOfAnEmptySectionBringsItBack() = runTest {
        val deletion = checkNotNull(sections.delete(section("Food")))

        sections.undoDelete(deletion)

        assertEquals(listOf("Food"), names())
    }

    @Test
    fun undoingADeletionJoinsTheSectionThatTookTheNameMeanwhile() = runTest {
        val food = section("Food")
        collection("Pasta", food)
        val deletion = checkNotNull(sections.delete(food))
        val newer = section("food")

        sections.undoDelete(deletion)

        assertEquals(listOf("food"), names())
        assertEquals(newer, collections.observeAll().first().single().collection.sectionId)
    }

    @Test
    fun undoingADeletionLeavesACollectionPutElsewhereMeanwhileOrDeleted() = runTest {
        val food = section("Food")
        val travel = section("Travel")
        val pasta = checkNotNull(collections.get(collection("Pasta", food)))
        val salads = collection("Salads", food)
        val deletion = checkNotNull(sections.delete(food))
        collections.update(pasta.copy(sectionId = travel))
        collections.deleteKeepingPosts(salads)

        sections.undoDelete(deletion)

        assertEquals(listOf("Food", "Travel"), names())
        assertEquals(listOf("Pasta" to "Travel"), sectionOfEach())
    }

    // A Collection deleted while emptied into another (undo)

    @Test
    fun undoingAMoveThatDeletedTheCollectionBringsItBackInItsSection() = runTest {
        val food = section("Food")
        val pasta = collection("Pasta", food)
        val recipes = collection("Recipes")
        val move = checkNotNull(collections.moveAllPosts(pasta, recipes, at = 2, deleteFrom = true))

        collections.undoMoveAllPosts(move)

        assertEquals(listOf("Pasta" to "Food", "Recipes" to null), sectionOfEach())
    }

    @Test
    fun undoingAMoveThatDeletedTheCollectionBringsItBackWithNoSectionOnceThatIsGone() = runTest {
        val food = section("Food")
        val pasta = collection("Pasta", food)
        val recipes = collection("Recipes")
        val move = checkNotNull(collections.moveAllPosts(pasta, recipes, at = 2, deleteFrom = true))
        sections.delete(food)

        collections.undoMoveAllPosts(move)

        assertEquals(listOf("Pasta" to null, "Recipes" to null), sectionOfEach())
    }
}
