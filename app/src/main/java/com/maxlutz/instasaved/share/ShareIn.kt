package com.maxlutz.instasaved.share

import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostDao
import com.maxlutz.instasaved.data.RecentlyDeletedDao

/** Share-in: adds the Post behind a link shared from Instagram to To sort, unless the app already has it. */
class ShareIn(
    private val posts: PostDao,
    private val deleted: RecentlyDeletedDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data class Added(val post: Post) : Result
        data class AlreadySaved(val post: Post) : Result

        /** A Deleted Post, left as it is: only the user can bring it back, with [addBack] (ADR-0012). */
        data class PreviouslyDeleted(val link: PostLink) : Result

        /** A Deleted Post taken back out of Recently deleted by [addBack], whole. */
        data class Restored(val post: Post) : Result
        data object NotAPostLink : Result
    }

    suspend fun receive(sharedText: String): Result {
        val link = PostLink.find(sharedText) ?: return Result.NotAPostLink
        if (deleted.hasTrace(link.shortcode)) return Result.PreviouslyDeleted(link)
        val id = insert(link)
        val post = checkNotNull(posts.get(link.shortcode))
        return when {
            id != -1L -> Result.Added(post)
            post.deletedAt != null -> Result.PreviouslyDeleted(link)
            else -> Result.AlreadySaved(post)
        }
    }

    /**
     * The user's yes to "Add it back?": restores the Post if it is still in Recently deleted; if only its trace
     * is left, adds a fresh Post to To sort.
     */
    suspend fun addBack(link: PostLink): Result {
        val existing = posts.get(link.shortcode)
        if (existing != null) {
            deleted.restore(existing.id)
            return Result.Restored(checkNotNull(posts.get(link.shortcode)))
        }
        deleted.forgetTrace(link.shortcode)
        insert(link)
        return Result.Added(checkNotNull(posts.get(link.shortcode)))
    }

    // Never seen in an Export: only Sync can say that (sync-spec R3a).
    private suspend fun insert(link: PostLink): Long =
        posts.insert(Post(shortcode = link.shortcode, url = link.url, addedAt = now(), seenInExport = false))
}
