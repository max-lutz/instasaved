package com.maxlutz.instasaved.tags

import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPickerTest {
    private fun tag(id: Long, name: String) = Tag(id, name, PALETTE[0])

    private fun post(id: Long, collectionId: Long?) = Post(
        id = id,
        shortcode = "P$id",
        url = "https://www.instagram.com/p/P$id/",
        addedAt = id,
        collectionId = collectionId,
    )

    private val creme = tag(1, "Crème")
    private val quick = tag(2, "Quick")
    private val vegan = tag(3, "Vegan")
    private val weekend = tag(4, "Weekend")
    private val tags = listOf(creme, quick, vegan, weekend)

    // Search

    @Test
    fun aBlankQueryKeepsEveryTagInOrder() {
        assertEquals(TagChoices(emptyList(), tags), tagChoices(tags, query = "  "))
    }

    @Test
    fun keepsTheTagsWhoseNameContainsTheQuery() {
        assertEquals(listOf(quick, weekend), tagChoices(tags, query = "k").others)
        assertEquals(listOf(vegan), tagChoices(tags, query = "ega").others)
    }

    @Test
    fun searchIgnoresCaseAccentsAndSurroundingSpaces() {
        assertEquals(listOf(creme), tagChoices(tags, query = " CREME ").others)
        assertEquals(listOf(vegan), tagChoices(tags, query = "végan").others)
    }

    @Test
    fun aQueryMatchingNoTagLeavesNoChoice() {
        assertTrue(tagChoices(tags, query = "pasta", inCollection = setOf(2)).isEmpty)
        assertFalse(tagChoices(tags, query = "quick").isEmpty)
    }

    // Collection Tags first

    @Test
    fun theCollectionsTagsComeApartBothInOrder() {
        assertEquals(
            TagChoices(inCollection = listOf(creme, weekend), others = listOf(quick, vegan)),
            tagChoices(tags, query = "", inCollection = setOf(4, 1)),
        )
    }

    @Test
    fun searchNarrowsBothTheCollectionsTagsAndTheOthers() {
        assertEquals(
            TagChoices(inCollection = listOf(weekend), others = listOf(quick)),
            tagChoices(tags, query = "k", inCollection = setOf(4, 1)),
        )
    }

    @Test
    fun theCollectionsTagsAreThoseOfItsOtherPosts() {
        val opened = post(1, collectionId = 10)
        val posts = listOf(opened, post(2, collectionId = 10), post(3, collectionId = 10), post(4, collectionId = 20))
        val postTags = listOf(PostTag(2, 1), PostTag(3, 1), PostTag(3, 2), PostTag(4, 3))

        assertEquals(setOf(1L, 2L), tagIdsInCollectionOf(listOf(opened), posts, postTags))
    }

    @Test
    fun thePostsOwnTagsDoNotCount() {
        val opened = post(1, collectionId = 10)
        val posts = listOf(opened, post(2, collectionId = 10))
        val postTags = listOf(PostTag(1, 1), PostTag(1, 2), PostTag(2, 2))

        assertEquals(setOf(2L), tagIdsInCollectionOf(listOf(opened), posts, postTags))
    }

    @Test
    fun aPostInToSortHasNoCollectionTags() {
        val opened = post(1, collectionId = null)
        val posts = listOf(opened, post(2, collectionId = null))

        assertEquals(emptySet<Long>(), tagIdsInCollectionOf(listOf(opened), posts, listOf(PostTag(2, 1))))
    }

    @Test
    fun severalPostsOfOneCollectionGetItsOtherPostsTags() {
        val picked = listOf(post(1, collectionId = 10), post(2, collectionId = 10))
        val posts = picked + post(3, collectionId = 10) + post(4, collectionId = 20)
        val postTags = listOf(PostTag(1, 1), PostTag(3, 2), PostTag(4, 3))

        assertEquals(setOf(2L), tagIdsInCollectionOf(picked, posts, postTags))
    }

    @Test
    fun postsOfSeveralCollectionsHaveNoCollectionTags() {
        val picked = listOf(post(1, collectionId = 10), post(2, collectionId = 20))
        val posts = picked + post(3, collectionId = 10) + post(4, collectionId = 20)
        val postTags = listOf(PostTag(3, 1), PostTag(4, 2))

        assertEquals(emptySet<Long>(), tagIdsInCollectionOf(picked, posts, postTags))
    }

    @Test
    fun aPostInToSortAmongThePickedLeavesNoCollectionTags() {
        val picked = listOf(post(1, collectionId = 10), post(2, collectionId = null))
        val posts = picked + post(3, collectionId = 10)

        assertEquals(emptySet<Long>(), tagIdsInCollectionOf(picked, posts, listOf(PostTag(3, 1))))
    }

    // Tagging several Posts together

    /** Posts 1 to 3; Tag 1 is on all, Tag 2 on Posts 1 and 2, Tag 3 on none. */
    private val three = Tagging(
        postIds = listOf(1, 2, 3),
        postTags = listOf(PostTag(1, 1), PostTag(2, 1), PostTag(3, 1), PostTag(1, 2), PostTag(2, 2)),
    )

    @Test
    fun aTagIsCarriedByAllSomeOrNoneOfThePosts() {
        assertEquals(
            listOf(Carried.All, Carried.Some, Carried.None),
            listOf(three.carried(1), three.carried(2), three.carried(3)),
        )
        assertEquals(listOf(3, 2, 0), listOf(three.countCarrying(1), three.countCarrying(2), three.countCarrying(3)))
        assertEquals(3, three.postCount)
    }

    @Test
    fun otherPostsTagsDoNotCount() {
        val tagging = Tagging(postIds = listOf(1), postTags = listOf(PostTag(1, 1), PostTag(2, 1), PostTag(2, 2)))

        assertEquals(listOf(Carried.All, Carried.None), listOf(tagging.carried(1), tagging.carried(2)))
    }

    @Test
    fun tappingATagCarriedByAllRemovesItFromAll() {
        assertEquals(TagChange(removeFrom = listOf(1, 2, 3)), three.tap(1))
    }

    @Test
    fun tappingATagCarriedBySomeAddsItToTheOthers() {
        assertEquals(TagChange(addTo = listOf(3)), three.tap(2))
    }

    @Test
    fun tappingATagCarriedByNoneAddsItToAll() {
        assertEquals(TagChange(addTo = listOf(1, 2, 3)), three.tap(3))
    }

    @Test
    fun addingSkipsThePostsThatAlreadyCarryFourTags() {
        val fullPost = (1L..4L).map { PostTag(2, it) }
        val tagging = Tagging(postIds = listOf(1, 2, 3), postTags = fullPost + PostTag(3, 1))

        assertEquals(TagChange(addTo = listOf(1, 3), full = listOf(2)), tagging.tap(9))
    }

    @Test
    fun aFullPostThatAlreadyCarriesTheTagIsNotSkipped() {
        val fullPost = (1L..4L).map { PostTag(2, it) }
        val tagging = Tagging(postIds = listOf(1, 2), postTags = fullPost)

        assertEquals(TagChange(addTo = listOf(1)), tagging.tap(4))
    }

    @Test
    fun aTagThatFitsOnNoPostCannotBeTapped() {
        val tagging = Tagging(postIds = listOf(1, 2), postTags = (1L..4L).flatMap { listOf(PostTag(1, it), PostTag(2, it)) })

        assertFalse(tagging.canTap(9))
        assertTrue(tagging.canTap(1))
        assertTrue(tagging.full)
    }

    @Test
    fun thePostsAreNotFullWhileOneHasRoom() {
        val tagging = Tagging(postIds = listOf(1, 2), postTags = (1L..4L).map { PostTag(1, it) })

        assertTrue(tagging.canTap(9))
        assertFalse(tagging.full)
    }
}
