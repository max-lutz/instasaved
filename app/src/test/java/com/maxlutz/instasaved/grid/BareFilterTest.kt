package com.maxlutz.instasaved.grid

import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BareFilterTest {
    private fun post(id: Long, postNote: String = "") =
        Post(id = id, shortcode = "P$id", url = "https://www.instagram.com/p/P$id/", addedAt = id, postNote = postNote)

    /** Posts 1 and 3 are Bare Posts; 2 has a Post Note and 4 a Tag. */
    private val posts = listOf(post(4), post(3), post(2, postNote = "Try it"), post(1))
    private val postTags = listOf(PostTag(4, 1))

    private fun filter(
        posts: List<Post> = this.posts,
        postTags: List<PostTag> = this.postTags,
        offered: Boolean = true,
        on: Boolean = false,
    ) = BareFilter(posts, postTags, offered, on)

    private fun BareFilter.ids() = shown.map { it.id }

    @Test
    fun countsThePostsWithNoTagAndNoPostNote() {
        assertEquals(2, filter().count)
    }

    @Test
    fun aPostNoteOfOnlySpacesAndLineBreaksIsNone() {
        assertEquals(1, filter(listOf(post(1, postNote = " \t\r\n "), post(2, postNote = " x ")), emptyList()).count)
    }

    @Test
    fun aTagOnAnotherPostLeavesAPostBare() {
        assertEquals(1, filter(listOf(post(1)), listOf(PostTag(2, 1))).count)
    }

    @Test
    fun aPostInRecentlyDeletedIsNotCounted() {
        assertEquals(0, filter(listOf(post(1).copy(deletedAt = 9L)), emptyList()).count)
    }

    @Test
    fun offShowsTheWholeView() {
        val filter = filter()

        assertFalse(filter.active)
        assertEquals(listOf(4L, 3L, 2L, 1L), filter.ids())
    }

    @Test
    fun onShowsOnlyTheBarePostsInTheOrderOfTheView() {
        val filter = filter(on = true)

        assertTrue(filter.active)
        assertEquals(listOf(3L, 1L), filter.ids())
    }

    @Test
    fun aPostLeavesTheFilteredViewOnceItGetsATagOrAPostNote() {
        assertEquals(listOf(1L), filter(postTags = postTags + PostTag(3, 1), on = true).ids())
        assertEquals(
            listOf(3L),
            filter(posts = posts.map { if (it.id == 1L) it.copy(postNote = "Sunday") else it }, on = true).ids(),
        )
    }

    @Test
    fun withNoBarePostLeftTheFilterIsOffAndTheWholeViewIsShown() {
        val filter = filter(postTags = postTags + PostTag(3, 1) + PostTag(1, 1), on = true)

        assertEquals(0, filter.count)
        assertFalse(filter.active)
        assertEquals(listOf(4L, 3L, 2L, 1L), filter.ids())
    }

    @Test
    fun notOfferedThereIsNothingToCountOrFilter() {
        val filter = filter(offered = false, on = true)

        assertEquals(0, filter.count)
        assertFalse(filter.active)
        assertEquals(listOf(4L, 3L, 2L, 1L), filter.ids())
    }
}
