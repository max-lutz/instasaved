package com.maxlutz.instasaved.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maxlutz.instasaved.InstaSavedApplication
import java.util.concurrent.TimeUnit

/** The daily background Sync. It says what it did in the sync status only: no system notification. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as InstaSavedApplication
        val report = app.sync.runInBackground(
            DriveAuthorization.authorize(app),
            everSynced = app.syncStatus.status.value.syncedAt != null,
            drive = { DriveRest(BearerHttp(it)) },
        )
        // Tried again soon, rather than a day later.
        return if (report?.problem == SyncProblem.Offline) Result.retry() else Result.success()
    }

    companion object {
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("daily-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/**
 * A Sync nobody is there to watch: it runs when Google hands a token without asking, and never opens the consent
 * screen.
 *
 * @param everSynced whether a Sync already went through Drive's Exports. If so, access that is no longer given is a
 *   problem to show; if not, the user has not connected Drive yet and the app says nothing.
 * @param drive the Drive an access token opens.
 * @return what the Sync did, or null when it was left for the user to start.
 */
suspend fun Sync.runInBackground(
    authorization: DriveAuthorization.Outcome,
    everSynced: Boolean,
    drive: (accessToken: String) -> Drive,
): Sync.Report? = when (authorization) {
    is DriveAuthorization.Outcome.Granted -> run(drive(authorization.accessToken))
    is DriveAuthorization.Outcome.NeedsConsent -> if (everSynced) failed(SyncProblem.AccessRefused) else null
    is DriveAuthorization.Outcome.Failed -> if (everSynced) failed(authorization.problem) else null
}
