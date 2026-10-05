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
 * Posts shown together in a view: those carrying exactly [tags] (alphabetically) when the view is grouped by Tag,
 * with no [tags] for those carrying none; the whole view when it is not grouped.
 */
data class PostGroup(val tags: List<Tag>, val posts: List<Post>)

/**
 * How the user is looking at a view of Posts.
 *
 * @property query what to search Title, Description and Post Note for; blank shows every Post.
 * @property tagId the Tag the Posts shown must carry; null shows them whatever their Tags.
 */
data class Browse(
    val query: String = "",
    val sort: PostSort = PostSort.Saved,
    val groupByTag: Boolean = false,
    val tagId: Long? = null,
) {
    /**
     * The [posts] carrying [tagId] and matching [query], sorted, in the groups to show them in; no group is empty.
     * Grouped by Tag, a group is an exact combination of Tags and a Post is only in the group of all its Tags: the
     * groups follow the order of [tags], Tag after Tag ("Quick", "Quick · Vegan", "Vegan"), then comes the one for
     * Posts without a Tag.
     */
    fun arrange(posts: List<Post>, tags: List<Tag>, postTags: List<PostTag>): List<PostGroup> {
        val tagIdsOf = postTags.groupBy({ it.postId }, { it.tagId })
        val found = posts.filter { tagId == null || tagId in tagIdsOf[it.id].orEmpty() }.search(query).sorted(sort)
        if (found.isEmpty()) return emptyList()
        if (!groupByTag) return listOf(PostGroup(emptyList(), found))
        // A combination is the places of its Tags in [tags], which orders both its Tags and the groups.
        val placeOf = tags.withIndex().associate { (place, tag) -> tag.id to place }
        return found.groupBy { post -> tagIdsOf[post.id].orEmpty().mapNotNull { placeOf[it] }.distinct().sorted() }
            .toList()
            .sortedWith { (a, _), (b, _) -> compareCombinations(a, b) }
            .map { (places, group) -> PostGroup(places.map { tags[it] }, group) }
    }
}

/** Orders two combinations of Tags by their Tags in turn, a shorter one before those it starts; none comes last. */
private fun compareCombinations(a: List<Int>, b: List<Int>): Int {
    if (a.isEmpty() || b.isEmpty()) return compareValues(a.isEmpty(), b.isEmpty())
    for (i in 0 until minOf(a.size, b.size)) {
        if (a[i] != b[i]) return a[i].compareTo(b[i])
    }
    return a.size.compareTo(b.size)
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
internal fun String.folded(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()

private val ACCENTS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")
