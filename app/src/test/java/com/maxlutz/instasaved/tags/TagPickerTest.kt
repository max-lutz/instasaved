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

        assertEquals(setOf(1L, 2L), tagIdsInCollectionOf(opened, posts, postTags))
    }

    @Test
    fun thePostsOwnTagsDoNotCount() {
        val opened = post(1, collectionId = 10)
        val posts = listOf(opened, post(2, collectionId = 10))
        val postTags = listOf(PostTag(1, 1), PostTag(1, 2), PostTag(2, 2))

        assertEquals(setOf(2L), tagIdsInCollectionOf(opened, posts, postTags))
    }

    @Test
    fun aPostInToSortHasNoCollectionTags() {
        val opened = post(1, collectionId = null)
        val posts = listOf(opened, post(2, collectionId = null))

        assertEquals(emptySet<Long>(), tagIdsInCollectionOf(opened, posts, listOf(PostTag(2, 1))))
    }
}
