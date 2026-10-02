package com.maxlutz.instasaved.share

import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostDao

/** Share-in: adds the Post behind a link shared from Instagram to To sort, unless the app already has it. */
class ShareIn(
    private val posts: PostDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data class Added(val post: Post) : Result
        data class AlreadySaved(val post: Post) : Result

        /** A Deleted Post. Asking "Add it back?" belongs to Recently deleted (ADR-0012); until then it is only reported. */
        data class PreviouslyDeleted(val post: Post) : Result
        data object NotAPostLink : Result
    }

    suspend fun receive(sharedText: String): Result {
        val link = PostLink.find(sharedText) ?: return Result.NotAPostLink
        // Never seen in an Export: only Sync can say that (sync-spec R3a).
        val id = posts.insert(Post(shortcode = link.shortcode, url = link.url, addedAt = now(), seenInExport = false))
        val post = checkNotNull(posts.get(link.shortcode))
        return when {
            id != -1L -> Result.Added(post)
            post.deletedAt != null -> Result.PreviouslyDeleted(post)
            else -> Result.AlreadySaved(post)
        }
    }
}
