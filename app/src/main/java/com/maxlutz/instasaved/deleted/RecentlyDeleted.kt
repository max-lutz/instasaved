package com.maxlutz.instasaved.deleted

import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.RecentlyDeletedDao
import com.maxlutz.instasaved.thumbnails.ThumbnailStore
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Ends a Deleted Post's stay in Recently deleted (ADR-0012): after [KEPT_FOR], or at once on "Empty now", only
 * its shortcode trace is left, and its Thumbnail goes with it.
 */
class RecentlyDeleted(
    private val dao: RecentlyDeletedDao,
    private val thumbnails: ThumbnailStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** "Empty now": every Post in Recently deleted. */
    suspend fun empty() = purge(deletedUpTo = Long.MAX_VALUE)

    /** The Posts deleted [KEPT_FOR] ago or longer. */
    suspend fun purgeExpired() = purge(deletedUpTo = now() - KEPT_FOR.inWholeMilliseconds)

    private suspend fun purge(deletedUpTo: Long) = dao.purge(deletedUpTo).forEach(thumbnails::delete)

    companion object {
        val KEPT_FOR = 30.days

        /** How many days, rounded up, the Post still has in Recently deleted. 0 for a Post not in it, or overdue. */
        fun daysLeft(post: Post, now: Long): Int {
            val deletedAt = post.deletedAt ?: return 0
            val left = KEPT_FOR - (now - deletedAt).milliseconds
            if (!left.isPositive()) return 0
            return (left.inWholeDays + if (left > left.inWholeDays.days) 1 else 0).toInt()
        }
    }
}
