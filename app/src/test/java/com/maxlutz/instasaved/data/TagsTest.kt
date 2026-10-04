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
class TagsTest {
    private lateinit var db: AppDatabase
    private val tags get() = db.tagDao()
    private val posts get() = db.postDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addPost(shortcode: String): Long =
        posts.insert(Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = 1L))

    private suspend fun create(name: String, color: Int = PALETTE[0]) = checkNotNull(tags.create(name, color))

    private suspend fun names() = tags.observeAll().first().map { it.tag.name }
    private suspend fun tagsOf(postId: Long) = tags.observeOnPost(postId).first().map { it.name }

    // Create, rename, recolor

    @Test
    fun createsATagWithItsColor() = runTest {
        val id = create("Vegan", PALETTE[4])

        assertEquals(Tag(id, "Vegan", PALETTE[4]), tags.get(id))
    }

    @Test
    fun namesAreTrimmedAndBlankOnesRefused() = runTest {
        val id = create("  Vegan ")

        assertEquals("Vegan", tags.get(id)?.name)
        assertNull(tags.create(" ", PALETTE[0]))
        assertEquals(listOf("Vegan"), names())
    }

    @Test
    fun namesAreUniqueIgnoringCase() = runTest {
        create("Vegan")

        assertNull(tags.create(" VEGAN ", PALETTE[1]))
        assertEquals(listOf("Vegan"), names())
    }

    @Test
    fun aTagMayShareItsNameWithACollection() = runTest {
        db.collectionDao().create("Recipes", PALETTE[0])

        create("Recipes")
    }

    @Test
    fun renamesAndRecolors() = runTest {
        val id = create("Vegan", PALETTE[0])

        assertTrue(tags.update(Tag(id, " Plant-based ", PALETTE[7])))
        assertEquals(Tag(id, "Plant-based", PALETTE[7]), tags.get(id))
    }

    @Test
    fun renamingToAnotherTagsNameOrBlankChangesNothing() = runTest {
        create("Vegan")
        val quick = create("Quick", PALETTE[1])

        assertFalse(tags.update(Tag(quick, "vegan", PALETTE[5])))
        assertFalse(tags.update(Tag(quick, " ", PALETTE[5])))
        assertEquals(Tag(quick, "Quick", PALETTE[1]), tags.get(quick))
    }

    @Test
    fun tagsAreListedAlphabeticallyIgnoringCaseWithTheirPostCounts() = runTest {
        val vegan = create("vegan")
        create("Asian")
        val post = addPost("A")
        tags.addToPost(post, vegan, at = 1L)

        assertEquals(
            listOf("Asian" to 0, "vegan" to 1),
            tags.observeAll().first().map { it.tag.name to it.postCount },
        )
    }

    @Test
    fun postCountsLeaveOutRecentlyDeletedPosts() = runTest {
        val vegan = create("Vegan")
        val kept = addPost("A")
        val deleted = addPost("B")
        tags.addToPost(kept, vegan, at = 1L)
        tags.addToPost(deleted, vegan, at = 1L)
        posts.delete(deleted, at = 5L)

        assertEquals(1, tags.observeAll().first().single().postCount)
    }

    // Tagging Posts

    @Test
    fun tagsAPostAndListsItsTagsAlphabetically() = runTest {
        val vegan = create("Vegan")
        val asian = create("Asian")
        val post = addPost("A")

        assertTrue(tags.addToPost(post, vegan, at = 1L))
        assertTrue(tags.addToPost(post, asian, at = 1L))

        assertEquals(listOf("Asian", "Vegan"), tagsOf(post))
    }

    @Test
    fun aTagCanBeOnManyPosts() = runTest {
        val vegan = create("Vegan")
        val a = addPost("A")
        val b = addPost("B")

        tags.addToPost(a, vegan, at = 1L)
        tags.addToPost(b, vegan, at = 1L)

        assertEquals(listOf("Vegan"), tagsOf(a))
        assertEquals(listOf("Vegan"), tagsOf(b))
    }

    @Test
    fun addingATagThePostAlreadyHasIsANoOp() = runTest {
        val vegan = create("Vegan")
        val post = addPost("A")
        tags.addToPost(post, vegan, at = 1L)

        assertTrue(tags.addToPost(post, vegan, at = 1L))
        assertEquals(listOf("Vegan"), tagsOf(post))
    }

    @Test
    fun aFifthTagIsRefused() = runTest {
        val post = addPost("A")
        val four = listOf("A", "B", "C", "D").map { create(it) }
        four.forEach { assertTrue(tags.addToPost(post, it, at = 1L)) }

        assertFalse(tags.addToPost(post, create("E"), at = 1L))
        assertEquals(listOf("A", "B", "C", "D"), tagsOf(post))
    }

    @Test
    fun aTagAlreadyOnAFullPostIsStillANoOp() = runTest {
        val post = addPost("A")
        val four = listOf("A", "B", "C", "D").map { create(it) }
        four.forEach { tags.addToPost(post, it, at = 1L) }

        assertTrue(tags.addToPost(post, four[0], at = 1L))
    }

    @Test
    fun removingATagFreesASlot() = runTest {
        val post = addPost("A")
        val four = listOf("A", "B", "C", "D").map { create(it) }
        four.forEach { tags.addToPost(post, it, at = 1L) }

        tags.removeFromPost(post, four[1], at = 1L)

        assertEquals(listOf("A", "C", "D"), tagsOf(post))
        assertTrue(tags.addToPost(post, create("E"), at = 1L))
    }

    @Test
    fun addingOrRemovingATagSetsThePostsModifiedDate() = runTest {
        val vegan = create("Vegan")
        val post = addPost("A")

        tags.addToPost(post, vegan, at = 5L)
        val tagged = posts.get("A")?.modifiedAt
        tags.removeFromPost(post, vegan, at = 8L)
        val untagged = posts.get("A")?.modifiedAt

        assertEquals(listOf(5L, 8L), listOf(tagged, untagged))
    }

    @Test
    fun aTagChangingNothingLeavesTheModifiedDateAlone() = runTest {
        val post = addPost("A")
        val four = listOf("A", "B", "C", "D").map { create(it) }
        four.forEach { tags.addToPost(post, it, at = 5L) }

        tags.addToPost(post, four[0], at = 6L)
        tags.addToPost(post, create("E"), at = 7L)
        tags.removeFromPost(post, create("F"), at = 8L)

        assertEquals(5L, posts.get("A")?.modifiedAt)
    }

    @Test
    fun putsATagOnSomePostsAndTakesItOffOthersAtOnce() = runTest {
        val vegan = create("Vegan")
        val a = addPost("A")
        val b = addPost("B")
        val c = addPost("C")
        tags.addToPost(c, vegan, at = 1L)

        tags.setOnPosts(vegan, addTo = listOf(a, b), removeFrom = listOf(c), at = 5L)

        assertEquals(listOf(listOf("Vegan"), listOf("Vegan"), emptyList()), listOf(tagsOf(a), tagsOf(b), tagsOf(c)))
        assertEquals(listOf(5L, 5L, 5L), listOf("A", "B", "C").map { posts.get(it)?.modifiedAt })
    }

    @Test
    fun aTagPutOnSeveralPostsStillSkipsAFullOne() = runTest {
        val a = addPost("A")
        val full = addPost("B")
        listOf("A", "B", "C", "D").forEach { tags.addToPost(full, create(it), at = 1L) }
        val vegan = create("Vegan")

        tags.setOnPosts(vegan, addTo = listOf(a, full), removeFrom = emptyList(), at = 5L)

        assertEquals(listOf("Vegan"), tagsOf(a))
        assertEquals(listOf("A", "B", "C", "D"), tagsOf(full))
    }

    @Test
    fun listsWhichTagsAreOnWhichPosts() = runTest {
        val vegan = create("Vegan")
        val quick = create("Quick")
        val a = addPost("A")
        val b = addPost("B")
        tags.addToPost(a, vegan, at = 1L)
        tags.addToPost(a, quick, at = 1L)
        tags.addToPost(b, vegan, at = 1L)

        assertEquals(
            setOf(PostTag(a, vegan), PostTag(a, quick), PostTag(b, vegan)),
            tags.observePostTags().first().toSet(),
        )
    }

    @Test
    fun renamingATagShowsOnItsPosts() = runTest {
        val vegan = create("Vegan")
        val post = addPost("A")
        tags.addToPost(post, vegan, at = 1L)

        tags.update(Tag(vegan, "Plant-based", PALETTE[0]))

        assertEquals(listOf("Plant-based"), tagsOf(post))
    }

    @Test
    fun tagsAreIndependentOfTheCollection() = runTest {
        val vegan = create("Vegan")
        val post = addPost("A")
        tags.addToPost(post, vegan, at = 1L)
        val recipes = checkNotNull(db.collectionDao().create("Recipes", PALETTE[0]))

        posts.setCollection(post, recipes, at = 1L)
        db.collectionDao().deleteKeepingPosts(recipes)

        assertEquals(listOf("Vegan"), tagsOf(post))
    }

    @Test
    fun aRecentlyDeletedPostKeepsItsTags() = runTest {
        val vegan = create("Vegan")
        val post = addPost("A")
        tags.addToPost(post, vegan, at = 1L)

        posts.delete(post, at = 5L)
        db.recentlyDeletedDao().restore(post)

        assertEquals(listOf("Vegan"), tagsOf(post))
    }
}
