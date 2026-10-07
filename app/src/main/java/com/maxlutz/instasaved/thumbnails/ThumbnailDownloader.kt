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
import kotlin.time.Duration.Companion.minutes

/**
 * Downloads the Thumbnail of every Post that lacks one. Best-effort (ADR-0008): a failed download is
 * retried [retryDelay] after its last failure, soon at first and then ever more slowly, so a post that is
 * gone from Instagram costs a request a week at most.
 *
 * @param fetch the image bytes for a shortcode, or null if it could not be had.
 * @param runs how the last runs ended, kept across restarts of the app.
 * @param pause waits between two downloads, to stay gentle on Instagram.
 */
class ThumbnailDownloader(
    private val posts: PostDao,
    private val store: ThumbnailStore,
    private val fetch: suspend (shortcode: String) -> ByteArray?,
    private val runs: ThumbnailRuns,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend () -> Unit = { delay(500) },
) {
    // One run at a time, so two runs never download the same Thumbnail.
    private val running = Mutex()

    /**
     * Tries every Post without a Thumbnail that is due: never tried, or failed long enough ago. Does nothing
     * while the runs are held back, after one that stopped early.
     *
     * @return how long until a download is due again, or null if no Thumbnail is missing.
     */
    suspend fun downloadMissing(): Duration? = running.withLock {
        if (now() >= runs.heldBackUntil) run()
        untilNextDue()
    }

    private suspend fun run() {
        var failuresInARow = 0
        for (post in posts.getAllFewestThumbnailFailuresFirst()) {
            if (store.has(post.shortcode) || post.dueAt() > now()) continue
            val image = fetch(post.shortcode)
            if (image != null) {
                store.save(post.shortcode, image)
                posts.thumbnailSaved(post.id)
                failuresInARow = 0
                runs.downloadWorked()
            } else {
                posts.thumbnailFailed(post.id, at = now())
                // Offline, throttled, or the endpoints broke: leave the rest untried for a later run.
                if (++failuresInARow == GIVE_UP_AFTER_FAILURES_IN_A_ROW) {
                    val stops = runs.stoppedEarlyInARow + 1
                    runs.stoppedEarly(stops, heldBackUntil = now() + runDelay(stops).inWholeMilliseconds)
                    return
                }
            }
            pause()
        }
    }

    private suspend fun untilNextDue(): Duration? {
        val missing = posts.getAllFewestThumbnailFailuresFirst().filterNot { store.has(it.shortcode) }
        val dueAt = missing.minOfOrNull { it.dueAt() } ?: return null
        return (maxOf(dueAt, runs.heldBackUntil) - now()).coerceAtLeast(0).milliseconds
    }

    /** When the Post's Thumbnail may be tried, in epoch milliseconds: at once if it never failed. */
    private fun Post.dueAt(): Long {
        val failedAt = thumbnailFailedAt ?: return 0
        return failedAt + retryDelay(thumbnailFailures).inWholeMilliseconds
    }

    companion object {
        const val GIVE_UP_AFTER_FAILURES_IN_A_ROW = 5

        private val LADDER = listOf(1.minutes, 5.minutes, 30.minutes, 2.hours, 6.hours)

        /**
         * How long a Post waits after its [failures]th failure in a row: 1 minute, 5 minutes, 30 minutes,
         * 2 hours, 6 hours, then doubling up to a week.
         */
        fun retryDelay(failures: Int): Duration =
            LADDER.getOrNull(failures.coerceAtLeast(1) - 1)
                ?: minOf(LADDER.last() * (1 shl (failures - LADDER.size).coerceAtMost(10)), 7.days)

        /**
         * How long the runs are held back after the [stops]th run in a row that stopped early: the same
         * ladder, up to 6 hours. It keeps the untried Posts from being tried every minute for as long as
         * Instagram is throttling.
         */
        fun runDelay(stops: Int): Duration = LADDER[(stops - 1).coerceIn(LADDER.indices)]
    }
}
