package com.maxlutz.instasaved.thumbnails

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maxlutz.instasaved.InstaSavedApplication
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Runs [ThumbnailDownloader.downloadMissing] in the background, once there is network. */
class ThumbnailWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val untilNextDue = (applicationContext as InstaSavedApplication).thumbnailDownloader.downloadMissing()
        if (untilNextDue != null) retryIn(untilNextDue)
        return Result.success()
    }

    /** Runs again when the next failed download is due, replacing the retry planned before. */
    private fun retryIn(wait: Duration) {
        val request = OneTimeWorkRequestBuilder<ThumbnailWorker>()
            .setConstraints(needsNetwork)
            .setInitialDelay(wait.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .addTag(RETRY)
            .build()
        // Replacing the retry from the retry itself would cancel this very run: it goes after it instead.
        val policy = if (RETRY in tags) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(RETRY, policy, request)
    }

    companion object {
        private const val RETRY = "thumbnail-retry"

        private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** For Posts just added. */
        fun downloadNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ThumbnailWorker>().setConstraints(needsNetwork).build()
            // Appended: a run already under way has listed its Posts and would miss the new one.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("thumbnails", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** The safety net under the retries each run plans for itself: a run every few hours, whatever happened. */
        fun scheduleRetries(context: Context) {
            val request = PeriodicWorkRequestBuilder<ThumbnailWorker>(6, TimeUnit.HOURS)
                .setConstraints(needsNetwork)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("thumbnail-retries", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
