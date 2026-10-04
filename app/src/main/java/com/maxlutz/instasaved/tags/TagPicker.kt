package com.maxlutz.instasaved.tags

import com.maxlutz.instasaved.data.MAX_TAGS_PER_POST
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
 * The ids of the Tags carried by at least one other Post of the Collection the [picked] Posts are all in; none
 * when they are not all in one Collection, as in To sort. The Tags of the [picked] Posts don't count, so that
 * tagging them does not reorder the picker.
 */
fun tagIdsInCollectionOf(picked: List<Post>, posts: List<Post>, postTags: List<PostTag>): Set<Long> {
    val collectionId = picked.map { it.collectionId }.distinct().singleOrNull() ?: return emptySet()
    val pickedIds = picked.mapTo(HashSet()) { it.id }
    val others = posts.filter { it.collectionId == collectionId && it.id !in pickedIds }.mapTo(HashSet()) { it.id }
    return postTags.filter { it.postId in others }.mapTo(HashSet()) { it.tagId }
}

/** How many of the Posts tagged together carry a Tag. */
enum class Carried { All, Some, None }

/**
 * What a tap on a Tag does to the Posts tagged together.
 *
 * @property addTo the ids of the Posts to put the Tag on.
 * @property removeFrom the ids of the Posts to take it off.
 * @property full the ids of the Posts left without the Tag, as they already carry [MAX_TAGS_PER_POST].
 */
data class TagChange(
    val addTo: List<Long> = emptyList(),
    val removeFrom: List<Long> = emptyList(),
    val full: List<Long> = emptyList(),
)

/**
 * The Tags of the Posts tagged together, one Post or several, and the bulk Tag rule: a tap on a Tag takes it off
 * every Post when all carry it, else puts it on those that lack it and have room for one more.
 *
 * @param postIds the Posts tagged together.
 * @param postTags which Posts carry which Tags; those of other Posts are ignored.
 */
class Tagging(postIds: Collection<Long>, postTags: List<PostTag>) {
    private val tagIdsOf: Map<Long, Set<Long>> = postTags.groupBy({ it.postId }, { it.tagId }).let { byPost ->
        postIds.associateWith { byPost[it].orEmpty().toSet() }
    }

    /** Whether no Post has room for one more Tag. */
    val full: Boolean = tagIdsOf.values.all { it.size >= MAX_TAGS_PER_POST }

    /** How many Posts are tagged together. */
    val postCount: Int get() = tagIdsOf.size

    /** How many of the Posts carry the Tag. */
    fun countCarrying(tagId: Long): Int = tagIdsOf.values.count { tagId in it }

    fun carried(tagId: Long): Carried = when (countCarrying(tagId)) {
        0 -> Carried.None
        postCount -> Carried.All
        else -> Carried.Some
    }

    fun tap(tagId: Long): TagChange {
        if (carried(tagId) == Carried.All) return TagChange(removeFrom = tagIdsOf.keys.toList())
        val (full, withRoom) = tagIdsOf.filterValues { tagId !in it }.entries
            .partition { it.value.size >= MAX_TAGS_PER_POST }
        return TagChange(addTo = withRoom.map { it.key }, full = full.map { it.key })
    }

    /** Whether a tap on the Tag changes any Post. */
    fun canTap(tagId: Long): Boolean = tap(tagId).let { it.addTo.isNotEmpty() || it.removeFrom.isNotEmpty() }
}
