package com.maxlutz.instasaved.grid

import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseTest {
    private fun post(
        id: Long,
        title: String = "",
        description: String = "",
        postNote: String = "",
        addedAt: Long = id,
        modifiedAt: Long = addedAt,
    ) = Post(
        id = id,
        shortcode = "P$id",
        url = "https://www.instagram.com/p/P$id/",
        addedAt = addedAt,
        modifiedAt = modifiedAt,
        title = title,
        description = description,
        postNote = postNote,
    )

    /** The ids shown, in order, when the view is not grouped. */
    private fun Browse.ids(posts: List<Post>): List<Long> =
        arrange(posts, tags = emptyList(), postTags = emptyList()).flatMap { it.posts }.map { it.id }

    // Search

    @Test
    fun aBlankQueryShowsEveryPost() {
        val posts = listOf(post(2, title = "Ramen"), post(1))

        assertEquals(listOf(2L, 1L), Browse(query = "  ").ids(posts))
    }

    @Test
    fun searchesTitleDescriptionAndPostNote() {
        val posts = listOf(
            post(4, title = "Ramen at home"),
            post(3, description = "The best ramen in Tokyo"),
            post(2, postNote = "Try the ramen place"),
            post(1, title = "Carbonara"),
        )

        assertEquals(listOf(4L, 3L, 2L), Browse(query = "ramen").ids(posts))
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        val posts = listOf(post(2, title = "Crème brûlée"), post(1, title = "CREME caramel"))

        assertEquals(listOf(2L, 1L), Browse(query = "creme").ids(posts))
        assertEquals(listOf(2L), Browse(query = "BRÛLÉE").ids(posts))
    }

    @Test
    fun everyWordMustMatchInAnyOfTheThreeTexts() {
        val posts = listOf(
            post(3, title = "Ramen", postNote = "Tokyo trip"),
            post(2, title = "Tokyo tower"),
            post(1, description = "Ramen"),
        )

        assertEquals(listOf(3L), Browse(query = " tokyo  ramen ").ids(posts))
    }

    @Test
    fun wordsMatchInsideLongerWords() {
        assertEquals(listOf(1L), Browse(query = "carbo").ids(listOf(post(1, title = "Carbonara"))))
    }

    @Test
    fun searchDoesNotLookAtTheShortcodeOrOwner() {
        val posts = listOf(post(1).copy(ownerName = "Pasta Grannies"))

        assertEquals(emptyList<Long>(), Browse(query = "pasta").ids(posts))
        assertEquals(emptyList<Long>(), Browse(query = "P1").ids(posts))
    }

    // Sort

    @Test
    fun sortsBySavedDateNewestFirst() {
        val posts = listOf(post(1, addedAt = 5), post(2, addedAt = 9), post(3, addedAt = 5))

        assertEquals(listOf(2L, 3L, 1L), Browse(sort = PostSort.Saved).ids(posts))
    }

    @Test
    fun sortsByModifiedDateLastChangedFirst() {
        val posts = listOf(post(1, modifiedAt = 30), post(2, modifiedAt = 10), post(3, modifiedAt = 20))

        assertEquals(listOf(1L, 3L, 2L), Browse(sort = PostSort.Modified).ids(posts))
    }

    @Test
    fun sortsByTitleIgnoringCaseAndAccentsWithUntitledPostsLast() {
        val posts = listOf(
            post(1, title = "zucchini"),
            post(2),
            post(3, title = "Éclair"),
            post(4, title = "Dumplings"),
            post(5, title = "falafel"),
        )

        assertEquals(listOf(4L, 3L, 5L, 1L, 2L), Browse(sort = PostSort.Title).ids(posts))
    }

    // Group by Tag

    private val quick = Tag(1, "Quick", PALETTE[0])
    private val vegan = Tag(2, "Vegan", PALETTE[1])
    private val unused = Tag(3, "Winter", PALETTE[2])
    private val tags = listOf(quick, vegan, unused)

    private fun List<PostGroup>.idsByTag() = map { it.tag?.name to it.posts.map { post -> post.id } }

    @Test
    fun notGroupedTheViewIsOneGroup() {
        val groups = Browse().arrange(listOf(post(1), post(2)), tags, listOf(PostTag(1, vegan.id)))

        assertEquals(listOf(null to listOf(2L, 1L)), groups.idsByTag())
    }

    @Test
    fun groupsByTagInTagOrderThenThePostsWithoutATag() {
        val posts = listOf(post(1), post(2), post(3))
        val postTags = listOf(PostTag(1, vegan.id), PostTag(3, quick.id))

        val groups = Browse(groupByTag = true).arrange(posts, tags, postTags)

        assertEquals(listOf("Quick" to listOf(3L), "Vegan" to listOf(1L), null to listOf(2L)), groups.idsByTag())
    }

    @Test
    fun aPostIsInTheGroupOfEachOfItsTags() {
        val postTags = listOf(PostTag(1, vegan.id), PostTag(1, quick.id))

        val groups = Browse(groupByTag = true).arrange(listOf(post(1)), tags, postTags)

        assertEquals(listOf("Quick" to listOf(1L), "Vegan" to listOf(1L)), groups.idsByTag())
    }

    @Test
    fun groupsAreSearchedAndSorted() {
        val posts = listOf(post(1, title = "Ramen"), post(2, title = "Dal"), post(3, title = "Miso ramen"), post(4, title = "Ramen eggs"))
        val postTags = listOf(PostTag(1, vegan.id), PostTag(2, vegan.id), PostTag(3, vegan.id))

        val groups = Browse(query = "ramen", sort = PostSort.Title, groupByTag = true).arrange(posts, tags, postTags)

        assertEquals(listOf("Vegan" to listOf(3L, 1L), null to listOf(4L)), groups.idsByTag())
    }

    @Test
    fun nothingFoundLeavesNoGroup() {
        val posts = listOf(post(1, title = "Ramen"))

        assertEquals(emptyList<PostGroup>(), Browse(query = "pasta").arrange(posts, tags, emptyList()))
        assertEquals(emptyList<PostGroup>(), Browse(query = "pasta", groupByTag = true).arrange(posts, tags, emptyList()))
    }
}
