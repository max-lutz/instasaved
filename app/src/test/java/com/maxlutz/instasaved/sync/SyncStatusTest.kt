package com.maxlutz.instasaved.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The stale warning (sync-spec "Sync status and Summary"). */
class SyncStatusTest {
    private fun syncedOn(day: String, newestInDrive: String?) = SyncStatus(
        syncedAt = LocalDate.parse(day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        newestInDrive = newestInDrive?.let(LocalDate::parse),
    )

    @Test
    fun staleWhenTheNewestExportInDriveIsMoreThanThreeDaysOld() {
        assertTrue(syncedOn("2026-10-05", newestInDrive = "2026-10-01").isStale(ZoneOffset.UTC))
        assertTrue(syncedOn("2026-11-20", newestInDrive = "2026-10-01").isStale(ZoneOffset.UTC))
    }

    @Test
    fun notStaleUpToThreeDays() {
        assertFalse(syncedOn("2026-10-04", newestInDrive = "2026-10-01").isStale(ZoneOffset.UTC))
        assertFalse(syncedOn("2026-10-01", newestInDrive = "2026-10-01").isStale(ZoneOffset.UTC))
        // The Export's day can be ahead of the phone's.
        assertFalse(syncedOn("2026-10-01", newestInDrive = "2026-10-02").isStale(ZoneOffset.UTC))
    }

    @Test
    fun notStaleBeforeASyncFoundAnExport() {
        assertFalse(SyncStatus().isStale(ZoneOffset.UTC))
        assertFalse(syncedOn("2026-10-05", newestInDrive = null).isStale(ZoneOffset.UTC))
        assertFalse(SyncStatus(newestInDrive = LocalDate.of(2026, 10, 1)).isStale(ZoneOffset.UTC))
    }
}
