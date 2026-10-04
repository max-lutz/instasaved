package com.maxlutz.instasaved.tags

import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.grid.folded

/** The Tags the picker offers: those already used in the Post's Collection, then every other one. */
data class TagChoices(val inCollection: List<Tag>, val others: List<Tag>) {
    val isEmpty get() = inCollection.isEmpty() && others.isEmpty()
}

/**
 * The [tags] whose name contains [query], ignoring case and accents, kept in their order: those of [inCollection]
 * apart from the others. A blank [query] keeps every Tag.
 *
 * @param inCollection the ids of the Tags used in the Post's Collection, see [tagIdsInCollectionOf].
 */
fun tagChoices(tags: List<Tag>, query: String, inCollection: Set<Long> = emptySet()): TagChoices {
    val text = query.trim().folded()
    val (first, others) = tags.filter { text in it.name.folded() }.partition { it.id in inCollection }
    return TagChoices(first, others)
}

/**
 * The ids of the Tags carried by at least one other Post of [post]'s Collection; none for a Post in To sort.
 * [post]'s own Tags don't count, so that tagging it does not reorder the picker.
 */
fun tagIdsInCollectionOf(post: Post, posts: List<Post>, postTags: List<PostTag>): Set<Long> {
    val collectionId = post.collectionId ?: return emptySet()
    val others = posts.filter { it.collectionId == collectionId && it.id != post.id }.map { it.id }.toSet()
    return postTags.filter { it.postId in others }.map { it.tagId }.toSet()
}
