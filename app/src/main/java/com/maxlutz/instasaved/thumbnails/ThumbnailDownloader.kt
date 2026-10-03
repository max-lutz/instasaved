package com.maxlutz.instasaved.thumbnails

import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Downloads the Thumbnail of every Post that lacks one. Best-effort (ADR-0008): a failed download is
 * retried slowly, [retryDelay] after its last failure, so a post that is gone from Instagram costs a
 * request a week at most.
 *
 * @param fetch the image bytes for a shortcode, or null if it could not be had.
 * @param pause waits between two downloads, to stay gentle on Instagram.
 */
class ThumbnailDownloader(
    private val posts: PostDao,
    private val store: ThumbnailStore,
    private val fetch: suspend (shortcode: String) -> ByteArray?,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend () -> Unit = { delay(500) },
) {
    // One run at a time, so two runs never download the same Thumbnail.
    private val running = Mutex()

    /** Tries every Post without a Thumbnail that is due: never tried, or failed long enough ago. */
    suspend fun downloadMissing() = running.withLock {
        var failuresInARow = 0
        for (post in posts.getAllFewestThumbnailFailuresFirst()) {
            if (store.has(post.shortcode) || !post.isDue()) continue
            val image = fetch(post.shortcode)
            if (image != null) {
                store.save(post.shortcode, image)
                posts.thumbnailSaved(post.id)
                failuresInARow = 0
            } else {
                posts.thumbnailFailed(post.id, at = now())
                // Offline, throttled, or the endpoints broke: leave the rest untried for a later run.
                if (++failuresInARow == GIVE_UP_AFTER_FAILURES_IN_A_ROW) return@withLock
            }
            pause()
        }
    }

    private fun Post.isDue(): Boolean {
        val failedAt = thumbnailFailedAt ?: return true
        return (now() - failedAt).milliseconds >= retryDelay(thumbnailFailures)
    }

    companion object {
        const val GIVE_UP_AFTER_FAILURES_IN_A_ROW = 5

        /** How long to wait after the [failures]th failure in a row: 6 hours, doubling up to a week. */
        fun retryDelay(failures: Int): Duration = minOf(6.hours * (1 shl (failures - 1).coerceIn(0, 10)), 7.days)
    }
}
