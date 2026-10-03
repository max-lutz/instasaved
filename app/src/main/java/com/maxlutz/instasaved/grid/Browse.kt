package com.maxlutz.instasaved.grid

import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import java.text.Normalizer

/** The orders a view of Posts can be shown in. */
enum class PostSort {
    /** Last added first. */
    Saved,

    /** Last changed by the user first. */
    Modified,

    /** By Title, A to Z; Posts without one come last. */
    Title,
}

/**
 * Posts shown together in a view: those carrying [tag] when the view is grouped by Tag, with a null [tag] for
 * those carrying none; the whole view when it is not grouped.
 */
data class PostGroup(val tag: Tag?, val posts: List<Post>)

/**
 * How the user is looking at a view of Posts.
 *
 * @property query what to search Title, Description and Post Note for; blank shows every Post.
 */
data class Browse(val query: String = "", val sort: PostSort = PostSort.Saved, val groupByTag: Boolean = false) {
    /**
     * The [posts] matching [query], sorted, in the groups to show them in; no group is empty. Grouped by Tag,
     * the groups follow the order of [tags], then comes the one for Posts without a Tag, and a Post is in the
     * group of each of its Tags.
     */
    fun arrange(posts: List<Post>, tags: List<Tag>, postTags: List<PostTag>): List<PostGroup> {
        val found = posts.search(query).sorted(sort)
        val groups = if (groupByTag) {
            val tagIdsOf = postTags.groupBy({ it.postId }, { it.tagId })
            tags.map { tag -> PostGroup(tag, found.filter { tag.id in tagIdsOf[it.id].orEmpty() }) } +
                PostGroup(null, found.filter { it.id !in tagIdsOf })
        } else {
            listOf(PostGroup(null, found))
        }
        return groups.filter { it.posts.isNotEmpty() }
    }
}

/** The Posts with every word of [query] somewhere in their Title, Description or Post Note. */
private fun List<Post>.search(query: String): List<Post> {
    val words = query.folded().split(WHITESPACE).filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    return filter { post ->
        val text = "${post.title}\n${post.description}\n${post.postNote}".folded()
        words.all { it in text }
    }
}

private fun List<Post>.sorted(sort: PostSort): List<Post> = when (sort) {
    PostSort.Saved -> sortedWith(compareByDescending<Post> { it.addedAt }.thenByDescending { it.id })
    PostSort.Modified -> sortedWith(compareByDescending<Post> { it.modifiedAt }.thenByDescending { it.id })
    PostSort.Title -> map { it to it.title.trim().folded() }
        .sortedWith(compareBy({ (_, title) -> title.isEmpty() }, { (_, title) -> title }, { (post, _) -> post.id }))
        .map { (post, _) -> post }
}

/** Without case or accents, so that "creme" finds "Crème". */
private fun String.folded(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()

private val ACCENTS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")
