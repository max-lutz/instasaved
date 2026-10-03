package com.maxlutz.instasaved.deleted

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maxlutz.instasaved.InstaSavedApplication
import java.util.concurrent.TimeUnit

/** The daily purge of Recently deleted: runs [RecentlyDeleted.purgeExpired] in the background. */
class PurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as InstaSavedApplication).recentlyDeleted.purgeExpired()
        return Result.success()
    }

    companion object {
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("purge-recently-deleted", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
