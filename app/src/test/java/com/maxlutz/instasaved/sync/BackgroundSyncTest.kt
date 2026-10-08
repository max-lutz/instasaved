package com.maxlutz.instasaved.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.data.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.LocalDate

/** The daily background Sync: what it does with each answer Google gives when asked for access without the user. */
@RunWith(RobolectricTestRunner::class)
class BackgroundSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var status: SyncStatusStore
    private lateinit var sync: Sync
    private val tokens = mutableListOf<String>()
    private var offline = false

    /** A Drive with one Export, holding no posts. */
    private val drive = object : Drive {
        override suspend fun exports(): List<DriveExport> {
            if (offline) throw IOException("offline")
            return listOf(DriveExport("id", "instagram-someone-2026-10-01-Xy12AbCd", LocalDate.of(2026, 10, 1), ""))
        }

        override suspend fun read(export: DriveExport, path: String) = "[]".takeIf { path == SAVED_POSTS_PATH }
    }

    private val needsConsent
        get() = DriveAuthorization.Outcome.NeedsConsent(
            PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
        )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        status = SyncStatusStore(context.getSharedPreferences("sync-test", Context.MODE_PRIVATE))
        sync = Sync(db.syncDao(), status, queueThumbnails = {}, now = { 42 })
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("sync-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private suspend fun runInBackground(authorization: DriveAuthorization.Outcome, everSynced: Boolean) =
        sync.runInBackground(authorization, everSynced) { token -> drive.also { tokens += token } }

    @Test
    fun syncsWithTheTokenGoogleHandsWithoutAsking() = runTest {
        val report = runInBackground(DriveAuthorization.Outcome.Granted("token"), everSynced = false)

        assertNull(report?.problem)
        assertEquals(listOf("token"), tokens)
        assertEquals(42L, status.status.value.syncedAt)
        assertEquals(LocalDate.of(2026, 10, 1), status.status.value.newestExport)
    }

    @Test
    fun saysNothingWhenTheUserHasNotConnectedDriveYet() = runTest {
        assertNull(runInBackground(needsConsent, everSynced = false))
        assertNull(runInBackground(DriveAuthorization.Outcome.Failed(SyncProblem.AccessNotGranted), everSynced = false))

        assertEquals(SyncStatus(), status.status.value)
        assertEquals(emptyList<String>(), tokens)
    }

    @Test
    fun accessLostSinceTheLastSyncIsAProblemToShow() = runTest {
        runInBackground(DriveAuthorization.Outcome.Granted("token"), everSynced = false)

        val report = runInBackground(needsConsent, everSynced = true)

        assertEquals(SyncProblem.AccessRefused, report?.problem)
        assertEquals(SyncProblem.AccessRefused, status.status.value.problem)
        assertEquals(42L, status.status.value.syncedAt)
    }

    @Test
    fun aFailedAuthorizationIsShownOnceDriveWasConnected() = runTest {
        val report = runInBackground(DriveAuthorization.Outcome.Failed(SyncProblem.Offline), everSynced = true)

        assertEquals(SyncProblem.Offline, report?.problem)
        assertEquals(SyncProblem.Offline, status.status.value.problem)
    }

    @Test
    fun reportsADriveThatCannotBeReached() = runTest {
        offline = true

        val report = runInBackground(DriveAuthorization.Outcome.Granted("token"), everSynced = true)

        assertEquals(SyncProblem.Offline, report?.problem)
    }
}
