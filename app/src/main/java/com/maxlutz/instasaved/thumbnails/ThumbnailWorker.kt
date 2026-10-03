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

/** Runs [ThumbnailDownloader.downloadMissing] in the background, once there is network. */
class ThumbnailWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as InstaSavedApplication).thumbnailDownloader.downloadMissing()
        return Result.success()
    }

    companion object {
        private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** For Posts just added. */
        fun downloadNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ThumbnailWorker>().setConstraints(needsNetwork).build()
            // Appended: a run already under way has listed its Posts and would miss the new one.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("thumbnails", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** The slow retry queue: a run every few hours picks up the failed downloads that are due again. */
        fun scheduleRetries(context: Context) {
            val request = PeriodicWorkRequestBuilder<ThumbnailWorker>(6, TimeUnit.HOURS)
                .setConstraints(needsNetwork)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("thumbnail-retries", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
